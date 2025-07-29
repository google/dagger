/*
 * Copyright (C) 2020 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.hilt.android.plugin

import com.android.build.api.AndroidPluginVersion
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.Component
import com.android.build.api.variant.HasAndroidTest
import com.android.build.api.variant.HasUnitTest
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.TestAndroidComponentsExtension
import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.TestExtension
import com.android.build.gradle.tasks.JdkImageInput
import dagger.hilt.android.plugin.task.AggregateDepsTask
import dagger.hilt.android.plugin.transform.AggregatedPackagesTransform
import dagger.hilt.android.plugin.transform.AndroidEntryPointClassVisitor
import dagger.hilt.android.plugin.transform.CopyTransform
import dagger.hilt.android.plugin.util.addJavaTaskProcessorOptions
import dagger.hilt.android.plugin.util.addKaptTaskProcessorOptions
import dagger.hilt.android.plugin.util.addKspTaskProcessorOptions
import dagger.hilt.android.plugin.util.capitalize
import dagger.hilt.android.plugin.util.forEachRootVariant
import dagger.hilt.android.plugin.util.getKaptConfigName
import dagger.hilt.android.plugin.util.getKspConfigName
import dagger.hilt.android.plugin.util.isKspTask
import dagger.hilt.android.plugin.util.onAllVariants
import dagger.hilt.processor.internal.optionvalues.GradleProjectType
import javax.inject.Inject
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.attributes.Attribute
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.process.CommandLineArgumentProvider
import org.objectweb.asm.Opcodes

/**
 * A Gradle plugin that checks if the project is an Android project and if so, registers a bytecode
 * transformation.
 *
 * The plugin also passes an annotation processor option to disable superclass validation for
 * classes annotated with `@AndroidEntryPoint` since the registered transform by this plugin will
 * update the superclass.
 */
class HiltGradlePlugin @Inject constructor(private val providers: ProviderFactory) :
  Plugin<Project> {
  override fun apply(project: Project) {
    var configured = false
    project.plugins.withId("com.android.base") {
      configured = true
      configureHilt(project)
    }
    project.afterEvaluate {
      check(configured) {
        // Check if configuration was applied, if not inform the developer they have applied the
        // plugin to a non-android project.
        "The Hilt Android Gradle plugin can only be applied to an Android project."
      }
      verifyDependencies(it)
    }
  }

  private fun configureHilt(project: Project) {
    val hiltExtension =
      project.extensions.create(HiltExtension::class.java, "hilt", HiltExtensionImpl::class.java)

    val androidExtension = project.extensions.findByType(AndroidComponentsExtension::class.java)
    check(androidExtension != null) { "Could not find the Android Gradle Plugin (AGP) extension." }
    check(androidExtension.pluginVersion >= AndroidPluginVersion(8, 1)) {
      "The Hilt Android Gradle plugin is only compatible with Android Gradle plugin (AGP) " +
        "version 8.1.0 or higher (found ${androidExtension.pluginVersion})."
    }

    configureDependencyTransforms(project)
    configureCompileClasspath(project, hiltExtension)
    configureBytecodeTransformASM(androidExtension)
    configureAggregatingTask(project, hiltExtension)
    configureProcessorFlags(project, hiltExtension, androidExtension)
  }

  // Configures Gradle dependency transforms.
  private fun configureDependencyTransforms(project: Project) =
    project.dependencies.apply {
      registerTransform(CopyTransform::class.java) { spec ->
        // Java/Kotlin library projects offer an artifact of type 'jar'.
        spec.from.attribute(ARTIFACT_TYPE_ATTRIBUTE, "jar")
        // Android library projects (with or without Kotlin) offer an artifact of type
        // 'android-classes', which AGP can offer as a jar.
        spec.from.attribute(ARTIFACT_TYPE_ATTRIBUTE, "android-classes")
        spec.to.attribute(ARTIFACT_TYPE_ATTRIBUTE, DAGGER_ARTIFACT_TYPE_VALUE)
      }
      registerTransform(CopyTransform::class.java) { spec ->
        // File Collection dependencies might be an artifact of type 'directory', e.g. when
        // adding as a dep the destination directory of the JavaCompile task.
        spec.from.attribute(ARTIFACT_TYPE_ATTRIBUTE, "directory")
        spec.to.attribute(ARTIFACT_TYPE_ATTRIBUTE, DAGGER_ARTIFACT_TYPE_VALUE)
      }
      registerTransform(AggregatedPackagesTransform::class.java) { spec ->
        spec.from.attribute(ARTIFACT_TYPE_ATTRIBUTE, DAGGER_ARTIFACT_TYPE_VALUE)
        spec.to.attribute(ARTIFACT_TYPE_ATTRIBUTE, AGGREGATED_HILT_ARTIFACT_TYPE_VALUE)
      }
    }

  private fun configureCompileClasspath(project: Project, hiltExtension: HiltExtension) {
    val androidExtension =
      project.extensions.findByType(BaseExtension::class.java)
        ?: error("Android BaseExtension not found.")
    androidExtension.forEachRootVariant { variant ->
      configureVariantCompileClasspath(project, hiltExtension, variant)
    }
  }

  private fun configureVariantCompileClasspath(
    project: Project,
    hiltExtension: HiltExtension,
    @Suppress("DEPRECATION") variant: com.android.build.gradle.api.BaseVariant,
  ) {
    if (
      !hiltExtension.enableExperimentalClasspathAggregation || hiltExtension.enableAggregatingTask
    ) {
      // Option is not enabled, don't configure compile classpath. Note that the option can't be
      // checked earlier (before iterating over the variants) since it would have been too early for
      // the value to be populated from the build file.
      return
    }

    if (project.isGradleSyncRunning()) {
      // Do not configure compile classpath when AndroidStudio is building the model (syncing)
      // otherwise it will cause a freeze.
      return
    }

    @Suppress("DEPRECATION") // Older variant API is deprecated
    val runtimeConfiguration =
      if (variant is com.android.build.gradle.api.TestVariant) {
        // For Android test variants, the tested runtime classpath is used since the test app has
        // tested dependencies removed.
        variant.testedVariant.runtimeConfiguration
      } else {
        variant.runtimeConfiguration
      }
    val artifactView =
      runtimeConfiguration.incoming.artifactView { view ->
        view.attributes.attribute(ARTIFACT_TYPE_ATTRIBUTE, DAGGER_ARTIFACT_TYPE_VALUE)
        view.componentFilter { identifier ->
          // Filter out the project's classes from the aggregated view since this can cause
          // issues with Kotlin internal members visibility. b/178230629
          if (identifier is ProjectComponentIdentifier) {
            identifier.projectName != project.name
          } else {
            true
          }
        }
      }

    // CompileOnly config names don't follow the usual convention:
    // <Variant Name>   -> <Config Name>
    // debug            -> debugCompileOnly
    // debugAndroidTest -> androidTestDebugCompileOnly
    // debugUnitTest    -> testDebugCompileOnly
    // release          -> releaseCompileOnly
    // releaseUnitTest  -> testReleaseCompileOnly
    @Suppress("DEPRECATION") // Older variant API is deprecated
    val compileOnlyConfigName =
      when (variant) {
        is com.android.build.gradle.api.TestVariant ->
          "androidTest${variant.name.substringBeforeLast("AndroidTest").capitalize()}CompileOnly"
        is com.android.build.gradle.api.UnitTestVariant ->
          "test${variant.name.substringBeforeLast("UnitTest").capitalize()}CompileOnly"
        else -> "${variant.name}CompileOnly"
      }
    project.dependencies.add(compileOnlyConfigName, artifactView.files)
  }

  private fun configureBytecodeTransformASM(androidExtension: AndroidComponentsExtension<*, *, *>) {
    androidExtension.onAllVariants { variantComponent ->
      variantComponent.instrumentation.transformClassesWith(
        classVisitorFactoryImplClass = AndroidEntryPointClassVisitor.Factory::class.java,
        scope = InstrumentationScope.PROJECT,
        instrumentationParamsConfig = {},
      )
      variantComponent.instrumentation.setAsmFramesComputationMode(
        FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS
      )
    }
  }

  private fun configureAggregatingTask(project: Project, hiltExtension: HiltExtension) {
    val androidExtension =
      project.extensions.findByType(BaseExtension::class.java)
        ?: error("Android BaseExtension not found.")
    androidExtension.forEachRootVariant { variant ->
      configureVariantAggregatingTask(project, hiltExtension, androidExtension, variant)
    }
  }

  private fun configureVariantAggregatingTask(
    project: Project,
    hiltExtension: HiltExtension,
    androidExtension: BaseExtension,
    @Suppress("DEPRECATION") variant: com.android.build.gradle.api.BaseVariant,
  ) {
    if (!hiltExtension.enableAggregatingTask) {
      // Option is not enabled, don't configure aggregating task.
      return
    }

    val hiltCompileConfiguration =
      project.configurations.create("hiltCompileOnly${variant.name.capitalize()}").apply {
        description = "Hilt aggregated compile only dependencies for '${variant.name}'"
        isCanBeConsumed = false
        isCanBeResolved = true
        isVisible = false
      }
    // Add the JavaCompile task classpath and output dir to the config, the task's classpath
    // will contain:
    //  * compileOnly dependencies
    //  * KAPT, KSP and Kotlinc generated bytecode
    //  * R.jar
    //  * Tested classes if the variant is androidTest
    // TODO(danysantiago): Revisit to support K2 compiler
    project.dependencies.add(
      hiltCompileConfiguration.name,
      project.files(variant.javaCompileProvider.map { it.classpath }),
    )
    project.dependencies.add(
      hiltCompileConfiguration.name,
      project.files(variant.javaCompileProvider.map { it.destinationDirectory.get() }),
    )

    val hiltAnnotationProcessorConfiguration =
      project.configurations.create("hiltAnnotationProcessor${variant.name.capitalize()}").also {
        config ->
        config.description = "Hilt annotation processor classpath for '${variant.name}'"
        config.isCanBeConsumed = false
        config.isCanBeResolved = true
        config.isVisible = false
        // Add user annotation processor configuration, so that SPI plugins and other processors
        // are discoverable.
        val apConfigurations: List<Configuration> = buildList {
          add(variant.annotationProcessorConfiguration)
          project.plugins.withId("kotlin-kapt") {
            project.configurations.findByName(getKaptConfigName(variant))?.let { add(it) }
          }
          project.plugins.withId("com.google.devtools.ksp") {
            // Add the main 'ksp' config since the variant aware config does not extend main.
            // https://github.com/google/ksp/issues/1433
            project.configurations.findByName("ksp")?.let { add(it) }
            project.configurations.findByName(getKspConfigName(variant))?.let { add(it) }
          }
        }
        config.extendsFrom(*apConfigurations.toTypedArray())
        // Add hilt-compiler even though it might be in the AP configurations already.
        project.dependencies.add(config.name, "com.google.dagger:hilt-compiler:$HILT_VERSION")
      }

    fun getInputClasspath(artifactAttributeValue: String) =
      buildList<Configuration> {
          @Suppress("DEPRECATION") // Older variant API is deprecated
          if (variant is com.android.build.gradle.api.TestVariant) {
            add(variant.testedVariant.runtimeConfiguration)
          }
          add(variant.runtimeConfiguration)
          add(hiltCompileConfiguration)
        }
        .map { configuration ->
          configuration.incoming
            .artifactView { view ->
              view.attributes.attribute(ARTIFACT_TYPE_ATTRIBUTE, artifactAttributeValue)
            }
            .files
        }
        .let { project.files(*it.toTypedArray()) }

    val aggregatingTask =
      project.tasks.register(
        "hiltAggregateDeps${variant.name.capitalize()}",
        AggregateDepsTask::class.java,
      ) {
        it.compileClasspath.setFrom(getInputClasspath(AGGREGATED_HILT_ARTIFACT_TYPE_VALUE))
        it.outputDir.set(
          project.file(project.buildDir.resolve("generated/hilt/component_trees/${variant.name}/"))
        )
        @Suppress("DEPRECATION") // Older variant API is deprecated
        it.testEnvironment.set(
          variant is com.android.build.gradle.api.TestVariant ||
            variant is com.android.build.gradle.api.UnitTestVariant ||
            androidExtension is com.android.build.gradle.TestExtension
        )
        it.crossCompilationRootValidationDisabled.set(
          hiltExtension.disableCrossCompilationRootValidation
        )
        it.asmApiVersion.set(Opcodes.ASM9)
      }

    val componentClasses =
      project.files(
        project.buildDir.resolve("intermediates/hilt/component_classes/${variant.name}/")
      )
    val componentsJavaCompileTask =
      project.tasks.register(
        "hiltJavaCompile${variant.name.capitalize()}",
        JavaCompile::class.java,
      ) { compileTask ->
        compileTask.source = aggregatingTask.map { it.outputDir.asFileTree }.get()
        // Configure the input classpath based on Java 9 compatibility, specifically for Java 9 the
        // android.jar is now included in the input classpath instead of the bootstrapClasspath.
        // See: com/android/build/gradle/tasks/JavaCompileUtils.kt
        val mainBootstrapClasspath =
          variant.javaCompileProvider.map { it.options.bootstrapClasspath ?: project.files() }.get()
        if (
          JavaVersion.current().isJava9Compatible &&
            androidExtension.compileOptions.targetCompatibility.isJava9Compatible
        ) {
          compileTask.classpath =
            getInputClasspath(DAGGER_ARTIFACT_TYPE_VALUE).plus(mainBootstrapClasspath)
          //  Copies argument providers from original task, which should contain the JdkImageInput
          variant.javaCompileProvider.get().let { originalCompileTask ->
            originalCompileTask.options.compilerArgumentProviders
              .filter { it is HiltCommandLineArgumentProvider || it is JdkImageInput }
              .forEach { compileTask.options.compilerArgumentProviders.add(it) }
          }
          compileTask.options.compilerArgs.add("-XDstringConcat=inline")
        } else {
          compileTask.classpath = getInputClasspath(DAGGER_ARTIFACT_TYPE_VALUE)
          compileTask.options.bootstrapClasspath = mainBootstrapClasspath
        }
        compileTask.destinationDirectory.set(componentClasses.singleFile)
        compileTask.options.apply {
          annotationProcessorPath = hiltAnnotationProcessorConfiguration
          generatedSourceOutputDirectory.set(
            project.file(
              project.buildDir.resolve("generated/hilt/component_sources/${variant.name}/")
            )
          )
          if (
            JavaVersion.current().isJava8Compatible &&
              androidExtension.compileOptions.targetCompatibility.isJava8Compatible
          ) {
            compilerArgs.add("-parameters")
          }
          compilerArgs.add("-Adagger.fastInit=enabled")
          compilerArgs.add("-Adagger.hilt.internal.useAggregatingRootProcessor=false")
          compilerArgs.add("-Adagger.hilt.android.internal.disableAndroidSuperclassValidation=true")
          encoding = androidExtension.compileOptions.encoding
        }
        compileTask.sourceCompatibility =
          androidExtension.compileOptions.sourceCompatibility.toString()
        compileTask.targetCompatibility =
          androidExtension.compileOptions.targetCompatibility.toString()
      }
    componentClasses.builtBy(componentsJavaCompileTask)

    variant.registerPostJavacGeneratedBytecode(componentClasses)
  }

  private fun configureProcessorFlags(
    project: Project,
    hiltExtension: HiltExtension,
    androidExtension: AndroidComponentsExtension<*, *, *>,
  ) {
    val projectType =
      when (androidExtension) {
        is ApplicationAndroidComponentsExtension -> GradleProjectType.APP
        is LibraryAndroidComponentsExtension -> GradleProjectType.LIBRARY
        is TestAndroidComponentsExtension -> GradleProjectType.TEST
        else -> error("Hilt plugin does not know how to configure '$androidExtension'")
      }

    androidExtension.onAllVariants { variantComponent ->
      // Pass annotation processor flags via a CommandLineArgumentProvider so that plugin
      // options defined in the extension are populated from the user's build file.
      val argsProducer: (Task) -> CommandLineArgumentProvider = { task ->
        HiltCommandLineArgumentProvider(
          forKsp = task.isKspTask(),
          projectType = projectType,
          enableAggregatingTask = hiltExtension.enableAggregatingTask,
          disableCrossCompilationRootValidation =
            hiltExtension.disableCrossCompilationRootValidation,
        )
      }
      addJavaTaskProcessorOptions(project, variantComponent, argsProducer)
      addKaptTaskProcessorOptions(project, variantComponent, argsProducer)
      addKspTaskProcessorOptions(project, variantComponent, argsProducer)
    }
  }

  private fun verifyDependencies(project: Project) {
    // If project is already failing, skip verification since dependencies might not be resolved.
    if (project.state.failure != null) {
      return
    }
    val dependencies =
      project.configurations
        .filterNot {
          // Exclude plugin created config since plugin adds the deps to them.
          it.name.startsWith("hiltAnnotationProcessor") || it.name.startsWith("hiltCompileOnly")
        }
        .flatMap { configuration ->
          configuration.dependencies.filterIsInstance<ExternalDependency>().map { dependency ->
            dependency.group to dependency.name
          }
        }
        .toSet()
    fun getMissingDepMsg(depCoordinate: String): String =
      "The Hilt Android Gradle plugin is applied but no $depCoordinate dependency was found."
    if (!dependencies.contains(LIBRARY_GROUP to "hilt-android")) {
      error(getMissingDepMsg("$LIBRARY_GROUP:hilt-android"))
    }
    if (
      !dependencies.contains(LIBRARY_GROUP to "hilt-android-compiler") &&
        !dependencies.contains(LIBRARY_GROUP to "hilt-compiler")
    ) {
      error(getMissingDepMsg("$LIBRARY_GROUP:hilt-compiler"))
    }
  }

  companion object {
    private val ARTIFACT_TYPE_ATTRIBUTE = Attribute.of("artifactType", String::class.java)
    const val DAGGER_ARTIFACT_TYPE_VALUE = "jar-for-dagger"
    const val AGGREGATED_HILT_ARTIFACT_TYPE_VALUE = "aggregated-jar-for-hilt"

    const val LIBRARY_GROUP = "com.google.dagger"

    private fun Project.isGradleSyncRunning() =
      gradleSyncProps.any { property ->
        providers.gradleProperty(property).map { it.toBoolean() }.orElse(false).get()
      }

    private val gradleSyncProps by lazy {
      listOf(
        "android.injected.build.model.v2",
        "android.injected.build.model.only",
        "android.injected.build.model.only.advanced",
      )
    }
  }
}

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

import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.TestExtension
import com.android.build.gradle.TestedExtension
import com.android.build.gradle.api.AndroidBasePlugin
import dagger.hilt.android.plugin.task.AggregateDepsTask
import dagger.hilt.android.plugin.task.HiltTransformTestClassesTask
import dagger.hilt.android.plugin.util.AggregatedPackagesTransform
import dagger.hilt.android.plugin.util.ComponentCompat
import dagger.hilt.android.plugin.util.CopyTransform
import dagger.hilt.android.plugin.util.SimpleAGPVersion
import dagger.hilt.android.plugin.util.capitalize
import dagger.hilt.android.plugin.util.getAndroidComponentsExtension
import dagger.hilt.android.plugin.util.getKaptConfigName
import dagger.hilt.android.plugin.util.getSdkPath
import dagger.hilt.processor.internal.optionvalues.GradleProjectType
import java.io.File
import javax.inject.Inject
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.attributes.Attribute
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.util.GradleVersion
import org.objectweb.asm.Opcodes

/**
 * A Gradle plugin that checks if the project is an Android project and if so, registers a
 * bytecode transformation.
 *
 * The plugin also passes an annotation processor option to disable superclass validation for
 * classes annotated with `@AndroidEntryPoint` since the registered transform by this plugin will
 * update the superclass.
 */
class HiltGradlePlugin @Inject constructor(
  val providers: ProviderFactory
) : Plugin<Project> {
  override fun apply(project: Project) {
    var configured = false
    project.plugins.withType(AndroidBasePlugin::class.java) {
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
    val hiltExtension = project.extensions.create(
      HiltExtension::class.java, "hilt", HiltExtensionImpl::class.java
    )
    configureDependencyTransforms(project)
    configureCompileClasspath(project, hiltExtension)
    if (SimpleAGPVersion.ANDROID_GRADLE_PLUGIN_VERSION < SimpleAGPVersion(4, 2)) {
      // Configures bytecode transform using older APIs pre AGP 4.2
      configureBytecodeTransform(project, hiltExtension)
    } else {
      // Configures bytecode transform using AGP 4.2 ASM pipeline.
      configureBytecodeTransformASM(project, hiltExtension)
    }
    configureAggregatingTask(project, hiltExtension)
    configureProcessorFlags(project, hiltExtension)
  }

  // Configures Gradle dependency transforms.
  private fun configureDependencyTransforms(project: Project) = project.dependencies.apply {
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
    val androidExtension = project.baseExtension() ?: error("Android BaseExtension not found.")
    androidExtension.forEachRootVariant { variant ->
      configureVariantCompileClasspath(project, hiltExtension, androidExtension, variant)
    }
  }

  // Invokes the [block] function for each Android variant that is considered a Hilt root, where
  // dependencies are aggregated and components are generated.
  private fun BaseExtension.forEachRootVariant(
    @Suppress("DEPRECATION") block: (variant: com.android.build.gradle.api.BaseVariant) -> Unit
  ) {
    when (this) {
      is AppExtension -> {
        // For an app project we configure the app variant and both androidTest and unitTest
        // variants, Hilt components are generated in all of them.
        applicationVariants.all { block(it) }
        testVariants.all { block(it) }
        unitTestVariants.all { block(it) }
      }
      is LibraryExtension -> {
        // For a library project, only the androidTest and unitTest variant are configured since
        // Hilt components are not generated in a library.
        testVariants.all { block(it) }
        unitTestVariants.all { block(it) }
      }
      is TestExtension -> {
        applicationVariants.all { block(it) }
      }
      else -> error("Hilt plugin does not know how to configure '$this'")
    }
  }

  private fun configureVariantCompileClasspath(
    project: Project,
    hiltExtension: HiltExtension,
    androidExtension: BaseExtension,
    @Suppress("DEPRECATION") variant: com.android.build.gradle.api.BaseVariant
  ) {
    if (
      !hiltExtension.enableExperimentalClasspathAggregation || hiltExtension.enableAggregatingTask
    ) {
      // Option is not enabled, don't configure compile classpath. Note that the option can't be
      // checked earlier (before iterating over the variants) since it would have been too early for
      // the value to be populated from the build file.
      return
    }

    if (
      androidExtension.lintOptions.isCheckReleaseBuilds &&
      SimpleAGPVersion.ANDROID_GRADLE_PLUGIN_VERSION < SimpleAGPVersion(7, 0)
    ) {
      // Sadly we have to ask users to disable lint when enableExperimentalClasspathAggregation is
      // set to true and they are not in AGP 7.0+ since Lint will cause issues during the
      // configuration phase. See b/158753935 and b/160392650
      error(
        "Invalid Hilt plugin configuration: When 'enableExperimentalClasspathAggregation' is " +
          "enabled 'android.lintOptions.checkReleaseBuilds' has to be set to false unless " +
          "com.android.tools.build:gradle:7.0.0+ is used."
      )
    }

    if (
      listOf(
        "android.injected.build.model.only", // Sent by AS 1.0 only
        "android.injected.build.model.only.advanced", // Sent by AS 1.1+
        "android.injected.build.model.only.versioned", // Sent by AS 2.4+
        "android.injected.build.model.feature.full.dependencies", // Sent by AS 2.4+
        "android.injected.build.model.v2", // Sent by AS 4.2+
      ).any {
        // forUseAtConfigurationTime() is deprecated in 7.4 and later:
        // https://docs.gradle.org/current/userguide/upgrading_version_7.html#changes_7.4
        if (GradleVersion.version(project.gradle.gradleVersion) < GradleVersion.version("7.4.0")) {
          @Suppress("DEPRECATION")
          providers.gradleProperty(it).forUseAtConfigurationTime().isPresent
        } else {
          providers.gradleProperty(it).isPresent
        }
      }
    ) {
      // Do not configure compile classpath when AndroidStudio is building the model (syncing)
      // otherwise it will cause a freeze.
      return
    }

    @Suppress("DEPRECATION") // Older variant API is deprecated
    val runtimeConfiguration = if (variant is com.android.build.gradle.api.TestVariant) {
      // For Android test variants, the tested runtime classpath is used since the test app has
      // tested dependencies removed.
      variant.testedVariant.runtimeConfiguration
    } else {
      variant.runtimeConfiguration
    }
    val artifactView = runtimeConfiguration.incoming.artifactView { view ->
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
    val compileOnlyConfigName = when (variant) {
      is com.android.build.gradle.api.TestVariant ->
        "androidTest${variant.name.substringBeforeLast("AndroidTest").capitalize()}CompileOnly"
      is com.android.build.gradle.api.UnitTestVariant ->
        "test${variant.name.substringBeforeLast("UnitTest").capitalize()}CompileOnly"
      else ->
        "${variant.name}CompileOnly"
    }
    project.dependencies.add(compileOnlyConfigName, artifactView.files)
  }

  @Suppress("UnstableApiUsage") // ASM Pipeline APIs
  private fun configureBytecodeTransformASM(project: Project, hiltExtension: HiltExtension) {
    var warnAboutLocalTestsFlag = false
    fun registerTransform(androidComponent: ComponentCompat) {
      if (hiltExtension.enableTransformForLocalTests && !warnAboutLocalTestsFlag) {
        project.logger.warn(
          "The Hilt configuration option 'enableTransformForLocalTests' is no longer necessary " +
            "when com.android.tools.build:gradle:4.2.0+ is used."
        )
        warnAboutLocalTestsFlag = true
      }
      androidComponent.transformClassesWith(
        classVisitorFactoryImplClass = AndroidEntryPointClassVisitor.Factory::class.java,
        scope = InstrumentationScope.PROJECT
      ) { params ->
        val classesDir =
          File(project.buildDir, "intermediates/javac/${androidComponent.name}/classes")
        params.additionalClassesDir.set(classesDir)
      }
      androidComponent.setAsmFramesComputationMode(
        FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS
      )
    }
    getAndroidComponentsExtension(project).onAllVariants { registerTransform(it) }
  }

  private fun configureBytecodeTransform(project: Project, hiltExtension: HiltExtension) {
    val androidExtension = project.baseExtension() ?: error("Android BaseExtension not found.")
    androidExtension::class.java.getMethod(
      "registerTransform",
      Class.forName("com.android.build.api.transform.Transform"),
      Array<Any>::class.java
    ).invoke(androidExtension, AndroidEntryPointTransform(), emptyArray<Any>())

    // Create and configure a task for applying the transform for host-side unit tests. b/37076369
    project.testedExtension()?.unitTestVariants?.all { unitTestVariant ->
      HiltTransformTestClassesTask.create(
        project = project,
        unitTestVariant = unitTestVariant,
        extension = hiltExtension
      )
    }
  }

  private fun configureAggregatingTask(project: Project, hiltExtension: HiltExtension) {
    val androidExtension = project.baseExtension() ?: error("Android BaseExtension not found.")
    androidExtension.forEachRootVariant { variant ->
      configureVariantAggregatingTask(project, hiltExtension, androidExtension, variant)
    }
  }

  private fun configureVariantAggregatingTask(
    project: Project,
    hiltExtension: HiltExtension,
    androidExtension: BaseExtension,
    @Suppress("DEPRECATION") variant: com.android.build.gradle.api.BaseVariant
  ) {
    if (!hiltExtension.enableAggregatingTask) {
      // Option is not enabled, don't configure aggregating task.
      return
    }

    val hiltCompileConfiguration = project.configurations.create(
      "hiltCompileOnly${variant.name.capitalize()}"
    ).apply {
      isCanBeConsumed = false
      isCanBeResolved = true
    }
    // Add the JavaCompile task classpath and output dir to the config, the task's classpath
    // will contain:
    //  * compileOnly dependencies
    //  * KAPT and Kotlinc generated bytecode
    //  * R.jar
    //  * Tested classes if the variant is androidTest
    project.dependencies.add(
      hiltCompileConfiguration.name,
      project.files(variant.javaCompileProvider.map { it.classpath })
    )
    project.dependencies.add(
      hiltCompileConfiguration.name,
      project.files(variant.javaCompileProvider.map {it.destinationDirectory.get() })
    )

    fun getInputClasspath(artifactAttributeValue: String) =
      mutableListOf<Configuration>().apply {
        @Suppress("DEPRECATION") // Older variant API is deprecated
        if (variant is com.android.build.gradle.api.TestVariant) {
          add(variant.testedVariant.runtimeConfiguration)
        }
        add(variant.runtimeConfiguration)
        add(hiltCompileConfiguration)
      }.map { configuration ->
        configuration.incoming.artifactView { view ->
          view.attributes.attribute(ARTIFACT_TYPE_ATTRIBUTE, artifactAttributeValue)
        }.files
      }.let {
        project.files(*it.toTypedArray())
      }

    val aggregatingTask = project.tasks.register(
      "hiltAggregateDeps${variant.name.capitalize()}",
      AggregateDepsTask::class.java
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
      if (SimpleAGPVersion.ANDROID_GRADLE_PLUGIN_VERSION >= SimpleAGPVersion(7, 1)) {
        it.asmApiVersion.set(Opcodes.ASM9)
      }
    }

    val componentClasses = project.files(
      project.buildDir.resolve("intermediates/hilt/component_classes/${variant.name}/")
    )
    val componentsJavaCompileTask = project.tasks.register(
      "hiltJavaCompile${variant.name.capitalize()}",
      JavaCompile::class.java
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
          originalCompileTask.options.compilerArgumentProviders.forEach {
            compileTask.options.compilerArgumentProviders.add(it)
          }
        }
        compileTask.options.compilerArgs.add("-XDstringConcat=inline")
      } else {
        compileTask.classpath = getInputClasspath(DAGGER_ARTIFACT_TYPE_VALUE)
        compileTask.options.bootstrapClasspath = mainBootstrapClasspath
      }
      compileTask.destinationDirectory.set(componentClasses.singleFile)
      compileTask.options.apply {
        annotationProcessorPath = project.configurations.create(
          "hiltAnnotationProcessor${variant.name.capitalize()}"
        ).also { config ->
          config.isCanBeConsumed = false
          config.isCanBeResolved = true
          // Add user annotation processor configuration, so that SPI plugins and other processors
          // are discoverable.
          val apConfigurations: List<Configuration> = mutableListOf<Configuration>().apply {
            add(variant.annotationProcessorConfiguration)
            project.configurations.findByName(getKaptConfigName(variant))?.let { add(it) }
          }
          config.extendsFrom(*apConfigurations.toTypedArray())
          // Add hilt-compiler even though it might be in the AP configurations already.
          project.dependencies.add(config.name, "com.google.dagger:hilt-compiler:$HILT_VERSION")
        }
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

  private fun getAndroidJar(project: Project, compileSdkVersion: String) =
    project.files(File(project.getSdkPath(), "platforms/$compileSdkVersion/android.jar"))

  private fun configureProcessorFlags(project: Project, hiltExtension: HiltExtension) {
    val androidExtension = project.baseExtension() ?: error("Android BaseExtension not found.")
    androidExtension.defaultConfig.javaCompileOptions.annotationProcessorOptions.apply {
      // Pass annotation processor flag to enable Dagger's fast-init, the best mode for Hilt.
      argument("dagger.fastInit", "enabled")
      // Pass annotation processor flag to disable @AndroidEntryPoint superclass validation.
      argument("dagger.hilt.android.internal.disableAndroidSuperclassValidation", "true")

      val projectType = when (androidExtension) {
        is AppExtension -> GradleProjectType.APP
        is LibraryExtension -> GradleProjectType.LIBRARY
        is TestExtension -> GradleProjectType.TEST
        else -> error("Hilt plugin does not know how to configure '$this'")
      }
      argument("dagger.hilt.android.internal.projectType", projectType.toString())

      // Pass certain annotation processor flags via a CommandLineArgumentProvider so that plugin
      // options defined in the extension are populated from the user's build file. Checking the
      // option too early would make it seem like it is never set.
      compilerArgumentProvider(
        // Suppress due to https://docs.gradle.org/7.2/userguide/validation_problems.html#implementation_unknown
        @Suppress("ObjectLiteralToLambda")
        object : CommandLineArgumentProvider {
          override fun asArguments() = mutableListOf<String>().apply {
            // Pass annotation processor flag to disable the aggregating processor if aggregating
            // task is enabled.
            if (hiltExtension.enableAggregatingTask) {
              add("-Adagger.hilt.internal.useAggregatingRootProcessor=false")
            }
            // Pass annotation processor flag to disable cross compilation root validation.
            // The plugin option duplicates the processor flag because it is an input of the
            // aggregating task.
            if (hiltExtension.disableCrossCompilationRootValidation) {
              add("-Adagger.hilt.disableCrossCompilationRootValidation=true")
            }
          }
        }
      )
    }
  }

  private fun verifyDependencies(project: Project) {
    // If project is already failing, skip verification since dependencies might not be resolved.
    if (project.state.failure != null) {
      return
    }
    val dependencies = project.configurations.flatMap { configuration ->
      configuration.dependencies.map { dependency -> dependency.group to dependency.name }
    }
    if (!dependencies.contains(LIBRARY_GROUP to "hilt-android")) {
      error(missingDepError("$LIBRARY_GROUP:hilt-android"))
    }
    if (
      !dependencies.contains(LIBRARY_GROUP to "hilt-android-compiler") &&
      !dependencies.contains(LIBRARY_GROUP to "hilt-compiler")
    ) {
      error(missingDepError("$LIBRARY_GROUP:hilt-compiler"))
    }
  }

  private fun Project.baseExtension(): BaseExtension?
      = extensions.findByType(BaseExtension::class.java)

  private fun Project.testedExtension(): TestedExtension?
      = extensions.findByType(TestedExtension::class.java)

  companion object {
    val ARTIFACT_TYPE_ATTRIBUTE = Attribute.of("artifactType", String::class.java)
    const val DAGGER_ARTIFACT_TYPE_VALUE = "jar-for-dagger"
    const val AGGREGATED_HILT_ARTIFACT_TYPE_VALUE = "aggregated-jar-for-hilt"

    const val LIBRARY_GROUP = "com.google.dagger"

    val missingDepError: (String) -> String = { depCoordinate ->
      "The Hilt Android Gradle plugin is applied but no $depCoordinate dependency was found."
    }
  }
}

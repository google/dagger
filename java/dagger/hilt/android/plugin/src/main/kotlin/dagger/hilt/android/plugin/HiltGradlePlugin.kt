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

import com.android.build.api.component.Component
import com.android.build.api.extension.AndroidComponentsExtension
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.TestExtension
import com.android.build.gradle.TestedExtension
import com.android.build.gradle.api.AndroidBasePlugin
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.TestVariant
import com.android.build.gradle.api.UnitTestVariant
import dagger.hilt.android.plugin.util.CopyTransform
import dagger.hilt.android.plugin.util.SimpleAGPVersion
import java.io.File
import javax.inject.Inject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.attributes.Attribute
import org.gradle.api.provider.ProviderFactory

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
    configureCompileClasspath(project, hiltExtension)
    if (SimpleAGPVersion.ANDROID_GRADLE_PLUGIN_VERSION < SimpleAGPVersion(4, 2)) {
      // Configures bytecode transform using older APIs pre AGP 4.2
      configureTransform(project, hiltExtension)
    } else {
      // Configures bytecode transform using AGP 4.2 ASM pipeline.
      configureTransformASM(project, hiltExtension)
    }
    configureProcessorFlags(project)
  }

  private fun configureCompileClasspath(project: Project, hiltExtension: HiltExtension) {
    val androidExtension = project.extensions.findByType(BaseExtension::class.java)
      ?: throw error("Android BaseExtension not found.")
    when (androidExtension) {
      is AppExtension -> {
        // For an app project we configure the app variant and both androidTest and test variants,
        // Hilt components are generated in all of them.
        androidExtension.applicationVariants.all {
          configureVariantCompileClasspath(project, hiltExtension, androidExtension, it)
        }
        androidExtension.testVariants.all {
          configureVariantCompileClasspath(project, hiltExtension, androidExtension, it)
        }
        androidExtension.unitTestVariants.all {
          configureVariantCompileClasspath(project, hiltExtension, androidExtension, it)
        }
      }
      is LibraryExtension -> {
        // For a library project, only the androidTest and test variant are configured since
        // Hilt components are not generated in a library.
        androidExtension.testVariants.all {
          configureVariantCompileClasspath(project, hiltExtension, androidExtension, it)
        }
        androidExtension.unitTestVariants.all {
          configureVariantCompileClasspath(project, hiltExtension, androidExtension, it)
        }
      }
      is TestExtension -> {
        androidExtension.applicationVariants.all {
          configureVariantCompileClasspath(project, hiltExtension, androidExtension, it)
        }
      }
      else -> error(
        "Hilt plugin is unable to configure the compile classpath for project with extension " +
          "'$androidExtension'"
      )
    }

    project.dependencies.apply {
      registerTransform(CopyTransform::class.java) { spec ->
        // Java/Kotlin library projects offer an artifact of type 'jar'.
        spec.from.attribute(ARTIFACT_TYPE_ATTRIBUTE, "jar")
        // Android library projects (with or without Kotlin) offer an artifact of type
        // 'processed-jar', which AGP can offer as a jar.
        spec.from.attribute(ARTIFACT_TYPE_ATTRIBUTE, "processed-jar")
        spec.to.attribute(ARTIFACT_TYPE_ATTRIBUTE, DAGGER_ARTIFACT_TYPE_VALUE)
      }
    }
  }

  @Suppress("UnstableApiUsage")
  private fun configureVariantCompileClasspath(
    project: Project,
    hiltExtension: HiltExtension,
    androidExtension: BaseExtension,
    variant: BaseVariant
  ) {
    if (!hiltExtension.enableExperimentalClasspathAggregation) {
      // Option is not enabled, don't configure compile classpath. Note that the option can't be
      // checked earlier (before iterating over the variants) since it would have been too early for
      // the value to be populated from the build file.
      return
    }

    if (androidExtension.lintOptions.isCheckReleaseBuilds &&
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
        ).any { providers.gradleProperty(it).forUseAtConfigurationTime().isPresent }
    ) {
      // Do not configure compile classpath when AndroidStudio is building the model (syncing)
      // otherwise it will cause a freeze.
      return
    }

    val runtimeConfiguration = if (variant is TestVariant) {
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
    val compileOnlyConfigName = when (variant) {
      is TestVariant ->
        "androidTest${variant.name.substringBeforeLast("AndroidTest").capitalize()}CompileOnly"
      is UnitTestVariant ->
        "test${variant.name.substringBeforeLast("UnitTest").capitalize()}CompileOnly"
      else ->
        "${variant.name}CompileOnly"
    }
    project.dependencies.add(compileOnlyConfigName, artifactView.files)
  }

  @Suppress("UnstableApiUsage")
  private fun configureTransformASM(project: Project, hiltExtension: HiltExtension) {
    var warnAboutLocalTestsFlag = false
    fun registerTransform(androidComponent: Component) {
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

    val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
    androidComponents.onVariants { registerTransform(it) }
    androidComponents.androidTests { registerTransform(it) }
    androidComponents.unitTests { registerTransform(it) }
  }

  private fun configureTransform(project: Project, hiltExtension: HiltExtension) {
    val androidExtension = project.extensions.findByType(BaseExtension::class.java)
      ?: throw error("Android BaseExtension not found.")
    androidExtension.registerTransform(AndroidEntryPointTransform())

    // Create and configure a task for applying the transform for host-side unit tests. b/37076369
    val testedExtensions = project.extensions.findByType(TestedExtension::class.java)
    testedExtensions?.unitTestVariants?.all { unitTestVariant ->
      HiltTransformTestClassesTask.create(
        project = project,
        unitTestVariant = unitTestVariant,
        extension = hiltExtension
      )
    }
  }

  private fun configureProcessorFlags(project: Project) {
    val androidExtension = project.extensions.findByType(BaseExtension::class.java)
      ?: throw error("Android BaseExtension not found.")
    // Pass annotation processor flag to disable @AndroidEntryPoint superclass validation.
    androidExtension.defaultConfig.apply {
      javaCompileOptions.apply {
        annotationProcessorOptions.apply {
          PROCESSOR_OPTIONS.forEach { (key, value) -> argument(key, value) }
        }
      }
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
    if (!dependencies.contains(LIBRARY_GROUP to "hilt-android-compiler") &&
      !dependencies.contains(LIBRARY_GROUP to "hilt-compiler")
    ) {
      error(missingDepError("$LIBRARY_GROUP:hilt-compiler"))
    }
  }

  companion object {
    val ARTIFACT_TYPE_ATTRIBUTE = Attribute.of("artifactType", String::class.java)
    const val DAGGER_ARTIFACT_TYPE_VALUE = "jar-for-dagger"

    const val LIBRARY_GROUP = "com.google.dagger"
    val PROCESSOR_OPTIONS = listOf(
      "dagger.fastInit" to "enabled",
      "dagger.hilt.android.internal.disableAndroidSuperclassValidation" to "true"
    )
    val missingDepError: (String) -> String = { depCoordinate ->
      "The Hilt Android Gradle plugin is applied but no $depCoordinate dependency was found."
    }
  }
}

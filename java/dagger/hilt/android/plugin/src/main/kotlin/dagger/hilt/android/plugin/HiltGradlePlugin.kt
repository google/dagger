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

import com.android.build.gradle.BaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.plugin.KaptExtension

/**
 * A Gradle plugin that checks if the project is an Android project and if so, registers a
 * bytecode transformation.
 *
 * <p>The plugin also passes an annotation processor option to disable superclass validation for
 * classes annotated with @AndroidEntryPoint since the registered transform by this plugin will
 * update the superclass.
 */
class HiltGradlePlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val androidExtension = project.extensions.findByType(BaseExtension::class.java)
      ?: throw error("The Hilt Android Gradle plugin can only be applied to an Android project.")
    androidExtension.registerTransform(AndroidEntryPointTransform())

    // Pass annotation processor flag to disable @AndroidEntryPoint superclass validation.
    androidExtension.defaultConfig.apply {
      javaCompileOptions.apply {
        annotationProcessorOptions.apply {
          PROCESSOR_OPTIONS.forEach { (key, value) -> argument(key, value) }
        }
      }
    }
    // If project has KAPT also pass the processor flag to disable superclass validation.
    project.extensions.findByType(KaptExtension::class.java)?.let { kaptExtension ->
      kaptExtension.arguments {
        PROCESSOR_OPTIONS.forEach { (key, value) -> arg(key, value) }
      }
    }

    project.afterEvaluate {
      verifyDependencies(it)
    }
  }

  private fun verifyDependencies(project: Project) {
    val dependencies = project.configurations.flatMap { configuration ->
      configuration.dependencies.map { dependency -> dependency.group to dependency.name }
    }
    // TODO(danysantiago): Consider also validating Dagger compiler dependency.
    listOf(
      LIBRARY_GROUP to "hilt-android",
      LIBRARY_GROUP to "hilt-android-compiler"
    ).filterNot { dependencies.contains(it) }.forEach { (groupId, artifactId) ->
      error(
        "The Hilt Android Gradle plugin is applied but no $groupId:$artifactId dependency " +
          "was found."
      )
    }
  }

  companion object {
    const val LIBRARY_GROUP = "com.google.dagger"
    val PROCESSOR_OPTIONS = listOf(
      "dagger.fastInit" to "enabled",
      "dagger.hilt.android.internal.disableAndroidSuperclassValidation" to "true"
    )
  }
}

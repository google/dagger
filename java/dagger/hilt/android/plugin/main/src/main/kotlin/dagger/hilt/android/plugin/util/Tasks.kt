/*
 * Copyright (C) 2023 The Dagger Authors.
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

package dagger.hilt.android.plugin.util

import com.android.build.api.variant.Component
import com.android.build.api.variant.ComponentIdentity
import com.google.devtools.ksp.gradle.KspAATask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.kotlin.gradle.internal.KaptTask

internal fun addJavaTaskProcessorOptions(
  variant: Component,
  argProvider: CommandLineArgumentProvider,
) {
  variant.javaCompilation?.annotationProcessor?.argumentProviders?.add(argProvider)
}

internal fun addKaptTaskProcessorOptions(
  project: Project,
  variantIdentity: ComponentIdentity,
  argProvider: CommandLineArgumentProvider,
) =
  project.plugins.withId("com.android.legacy-kapt") {
    checkClass("org.jetbrains.kotlin.gradle.internal.KaptTask") {
      """
      The KAPT plugin was detected to be applied but its task class could not be found.

      This is an indicator that the Hilt Gradle Plugin is using a different class loader because
      it was declared at the root while KAPT was declared in a sub-project. To fix this, declare
      both plugins in the same scope, i.e. either at the root (without applying them) or at the
      sub-projects.
      """
        .trimIndent()
    }
    project.tasks.withType(KaptTask::class.java).configureEach { task ->
      if (
        task.name == "kapt${variantIdentity.name.capitalize()}Kotlin" ||
          // Task names in shared/src/AndroidMain in KMP projects has a platform suffix.
          task.name == "kapt${variantIdentity.name.capitalize()}KotlinAndroid"
      ) {
        // TODO: Update once KT-58009 is fixed.
        try {
          // Because of KT-58009, we need to add a `listOf(argProvider)` instead
          // of `argProvider`.
          @Suppress("DEPRECATION") // b/418799397
          task.annotationProcessorOptionProviders.add(listOf(argProvider))
        } catch (e: Throwable) {
          // Once KT-58009 is fixed, adding `listOf(argProvider)` will fail, we will
          // pass `argProvider` instead, which is the correct way.
          @Suppress("DEPRECATION") // b/418799397
          task.annotationProcessorOptionProviders.add(argProvider)
        }
      }
    }
  }

internal fun addKspTaskProcessorOptions(
  project: Project,
  variantIdentity: ComponentIdentity,
  argProvider: CommandLineArgumentProvider,
) =
  project.plugins.withId("com.google.devtools.ksp") {
    check(kspOneTaskClass != null || kspTwoTaskClass != null) {
      """
      The KSP plugin was detected to be applied but its task class could not be found.

      This is an indicator that the Hilt Gradle Plugin is using a different class loader because
      it was declared at the root while KSP was declared in a sub-project. To fix this, declare
      both plugins in the same scope, i.e. either at the root (without applying them) or at the
      sub-projects.

      See https://github.com/google/dagger/issues/3965 for more details.
      """
        .trimIndent()
    }
    val variantName = variantIdentity.name.capitalize()
    fun Task.matchesVariant() =
      name == "ksp${variantName}Kotlin" ||
        // Task names in shared/src/AndroidMain in KMP projects has a platform suffix.
        name == "ksp${variantName}KotlinAndroid"

    if (kspOneTaskClass != null) {
      project.tasks.withType(kspOneTaskClass).configureEach { task ->
        if (task.matchesVariant()) {
          try {
            val method = task.javaClass.getMethod("getCommandLineArgumentProviders")
            val providers = method.invoke(task)
            val addMethod = providers.javaClass.getMethod("add", Any::class.java)
            addMethod.invoke(providers, argProvider)
          } catch (e: Exception) {
            throw RuntimeException("Failed to configure KSP1 task reflectively", e)
          }
        }
      }
    }
    if (kspTwoTaskClass != null) {
      project.tasks.withType(KspAATask::class.java).configureEach { task ->
        if (task.matchesVariant()) {
          task.commandLineArgumentProviders.add(argProvider)
        }
      }
    }
  }

private inline fun checkClass(fqn: String, msg: () -> String) {
  try {
    Class.forName(fqn)
  } catch (ex: ClassNotFoundException) {
    throw IllegalStateException(msg.invoke(), ex)
  }
}

private val kspOneTaskClass: Class<out Task>? =
  try {
    Class.forName("com.google.devtools.ksp.gradle.KspTask").asSubclass(Task::class.java)
  } catch (ex: ClassNotFoundException) {
    null
  }

private val kspTwoTaskClass: Class<out Task>? =
  try {
    Class.forName("com.google.devtools.ksp.gradle.KspAATask").asSubclass(Task::class.java)
  } catch (ex: ClassNotFoundException) {
    null
  }

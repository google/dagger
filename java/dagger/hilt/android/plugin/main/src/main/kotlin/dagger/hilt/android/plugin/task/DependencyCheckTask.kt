/*
 * Copyright (C) 2025 The Dagger Authors.
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

package dagger.hilt.android.plugin.task

import dagger.hilt.android.plugin.HiltGradlePlugin.Companion.LIBRARY_GROUP
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/** Check Hilt dependencies are applied since the project has the Hilt Gradle Plugin applied. */
@DisableCachingByDefault(because = "not worth caching")
abstract class DependencyCheckTask : DefaultTask() {

  @get:Input
  abstract var runtimeDependencies: List<Pair<String?, String>>

  @get:Input
  abstract var processorDependencies: List<Pair<String?, String>>

  @get:OutputDirectory
  abstract val outputDirectory: DirectoryProperty

  @TaskAction
  fun check() {
    if (!runtimeDependencies.contains(LIBRARY_GROUP to "hilt-android")) {
      error(getMissingDepMsg("$LIBRARY_GROUP:hilt-android"))
    }

    if (
      !processorDependencies.contains(LIBRARY_GROUP to "hilt-android-compiler") &&
      !processorDependencies.contains(LIBRARY_GROUP to "hilt-compiler")
    ) {
      error(getMissingDepMsg("$LIBRARY_GROUP:hilt-compiler"))
    }
  }

  private fun getMissingDepMsg(depCoordinate: String): String =
    "The Hilt Android Gradle plugin is applied but no $depCoordinate dependency was found."
}

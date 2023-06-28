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

package dagger.hilt.android.plugin

import dagger.hilt.processor.internal.optionvalues.GradleProjectType
import org.gradle.api.tasks.Input
import org.gradle.process.CommandLineArgumentProvider

/**
 * Plugin configured annotation processor options provider.
 */
internal class HiltCommandLineArgumentProvider(
  @get:Input
  val forKsp: Boolean,
  @get:Input
  val projectType: GradleProjectType,
  @get:Input
  val enableAggregatingTask: Boolean,
  @get:Input
  val disableCrossCompilationRootValidation: Boolean
): CommandLineArgumentProvider {

  private val prefix = if (forKsp) "" else "-A"

  override fun asArguments() = buildMap {
    // Enable Dagger's fast-init, the best mode for Hilt.
    put("dagger.fastInit", "enabled")
    // Disable @AndroidEntryPoint superclass validation.
    put("dagger.hilt.android.internal.disableAndroidSuperclassValidation", "true")
    // Report project type for root validation.
    put("dagger.hilt.android.internal.projectType", projectType.toString())

    // Disable the aggregating processor if aggregating task is enabled.
    if (enableAggregatingTask) {
      put("dagger.hilt.internal.useAggregatingRootProcessor", "false")
    }
    // Disable cross compilation root validation.
    // The plugin option duplicates the processor flag because it is an input of the
    // aggregating task.
    if (disableCrossCompilationRootValidation) {
      put("dagger.hilt.disableCrossCompilationRootValidation", "true")
    }
  }.map { (key, value) -> "$prefix$key=$value" }
}
/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.hilt.android.plugin.root

// Annotations used for aggregating dependencies by the annotation processors.
internal enum class AggregatedAnnotation(val descriptor: String) {
  AGGREGATED_ROOT(
    "Ldagger/hilt/internal/aggregatedroot/AggregatedRoot;"
  ),
  PROCESSED_ROOT_SENTINEL(
    "Ldagger/hilt/internal/processedrootsentinel/ProcessedRootSentinel;"
  ),
  DEFINE_COMPONENT(
    "Ldagger/hilt/internal/definecomponent/DefineComponentClasses;"
  ),
  ALIAS_OF(
    "Ldagger/hilt/internal/aliasof/AliasOfPropagatedData;"
  ),
  AGGREGATED_DEP(
    "Ldagger/hilt/processor/internal/aggregateddeps/AggregatedDeps;"
  ),
  AGGREGATED_DEP_PROXY(
    "Ldagger/hilt/android/internal/legacy/AggregatedElementProxy;"
  ),
  AGGREGATED_UNINSTALL_MODULES(
    "Ldagger/hilt/android/internal/uninstallmodules/AggregatedUninstallModules;"
  ),
  AGGREGATED_EARLY_ENTRY_POINT(
    "Ldagger/hilt/android/internal/earlyentrypoint/AggregatedEarlyEntryPoint;"
  ),
  NONE("");

  companion object {
    fun fromString(str: String) = values().firstOrNull { it.descriptor == str } ?: NONE
  }
}

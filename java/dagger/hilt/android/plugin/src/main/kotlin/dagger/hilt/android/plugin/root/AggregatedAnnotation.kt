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
internal enum class AggregatedAnnotation(
  private val descriptor: String,
  private val aggregatedPackage: String
) {
  AGGREGATED_ROOT(
    "Ldagger/hilt/internal/aggregatedroot/AggregatedRoot;",
    "dagger/hilt/internal/aggregatedroot/codegen"
  ),
  PROCESSED_ROOT_SENTINEL(
    "Ldagger/hilt/internal/processedrootsentinel/ProcessedRootSentinel;",
    "dagger/hilt/internal/processedrootsentinel/codegen"
  ),
  DEFINE_COMPONENT(
    "Ldagger/hilt/internal/definecomponent/DefineComponentClasses;",
    "dagger/hilt/processor/internal/definecomponent/codegen"
  ),
  ALIAS_OF(
    "Ldagger/hilt/internal/aliasof/AliasOfPropagatedData;",
    "dagger/hilt/processor/internal/aliasof/codegen"
  ),
  AGGREGATED_DEP(
    "Ldagger/hilt/processor/internal/aggregateddeps/AggregatedDeps;",
    "hilt_aggregated_deps"
  ),
  AGGREGATED_DEP_PROXY(
    "Ldagger/hilt/android/internal/legacy/AggregatedElementProxy;",
    "", // Proxies share the same package name as the elements they are proxying.
  ),
  AGGREGATED_UNINSTALL_MODULES(
    "Ldagger/hilt/android/internal/uninstallmodules/AggregatedUninstallModules;",
    "dagger/hilt/android/internal/uninstallmodules/codegen"
  ),
  AGGREGATED_EARLY_ENTRY_POINT(
    "Ldagger/hilt/android/internal/earlyentrypoint/AggregatedEarlyEntryPoint;",
    "dagger/hilt/android/internal/earlyentrypoint/codegen"
  ),
  NONE("", "");

  companion object {
    fun fromString(str: String) = values().firstOrNull { it.descriptor == str } ?: NONE

    val AGGREGATED_PACKAGES = values().map { it.aggregatedPackage }.filter { it.isNotEmpty() }
  }
}

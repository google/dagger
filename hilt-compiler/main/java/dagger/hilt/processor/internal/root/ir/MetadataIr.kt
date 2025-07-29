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

package dagger.hilt.processor.internal.root.ir

import com.squareup.javapoet.ClassName

/**
 * Represents [dagger.hilt.processor.internal.aggregateddeps.AggregatedDeps]
 *
 * Even though the annotation uses arrays for modules, entryPoints and componentEntryPoints the
 * reality is that exactly only one value will be present in one of those arrays.
 */
data class AggregatedDepsIr(
  val fqName: ClassName,
  val components: List<String>,
  val test: String?,
  val replaces: List<String>,
  val module: String?,
  val entryPoint: String?,
  val componentEntryPoint: String?,
)

/** Represents [dagger.hilt.android.internal.earlyentrypoint.AggregatedEarlyEntryPoint] */
data class AggregatedEarlyEntryPointIr(val fqName: ClassName, val earlyEntryPoint: String)

/** Represents [dagger.hilt.android.internal.legacy.AggregatedElementProxy] */
data class AggregatedElementProxyIr(val fqName: ClassName, val value: ClassName)

/** Represents [dagger.hilt.internal.aggregatedroot.AggregatedRoot] */
data class AggregatedRootIr(
  val fqName: ClassName,
  val root: ClassName,
  val originatingRoot: ClassName,
  val rootAnnotation: ClassName,
  val rootComponentName: ClassName,
  // External property from the annotation that indicates if root can use a shared component.
  val allowsSharingComponent: Boolean = true,
) {
  // Equivalent to RootType.isTestRoot()
  val isTestRoot = TEST_ROOT_ANNOTATIONS.contains(rootAnnotation.toString())

  companion object {
    private val TEST_ROOT_ANNOTATIONS =
      listOf(
        "dagger.hilt.android.testing.HiltAndroidTest",
        "dagger.hilt.android.internal.testing.InternalTestRoot",
      )
  }
}

/** Represents [dagger.hilt.android.internal.uninstallmodules.AggregatedUninstallModules] */
data class AggregatedUninstallModulesIr(
  val fqName: ClassName,
  val test: String,
  val uninstallModules: List<String>,
)

/** Represents [dagger.hilt.internal.aliasof.AliasOfPropagatedData] */
data class AliasOfPropagatedDataIr(
  val fqName: ClassName,
  val defineComponentScopes: List<ClassName>,
  val alias: ClassName,
)

/** Represents [dagger.hilt.internal.componenttreedeps.ComponentTreeDeps] */
data class ComponentTreeDepsIr(
  val name: ClassName,
  val rootDeps: Set<ClassName>,
  val defineComponentDeps: Set<ClassName>,
  val aliasOfDeps: Set<ClassName>,
  val aggregatedDeps: Set<ClassName>,
  val uninstallModulesDeps: Set<ClassName>,
  val earlyEntryPointDeps: Set<ClassName>,
)

/** Represents [dagger.hilt.internal.definecomponent.DefineComponentClasses] */
data class DefineComponentClassesIr(val fqName: ClassName, val component: String)

/** Represents [dagger.hilt.internal.processedrootsentinel.ProcessedRootSentinel] */
data class ProcessedRootSentinelIr(val fqName: ClassName, val roots: List<String>)

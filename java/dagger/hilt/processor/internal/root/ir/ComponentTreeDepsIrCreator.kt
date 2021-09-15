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

// Produces ComponentTreeDepsIr for a set of aggregated deps and roots to process.
class ComponentTreeDepsIrCreator private constructor(
  private val isSharedTestComponentsEnabled: Boolean,
  private val aggregatedRoots: Set<AggregatedRootIr>,
  private val defineComponentDeps: Set<DefineComponentClassesIr>,
  private val aliasOfDeps: Set<AliasOfPropagatedDataIr>,
  private val aggregatedDeps: Set<AggregatedDepsIr>,
  private val aggregatedUninstallModulesDeps: Set<AggregatedUninstallModulesIr>,
  private val aggregatedEarlyEntryPointDeps: Set<AggregatedEarlyEntryPointIr>,
) {
  private fun prodComponents(): Set<ComponentTreeDepsIr> {
    // There should only be one prod root in a given build.
    val aggregatedRoot = aggregatedRoots.single()
    return setOf(
      ComponentTreeDepsIr(
        name = ComponentTreeDepsNameGenerator().generate(aggregatedRoot.root),
        rootDeps = setOf(aggregatedRoot.fqName),
        defineComponentDeps = defineComponentDeps.map { it.fqName }.toSet(),
        aliasOfDeps = aliasOfDeps.map { it.fqName }.toSet(),
        aggregatedDeps =
          // @AggregatedDeps with non-empty replaces are from @TestInstallIn and should not be
          // installed in production components
          aggregatedDeps.filter { it.replaces.isEmpty() }.map { it.fqName }.toSet(),
        uninstallModulesDeps = emptySet(),
        earlyEntryPointDeps = emptySet(),
      )
    )
  }

  private fun testComponents(): Set<ComponentTreeDepsIr> {
    val rootsUsingSharedComponent = rootsUsingSharedComponent(aggregatedRoots)
    val aggregatedRootsByRoot = aggregatedRoots.associateBy { it.root }
    val aggregatedDepsByRoot = aggregatedDepsByRoot(
      aggregatedRoots = aggregatedRoots,
      rootsUsingSharedComponent = rootsUsingSharedComponent,
      hasEarlyEntryPoints = aggregatedEarlyEntryPointDeps.isNotEmpty()
    )
    val uninstallModuleDepsByRoot =
      aggregatedUninstallModulesDeps.associate { it.test to it.fqName }
    return mutableSetOf<ComponentTreeDepsIr>().apply {
      aggregatedDepsByRoot.keys.forEach { root ->
        val isDefaultRoot = root == DEFAULT_ROOT_CLASS_NAME
        val isEarlyEntryPointRoot = isDefaultRoot && rootsUsingSharedComponent.isEmpty()
        // We want to base the generated name on the user written root rather than a generated root.
        val rootName = if (isDefaultRoot) {
          DEFAULT_ROOT_CLASS_NAME
        } else {
          aggregatedRootsByRoot.getValue(root).originatingRoot
        }
        val componentNameGenerator =
          if (isSharedTestComponentsEnabled) {
            ComponentTreeDepsNameGenerator(
              destinationPackage = "dagger.hilt.android.internal.testing.root",
              otherRootNames = aggregatedDepsByRoot.keys
            )
          } else {
            ComponentTreeDepsNameGenerator()
          }
        add(
          ComponentTreeDepsIr(
            name = componentNameGenerator.generate(rootName),
            rootDeps =
              // Non-default component: the root
              // Shared component: all roots sharing the component
              // EarlyEntryPoint component: empty
              if (isDefaultRoot) {
                rootsUsingSharedComponent.map { aggregatedRootsByRoot.getValue(it).fqName }.toSet()
              } else {
                setOf(aggregatedRootsByRoot.getValue(root).fqName)
              },
            defineComponentDeps = defineComponentDeps.map { it.fqName }.toSet(),
            aliasOfDeps = aliasOfDeps.map { it.fqName }.toSet(),
            aggregatedDeps = aggregatedDepsByRoot.getOrElse(root) { emptySet() },
            uninstallModulesDeps = uninstallModuleDepsByRoot[root]?.let { setOf(it) } ?: emptySet(),
            earlyEntryPointDeps =
              if (isEarlyEntryPointRoot) {
                aggregatedEarlyEntryPointDeps.map { it.fqName }.toSet()
              } else {
                emptySet()
              }
          )
        )
      }
    }
  }

  private fun rootsUsingSharedComponent(roots: Set<AggregatedRootIr>): Set<ClassName> {
    if (!isSharedTestComponentsEnabled) {
      return emptySet()
    }
    val hasLocalModuleDependencies: Set<ClassName> = mutableSetOf<ClassName>().apply {
      addAll(aggregatedDeps.filter { it.module != null }.mapNotNull { it.test })
      addAll(aggregatedUninstallModulesDeps.map { it.test })
    }
    return roots
      .filter { it.isTestRoot && it.allowsSharingComponent }
      .map { it.root }
      .filter { !hasLocalModuleDependencies.contains(it) }
      .toSet()
  }

  private fun aggregatedDepsByRoot(
    aggregatedRoots: Set<AggregatedRootIr>,
    rootsUsingSharedComponent: Set<ClassName>,
    hasEarlyEntryPoints: Boolean
  ): Map<ClassName, Set<ClassName>> {
    val testDepsByRoot = aggregatedDeps
      .filter { it.test != null }
      .groupBy(keySelector = { it.test }, valueTransform = { it.fqName })
    val globalModules = aggregatedDeps
      .filter { it.test == null && it.module != null }
      .map { it.fqName }
    val globalEntryPointsByComponent = aggregatedDeps
      .filter { it.test == null && it.module == null }
      .groupBy(keySelector = { it.test }, valueTransform = { it.fqName })
    val result = mutableMapOf<ClassName, MutableSet<ClassName>>()
    aggregatedRoots.forEach { aggregatedRoot ->
      if (!rootsUsingSharedComponent.contains(aggregatedRoot.root)) {
        result.getOrPut(aggregatedRoot.root) { mutableSetOf() }.apply {
          addAll(globalModules)
          addAll(globalEntryPointsByComponent.values.flatten())
          addAll(testDepsByRoot.getOrElse(aggregatedRoot.root) { emptyList() })
        }
      }
    }
    // Add the Default/EarlyEntryPoint root if necessary.
    if (rootsUsingSharedComponent.isNotEmpty()) {
      result.getOrPut(DEFAULT_ROOT_CLASS_NAME) { mutableSetOf() }.apply {
        addAll(globalModules)
        addAll(globalEntryPointsByComponent.values.flatten())
        addAll(rootsUsingSharedComponent.flatMap { testDepsByRoot.getOrElse(it) { emptyList() } })
      }
    } else if (hasEarlyEntryPoints) {
      result.getOrPut(DEFAULT_ROOT_CLASS_NAME) { mutableSetOf() }.apply {
        addAll(globalModules)
        addAll(
          globalEntryPointsByComponent.entries
            .filterNot { (component, _) -> component == SINGLETON_COMPONENT_CLASS_NAME }
            .flatMap { (_, entryPoints) -> entryPoints }
        )
      }
    }
    return result
  }

  /**
   * Generates a component name for a tree that will be based off the given root after mapping it to
   * the [destinationPackage] and disambiguating from [otherRootNames].
   */
  private class ComponentTreeDepsNameGenerator(
    private val destinationPackage: String? = null,
    private val otherRootNames: Collection<ClassName> = emptySet()
  ) {
    private val simpleNameMap: Map<ClassName, String> by lazy {
      mutableMapOf<ClassName, String>().apply {
        otherRootNames.groupBy { it.enclosedName() }.values.forEach { conflictingRootNames ->
          if (conflictingRootNames.size == 1) {
            // If there's only 1 root there's nothing to disambiguate so return the simple name.
            put(conflictingRootNames.first(), conflictingRootNames.first().enclosedName())
          } else {
            // There are conflicting simple names, so disambiguate them with a unique prefix.
            // We keep them small to fix https://github.com/google/dagger/issues/421.
            // Sorted in order to guarantee determinism if this is invoked by different processors.
            val usedNames = mutableSetOf<String>()
            conflictingRootNames.sorted().forEach { rootClassName ->
              val basePrefix = rootClassName.let { className ->
                val containerName = className.enclosingClassName()?.enclosedName() ?: ""
                if (containerName.isNotEmpty() && containerName[0].isUpperCase()) {
                  // If parent element looks like a class, use its initials as a prefix.
                  containerName.filterNot { it.isLowerCase() }
                } else {
                  // Not in a normally named class. Prefix with the initials of the elements
                  // leading here.
                  className.toString().split('.').dropLast(1)
                    .joinToString(separator = "") { "${it.first()}" }
                }
              }
              var uniqueName = basePrefix
              var differentiator = 2
              while (!usedNames.add(uniqueName)) {
                uniqueName = basePrefix + differentiator++
              }
              put(rootClassName, "${uniqueName}_${rootClassName.enclosedName()}")
            }
          }
        }
      }
    }

    fun generate(rootName: ClassName): ClassName =
      ClassName.get(
        destinationPackage ?: rootName.packageName(),
        if (otherRootNames.isEmpty()) {
          rootName.enclosedName()
        } else {
          simpleNameMap.getValue(rootName)
        }
      ).append("_ComponentTreeDeps")

    private fun ClassName.enclosedName() = simpleNames().joinToString(separator = "_")

    private fun ClassName.append(suffix: String) = peerClass(simpleName() + suffix)
  }

  companion object {

    @JvmStatic
    fun components(
      isTest: Boolean,
      isSharedTestComponentsEnabled: Boolean,
      aggregatedRoots: Set<AggregatedRootIr>,
      defineComponentDeps: Set<DefineComponentClassesIr>,
      aliasOfDeps: Set<AliasOfPropagatedDataIr>,
      aggregatedDeps: Set<AggregatedDepsIr>,
      aggregatedUninstallModulesDeps: Set<AggregatedUninstallModulesIr>,
      aggregatedEarlyEntryPointDeps: Set<AggregatedEarlyEntryPointIr>,
    ) = ComponentTreeDepsIrCreator(
      isSharedTestComponentsEnabled,
      aggregatedRoots,
      defineComponentDeps,
      aliasOfDeps,
      aggregatedDeps,
      aggregatedUninstallModulesDeps,
      aggregatedEarlyEntryPointDeps
    ).let { producer ->
      if (isTest) {
        producer.testComponents()
      } else {
        producer.prodComponents()
      }
    }

    val DEFAULT_ROOT_CLASS_NAME: ClassName =
      ClassName.get("dagger.hilt.android.internal.testing.root", "Default")
    val SINGLETON_COMPONENT_CLASS_NAME: ClassName =
      ClassName.get("dagger.hilt.components", "SingletonComponent")
  }
}

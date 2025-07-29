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
class ComponentTreeDepsIrCreator
private constructor(
  private val isSharedTestComponentsEnabled: Boolean,
  private val aggregatedRoots: Set<AggregatedRootIr>,
  private val defineComponentDeps: Set<DefineComponentClassesIr>,
  private val aliasOfDeps: Set<AliasOfPropagatedDataIr>,
  private val aggregatedDeps: Set<AggregatedDepsIr>,
  private val aggregatedUninstallModulesDeps: Set<AggregatedUninstallModulesIr>,
  private val aggregatedEarlyEntryPointDeps: Set<AggregatedEarlyEntryPointIr>,
) {
  private fun prodComponents(): Set<ComponentTreeDepsIr> {
    val componentTreeDeps = mutableSetOf<ComponentTreeDepsIr>()
    aggregatedRoots.filter { !it.isTestRoot }.forEach { aggregatedRoot ->
      componentTreeDeps.add(ComponentTreeDepsIr(
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
      ))
    }
    return componentTreeDeps
  }

  private fun testComponents(): Set<ComponentTreeDepsIr> {
    val rootsUsingSharedComponent = rootsUsingSharedComponent(aggregatedRoots)
    val aggregatedRootsByRoot = aggregatedRoots.filter { it.isTestRoot }.associateBy { it.root }
    val aggregatedDepsByRoot =
      aggregatedDepsByRoot(
        aggregatedRoots = aggregatedRoots,
        rootsUsingSharedComponent = rootsUsingSharedComponent,
        hasEarlyEntryPoints = aggregatedEarlyEntryPointDeps.isNotEmpty()
      )
    val uninstallModuleDepsByRoot =
      aggregatedUninstallModulesDeps.associate { it.test to it.fqName }
    return mutableSetOf<ComponentTreeDepsIr>().apply {
      aggregatedDepsByRoot.keys.forEach { root ->
        val isDefaultRoot = root == DEFAULT_ROOT_CLASS_NAME
        val isEarlyEntryPointRoot = isDefaultRoot && aggregatedEarlyEntryPointDeps.isNotEmpty()
        // We want to base the generated name on the user written root rather than a generated root.
        val rootName =
          if (isDefaultRoot) {
            DEFAULT_ROOT_CLASS_NAME
          } else if (aggregatedRootsByRoot.containsKey(root)) {
            aggregatedRootsByRoot.getValue(root).originatingRoot
          } else {
            // If it isn't contained in the map of roots, it is a production root and can be skipped
            return@forEach
          }
        val componentNameGenerator =
          if (isSharedTestComponentsEnabled) {
            ComponentTreeDepsNameGenerator(
              destinationPackage = "dagger.hilt.android.internal.testing.root",
              otherRootNames = aggregatedDepsByRoot.keys,
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
            uninstallModulesDeps =
              uninstallModuleDepsByRoot[root.canonicalName()]?.let { setOf(it) } ?: emptySet(),
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
    val hasLocalModuleDependencies: Set<String> =
      mutableSetOf<String>().apply {
        addAll(aggregatedDeps.filter { it.module != null }.mapNotNull { it.test })
        addAll(aggregatedUninstallModulesDeps.map { it.test })
      }
    return roots
      .filter { it.isTestRoot && it.allowsSharingComponent }
      .map { it.root }
      .filter { !hasLocalModuleDependencies.contains(it.canonicalName()) }
      .toSet()
  }

  private fun aggregatedDepsByRoot(
    aggregatedRoots: Set<AggregatedRootIr>,
    rootsUsingSharedComponent: Set<ClassName>,
    hasEarlyEntryPoints: Boolean
  ): Map<ClassName, Set<ClassName>> {
    val testDepsByRoot =
      aggregatedDeps
        .filter { it.test != null }
        .groupBy(keySelector = { it.test }, valueTransform = { it.fqName })
    val globalModules =
      aggregatedDeps.filter { it.test == null && it.module != null }.map { it.fqName }
    val globalEntryPointsByComponent =
      aggregatedDeps
        .filter { it.test == null && it.module == null }
        .groupBy(keySelector = { it.test }, valueTransform = { it.fqName })
    val result = mutableMapOf<ClassName, LinkedHashSet<ClassName>>()
    aggregatedRoots.forEach { aggregatedRoot ->
      if (!rootsUsingSharedComponent.contains(aggregatedRoot.root)) {
        result.getOrPut(aggregatedRoot.root) { linkedSetOf() }.apply {
          addAll(globalModules)
          addAll(globalEntryPointsByComponent.values.flatten())
          addAll(testDepsByRoot.getOrElse(aggregatedRoot.root.canonicalName()) { emptyList() })
        }
      }
    }
    // Add the Default/EarlyEntryPoint root if necessary.
    if (rootsUsingSharedComponent.isNotEmpty()) {
      result.getOrPut(DEFAULT_ROOT_CLASS_NAME) { linkedSetOf() }.apply {
        addAll(globalModules)
        addAll(globalEntryPointsByComponent.values.flatten())
        addAll(
          rootsUsingSharedComponent.flatMap {
            testDepsByRoot.getOrElse(it.canonicalName()) { emptyList() }
          }
        )
      }
    } else if (hasEarlyEntryPoints) {
      result.getOrPut(DEFAULT_ROOT_CLASS_NAME) { linkedSetOf() }.apply {
        addAll(globalModules)
        addAll(
          globalEntryPointsByComponent.entries
            .filterNot { (component, _) ->
              component == SINGLETON_COMPONENT_CLASS_NAME.canonicalName()
            }
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
              val basePrefix =
                rootClassName.let { className ->
                  val containerName = className.enclosingClassName()?.enclosedName() ?: ""
                  if (containerName.isNotEmpty() && containerName[0].isUpperCase()) {
                    // If parent element looks like a class, use its initials as a prefix.
                    containerName.filterNot { it.isLowerCase() }
                  } else {
                    // Not in a normally named class. Prefix with the initials of the elements
                    // leading here.
                    className.toString().split('.').dropLast(1).joinToString(separator = "") {
                      "${it.first()}"
                    }
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
        )
        .append("_ComponentTreeDeps")

    private fun ClassName.enclosedName() = simpleNames().joinToString(separator = "_")

    private fun ClassName.append(suffix: String) = peerClass(simpleName() + suffix)
  }

  companion object {

    @JvmStatic
    fun components(
      isSharedTestComponentsEnabled: Boolean,
      aggregatedRoots: Set<AggregatedRootIr>,
      defineComponentDeps: Set<DefineComponentClassesIr>,
      aliasOfDeps: Set<AliasOfPropagatedDataIr>,
      aggregatedDeps: Set<AggregatedDepsIr>,
      aggregatedUninstallModulesDeps: Set<AggregatedUninstallModulesIr>,
      aggregatedEarlyEntryPointDeps: Set<AggregatedEarlyEntryPointIr>,
    ): Set<ComponentTreeDepsIr> {
      val creator = ComponentTreeDepsIrCreator(
          isSharedTestComponentsEnabled,
          // TODO(bcorso): Consider creating a common interface for fqName so that we can sort these
          // using a shared method rather than repeating the sorting logic.
          aggregatedRoots.toList().sortedBy { it.fqName.canonicalName() }.toSet(),
          defineComponentDeps.toList().sortedBy { it.fqName.canonicalName() }.toSet(),
          aliasOfDeps.toList().sortedBy { it.fqName.canonicalName() }.toSet(),
          aggregatedDeps.toList().sortedBy { it.fqName.canonicalName() }.toSet(),
          aggregatedUninstallModulesDeps.toList().sortedBy { it.fqName.canonicalName() }.toSet(),
          aggregatedEarlyEntryPointDeps.toList().sortedBy { it.fqName.canonicalName() }.toSet()
      )

      // AggregatedRootIrValidator should enforce rules on the roots, so just handle both prod and
      // test roots.
      val componentTreeDeps = mutableSetOf<ComponentTreeDepsIr>()

      // Only add test components if there are test roots though as this will automatically add a
      // default root.
      if (aggregatedRoots.stream().anyMatch(AggregatedRootIr::isTestRoot)) {
        componentTreeDeps.addAll(creator.testComponents())
      }
      componentTreeDeps.addAll(creator.prodComponents())
      return componentTreeDeps
    }

    val DEFAULT_ROOT_CLASS_NAME: ClassName =
      ClassName.get("dagger.hilt.android.internal.testing.root", "Default")
    val SINGLETON_COMPONENT_CLASS_NAME: ClassName =
      ClassName.get("dagger.hilt.components", "SingletonComponent")
  }
}

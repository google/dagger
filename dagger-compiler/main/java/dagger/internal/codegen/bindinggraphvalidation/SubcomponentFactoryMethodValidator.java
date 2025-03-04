/*
 * Copyright (C) 2018 The Dagger Authors.
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

package dagger.internal.codegen.bindinggraphvalidation;

import static com.google.common.collect.Sets.union;
import static dagger.internal.codegen.binding.ComponentRequirement.componentCanMakeNewInstances;
import static dagger.internal.codegen.extension.DaggerStreams.instancesOf;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static java.util.stream.Collectors.joining;
import static javax.tools.Diagnostic.Kind.ERROR;

import androidx.room.compiler.processing.XExecutableType;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import dagger.internal.codegen.base.Util;
import dagger.internal.codegen.binding.ComponentNodeImpl;
import dagger.internal.codegen.model.BindingGraph;
import dagger.internal.codegen.model.BindingGraph.ChildFactoryMethodEdge;
import dagger.internal.codegen.model.BindingGraph.ComponentNode;
import dagger.internal.codegen.model.DiagnosticReporter;
import dagger.internal.codegen.validation.ValidationBindingGraphPlugin;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javax.inject.Inject;

/** Reports an error if a subcomponent factory method is missing required modules. */
final class SubcomponentFactoryMethodValidator extends ValidationBindingGraphPlugin {

  private final Map<ComponentNode, Set<XTypeElement>> inheritedModulesCache = new HashMap<>();

  @Inject
  SubcomponentFactoryMethodValidator() {}

  @Override
  public String pluginName() {
    return "Dagger/SubcomponentFactoryMethodMissingModule";
  }

  @Override
  public void visitGraph(BindingGraph bindingGraph, DiagnosticReporter diagnosticReporter) {
    if (!bindingGraph.rootComponentNode().isRealComponent()
        || bindingGraph.rootComponentNode().isSubcomponent()) {
      // We don't know all the modules that might be owned by the child until we know the real root
      // component, which we don't if the root component node is really a module or a subcomponent.
      return;
    }
    bindingGraph.network().edges().stream()
        .flatMap(instancesOf(ChildFactoryMethodEdge.class))
        .forEach(
            edge -> {
              ImmutableSet<XTypeElement> missingModules = findMissingModules(edge, bindingGraph);
              if (!missingModules.isEmpty()) {
                reportMissingModuleParameters(
                    edge, missingModules, bindingGraph, diagnosticReporter);
              }
            });
  }

  private ImmutableSet<XTypeElement> findMissingModules(
      ChildFactoryMethodEdge edge, BindingGraph graph) {
    ImmutableSet<XTypeElement> factoryMethodParameters =
        subgraphFactoryMethodParameters(edge, graph);
    ComponentNode child = (ComponentNode) graph.network().incidentNodes(edge).target();
    SetView<XTypeElement> modulesOwnedByChild = ownedModules(child, graph);
    return graph.bindings().stream()
        // bindings owned by child
        .filter(binding -> binding.componentPath().equals(child.componentPath()))
        // that require a module instance
        .filter(binding -> binding.requiresModuleInstance())
        .map(binding -> binding.contributingModule().get().xprocessing())
        .distinct()
        // module owned by child
        .filter(module -> modulesOwnedByChild.contains(module))
        // module not in the method parameters
        .filter(module -> !factoryMethodParameters.contains(module))
        // module doesn't have an accessible no-arg constructor
        .filter(moduleType -> !componentCanMakeNewInstances(moduleType))
        .collect(toImmutableSet());
  }

  private ImmutableSet<XTypeElement> subgraphFactoryMethodParameters(
      ChildFactoryMethodEdge edge, BindingGraph bindingGraph) {
    ComponentNode parent = (ComponentNode) bindingGraph.network().incidentNodes(edge).source();
    XType parentType = parent.componentPath().currentComponent().xprocessing().getType();
    XExecutableType factoryMethodType = edge.factoryMethod().xprocessing().asMemberOf(parentType);
    return factoryMethodType.getParameterTypes().stream()
        .map(XType::getTypeElement)
        .collect(toImmutableSet());
  }

  private SetView<XTypeElement> ownedModules(ComponentNode component, BindingGraph graph) {
    return Sets.difference(
        ((ComponentNodeImpl) component).componentDescriptor().moduleTypes(),
        inheritedModules(component, graph));
  }

  private Set<XTypeElement> inheritedModules(ComponentNode component, BindingGraph graph) {
    return Util.reentrantComputeIfAbsent(
        inheritedModulesCache, component, uncachedInheritedModules(graph));
  }

  private Function<ComponentNode, Set<XTypeElement>> uncachedInheritedModules(BindingGraph graph) {
    return componentNode ->
        componentNode.componentPath().atRoot()
            ? ImmutableSet.of()
            : graph
                .componentNode(componentNode.componentPath().parent())
                .map(parent -> union(ownedModules(parent, graph), inheritedModules(parent, graph)))
                .get();
  }

  private void reportMissingModuleParameters(
      ChildFactoryMethodEdge edge,
      ImmutableSet<XTypeElement> missingModules,
      BindingGraph graph,
      DiagnosticReporter diagnosticReporter) {
    diagnosticReporter.reportSubcomponentFactoryMethod(
        ERROR,
        edge,
        "%s requires modules which have no visible default constructors. "
            + "Add the following modules as parameters to this method: %s",
        graph
            .network()
            .incidentNodes(edge)
            .target()
            .componentPath()
            .currentComponent()
            .xprocessing()
            .getQualifiedName(),
        missingModules.stream()
            .map(XTypeElement::getQualifiedName)
            .collect(joining(", ")));
  }
}

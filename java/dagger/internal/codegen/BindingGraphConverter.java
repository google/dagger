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

package dagger.internal.codegen;

import static dagger.internal.codegen.DaggerStreams.toImmutableSet;
import static dagger.model.BindingGraphProxies.childFactoryMethodEdge;
import static dagger.model.BindingGraphProxies.componentNode;
import static dagger.model.BindingGraphProxies.dependencyEdge;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.MutableNetwork;
import com.google.common.graph.NetworkBuilder;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.model.BindingGraph.BindingNode;
import dagger.model.BindingGraph.ComponentNode;
import dagger.model.BindingGraph.Edge;
import dagger.model.BindingGraph.Node;
import dagger.model.BindingGraphProxies;
import dagger.model.DependencyRequest;
import java.util.Collection;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;

/** Converts {@link dagger.internal.codegen.BindingGraph}s to {@link dagger.model.BindingGraph}s. */
final class BindingGraphConverter {

  private final BindingDeclarationFormatter bindingDeclarationFormatter;

  @Inject
  BindingGraphConverter(BindingDeclarationFormatter bindingDeclarationFormatter) {
    this.bindingDeclarationFormatter = bindingDeclarationFormatter;
  }

  /**
   * Creates the external {@link dagger.model.BindingGraph} representing the given internal root
   * {@link dagger.internal.codegen.BindingGraph}.
   */
  dagger.model.BindingGraph convert(BindingGraph rootGraph) {
    Traverser traverser = new Traverser(rootGraph);
    traverser.traverseComponents();
    return BindingGraphProxies.bindingGraph(traverser.network);
  }

  private final class Traverser extends ComponentTreeTraverser {

    private final MutableNetwork<Node, Edge> network =
        NetworkBuilder.directed().allowsParallelEdges(true).allowsSelfLoops(true).build();

    private ComponentNode parentComponent;
    private ComponentNode currentComponent;

    Traverser(BindingGraph graph) {
      super(graph);
    }

    @Override
    protected void visitComponent(BindingGraph graph) {
      ComponentNode grandparentComponent = parentComponent;
      parentComponent = currentComponent;
      currentComponent =
          componentNode(
              componentTreePath().toComponentPath(), graph.componentDescriptor().entryPoints());

      network.addNode(currentComponent);

      for (ComponentMethodDescriptor method : graph.componentDescriptor().entryPointMethods()) {
        addDependencyEdges(currentComponent, method.dependencyRequest().get(), graph);
      }

      for (ResolvedBindings resolvedBindings : graph.resolvedBindings()) {
        bindingNodes(resolvedBindings).forEach(node -> addBindingNode(node, graph));
      }

      super.visitComponent(graph);

      currentComponent = parentComponent;
      parentComponent = grandparentComponent;
    }

    @Override
    protected void visitSubcomponentFactoryMethod(
        BindingGraph graph, BindingGraph parent, ExecutableElement factoryMethod) {
      network.addEdge(parentComponent, currentComponent, childFactoryMethodEdge(factoryMethod));
      super.visitSubcomponentFactoryMethod(graph, parent, factoryMethod);
    }

    /**
     * Adds a {@link dagger.model.BindingGraph.DependencyEdge} from a node to the binding(s) that
     * satisfy a dependency request.
     */
    private void addDependencyEdges(
        Node source, DependencyRequest dependencyRequest, BindingGraph graph) {
      for (BindingNode dependency :
          bindingNodes(graph.resolvedBindings(dependencyRequest.kind(), dependencyRequest.key()))) {
        network.addEdge(
            source, dependency, dependencyEdge(dependencyRequest, source instanceof ComponentNode));
      }
    }

    private ImmutableSet<BindingNode> bindingNodes(ResolvedBindings resolvedBindings) {
      ImmutableSet.Builder<BindingNode> bindingNodes = ImmutableSet.builder();
      resolvedBindings
          .allBindings()
          .asMap()
          .forEach(
              (component, bindings) -> {
                for (Binding binding : bindings) {
                  bindingNodes.add(bindingNode(resolvedBindings, binding, component));
                }
              });
      return bindingNodes.build();
    }

    /** Adds a binding node and edges for all its dependencies. */
    private void addBindingNode(BindingNode node, BindingGraph graph) {
      network.addNode(node);
      for (DependencyRequest dependencyRequest : node.binding().dependencies()) {
        addDependencyEdges(node, dependencyRequest, graph);
      }
    }

    private BindingNode bindingNode(
        ResolvedBindings resolvedBindings, Binding binding, ComponentDescriptor owningComponent) {
      return BindingGraphProxies.bindingNode(
          componentTreePath().pathFromRootToAncestor(owningComponent).toComponentPath(),
          binding,
          associatedDeclaringElements(resolvedBindings),
          () -> bindingDeclarationFormatter.format(binding));
    }

    private ImmutableSet<Element> associatedDeclaringElements(ResolvedBindings resolvedBindings) {
      return ImmutableList.of(
              resolvedBindings.multibindingDeclarations(),
              resolvedBindings.optionalBindingDeclarations(),
              resolvedBindings.subcomponentDeclarations())
          .stream()
          .flatMap(Collection::stream)
          .map(declaration -> declaration.bindingElement().get())
          .collect(toImmutableSet());
    }
  }
}

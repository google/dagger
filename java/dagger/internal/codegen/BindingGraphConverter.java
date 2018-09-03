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

import static com.google.auto.common.MoreTypes.asTypeElement;
import static dagger.internal.codegen.DaggerGraphs.unreachableNodes;
import static dagger.internal.codegen.DaggerStreams.instancesOf;
import static dagger.internal.codegen.DaggerStreams.presentValues;
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;
import static dagger.model.BindingGraphProxies.childFactoryMethodEdge;
import static dagger.model.BindingGraphProxies.dependencyEdge;
import static dagger.model.BindingGraphProxies.subcomponentBuilderBindingEdge;
import static dagger.model.BindingKind.SUBCOMPONENT_BUILDER;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.graph.MutableNetwork;
import com.google.common.graph.Network;
import com.google.common.graph.NetworkBuilder;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.model.BindingGraph.BindingNode;
import dagger.model.BindingGraph.ComponentNode;
import dagger.model.BindingGraph.DependencyEdge;
import dagger.model.BindingGraph.Edge;
import dagger.model.BindingGraph.MissingBindingNode;
import dagger.model.BindingGraph.Node;
import dagger.model.BindingGraphProxies;
import dagger.model.DependencyRequest;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/** Converts {@link dagger.internal.codegen.BindingGraph}s to {@link dagger.model.BindingGraph}s. */
final class BindingGraphConverter {

  private final BindingDeclarationFormatter bindingDeclarationFormatter;
  private final CompilerOptions compilerOptions;

  @Inject
  BindingGraphConverter(
      BindingDeclarationFormatter bindingDeclarationFormatter, CompilerOptions compilerOptions) {
    this.bindingDeclarationFormatter = bindingDeclarationFormatter;
    this.compilerOptions = compilerOptions;
  }

  /**
   * Creates the external {@link dagger.model.BindingGraph} representing the given internal root
   * {@link dagger.internal.codegen.BindingGraph}.
   */
  dagger.model.BindingGraph convert(BindingGraph rootGraph) {
    Traverser traverser = new Traverser(rootGraph);
    traverser.traverseComponents();

    // When bindings are copied down into child graphs because they transitively depend on local
    // multibindings or optional bindings, the parent-owned binding is still there. If that
    // parent-owned binding is not reachable from its component, it doesn't need to be in the graph
    // because it will never be used. So remove all nodes that are not reachable from the root
    // component.
    unreachableNodes(traverser.network.asGraph(), rootComponentNode(traverser.network))
        .forEach(traverser.network::removeNode);

    return BindingGraphProxies.bindingGraph(traverser.network);
  }

  // TODO(dpb): Example of BindingGraph logic applied to derived networks.
  private ComponentNode rootComponentNode(Network<Node, Edge> network) {
    return (ComponentNode)
        Iterables.find(
            network.nodes(),
            node -> node instanceof ComponentNode && node.componentPath().atRoot());
  }

  private final class Traverser extends ComponentTreeTraverser {

    private final MutableNetwork<Node, Edge> network =
        NetworkBuilder.directed().allowsParallelEdges(true).allowsSelfLoops(true).build();

    private ComponentNode parentComponent;
    private ComponentNode currentComponent;

    Traverser(BindingGraph graph) {
      super(graph, compilerOptions);
    }

    @Override
    protected void visitComponent(BindingGraph graph) {
      ComponentNode grandparentComponent = parentComponent;
      parentComponent = currentComponent;
      currentComponent =
          ComponentNodeImpl.create(
              componentTreePath().toComponentPath(), graph.componentDescriptor());

      network.addNode(currentComponent);

      for (ComponentMethodDescriptor method : graph.componentDescriptor().entryPointMethods()) {
        addDependencyEdges(currentComponent, method.dependencyRequest().get());
      }

      for (ResolvedBindings resolvedBindings : graph.resolvedBindings()) {
        for (BindingNode node : bindingNodes(resolvedBindings)) {
          addBindingNode(node);
          if (node.binding().kind().equals(SUBCOMPONENT_BUILDER)
              && node.componentPath().equals(currentComponent.componentPath())) {
            network.addEdge(
                node,
                subcomponentNode(node.binding().key().type(), graph),
                subcomponentBuilderBindingEdge(subcomponentDeclaringModules(resolvedBindings)));
          }
        }
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
    private void addDependencyEdges(Node source, DependencyRequest dependencyRequest) {
      ResolvedBindings dependencies = resolvedDependencies(source, dependencyRequest);
      if (dependencies.isEmpty()) {
        addDependencyEdge(source, dependencyRequest, missingBindingNode(dependencies));
      } else {
        for (BindingNode dependency : bindingNodes(dependencies)) {
          addDependencyEdge(source, dependencyRequest, dependency);
        }
      }
    }

    private void addDependencyEdge(
        Node source, DependencyRequest dependencyRequest, Node dependency) {
      network.addNode(dependency);
      if (!hasDependencyEdge(source, dependency, dependencyRequest)) {
        network.addEdge(
            source, dependency, dependencyEdge(dependencyRequest, source instanceof ComponentNode));
      }
    }

    private boolean hasDependencyEdge(
        Node source, Node dependency, DependencyRequest dependencyRequest) {
      return network
          .edgesConnecting(source, dependency)
          .stream()
          .flatMap(instancesOf(DependencyEdge.class))
          .anyMatch(edge -> edge.dependencyRequest().equals(dependencyRequest));
    }

    private ResolvedBindings resolvedDependencies(
        Node source, DependencyRequest dependencyRequest) {
      return componentTreePath()
          .pathFromRootToAncestor(source.componentPath().currentComponent())
          .currentGraph()
          .resolvedBindings(dependencyRequest.kind(), dependencyRequest.key());
    }

    /** Adds a binding node and edges for all its dependencies. */
    private void addBindingNode(BindingNode node) {
      network.addNode(node);
      for (DependencyRequest dependencyRequest : node.binding().dependencies()) {
        addDependencyEdges(node, dependencyRequest);
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

    private BindingNode bindingNode(
        ResolvedBindings resolvedBindings, Binding binding, ComponentDescriptor owningComponent) {
      return BindingNodeImpl.create(
          componentTreePath()
              .pathFromRootToAncestor(owningComponent.componentDefinitionType())
              .toComponentPath(),
          binding,
          associatedDeclaringElements(resolvedBindings),
          () -> bindingDeclarationFormatter.format(binding));
    }

    private Iterable<BindingDeclaration> associatedDeclaringElements(
        ResolvedBindings resolvedBindings) {
      return Iterables.concat(
          resolvedBindings.multibindingDeclarations(),
          resolvedBindings.optionalBindingDeclarations(),
          resolvedBindings.subcomponentDeclarations());
    }

    private MissingBindingNode missingBindingNode(ResolvedBindings dependencies) {
      return BindingGraphProxies.missingBindingNode(
          componentTreePath()
              .pathFromRootToAncestor(dependencies.owningComponent().componentDefinitionType())
              .toComponentPath(),
          dependencies.key());
    }

    private ComponentNode subcomponentNode(TypeMirror subcomponentBuilderType, BindingGraph graph) {
      TypeElement subcomponentBuilderElement = asTypeElement(subcomponentBuilderType);
      ComponentDescriptor subcomponent =
          graph.componentDescriptor().subcomponentsByBuilderType().get(subcomponentBuilderElement);
      return ComponentNodeImpl.create(
          componentTreePath().childPath(subcomponent.componentDefinitionType()).toComponentPath(),
          subcomponent);
    }

    private ImmutableSet<TypeElement> subcomponentDeclaringModules(
        ResolvedBindings resolvedBindings) {
      return resolvedBindings
          .subcomponentDeclarations()
          .stream()
          .map(SubcomponentDeclaration::contributingModule)
          .flatMap(presentValues())
          .collect(toImmutableSet());
    }
  }
}

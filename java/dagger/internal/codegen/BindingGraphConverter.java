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
import static dagger.internal.codegen.BindingRequest.bindingRequest;
import static dagger.internal.codegen.DaggerGraphs.unreachableNodes;
import static dagger.model.BindingKind.SUBCOMPONENT_CREATOR;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.graph.MutableNetwork;
import com.google.common.graph.Network;
import com.google.common.graph.NetworkBuilder;
import dagger.model.BindingGraph.ComponentNode;
import dagger.model.BindingGraph.DependencyEdge;
import dagger.model.BindingGraph.Edge;
import dagger.model.BindingGraph.MissingBinding;
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

  @Inject
  BindingGraphConverter(BindingDeclarationFormatter bindingDeclarationFormatter) {
    this.bindingDeclarationFormatter = bindingDeclarationFormatter;
  }

  /**
   * Creates the external {@link dagger.model.BindingGraph} representing the given internal {@link
   * dagger.internal.codegen.BindingGraph}.
   */
  dagger.model.BindingGraph convert(BindingGraph bindingGraph) {
    Traverser traverser = new Traverser(bindingGraph);
    traverser.traverseComponents();

    // When bindings are copied down into child graphs because they transitively depend on local
    // multibindings or optional bindings, the parent-owned binding is still there. If that
    // parent-owned binding is not reachable from its component, it doesn't need to be in the graph
    // because it will never be used. So remove all nodes that are not reachable from the root
    // component—unless we're converting a full binding graph.
    if (!bindingGraph.isFullBindingGraph()) {
      unreachableNodes(traverser.network.asGraph(), rootComponentNode(traverser.network))
          .forEach(traverser.network::removeNode);
    }

    return BindingGraphProxies.bindingGraph(traverser.network, bindingGraph.isFullBindingGraph());
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
      super(graph);
    }

    @Override
    protected void visitComponent(BindingGraph graph) {
      ComponentNode grandparentComponent = parentComponent;
      parentComponent = currentComponent;
      currentComponent = ComponentNodeImpl.create(componentPath(), graph.componentDescriptor());

      network.addNode(currentComponent);

      for (ResolvedBindings resolvedBindings : graph.resolvedBindings()) {
        for (BindingNode binding : bindingNodes(resolvedBindings)) {
          addBinding(binding);
          if (binding.kind().equals(SUBCOMPONENT_CREATOR)
              && binding.componentPath().equals(currentComponent.componentPath())) {
            network.addEdge(
                binding,
                subcomponentNode(binding.key().type(), graph),
                new SubcomponentCreatorBindingEdgeImpl(
                    resolvedBindings.subcomponentDeclarations()));
          }
        }
      }

      super.visitComponent(graph);

      currentComponent = parentComponent;
      parentComponent = grandparentComponent;
    }

    @Override
    protected void visitEntryPoint(DependencyRequest entryPoint, BindingGraph graph) {
      addDependencyEdges(currentComponent, entryPoint);
      super.visitEntryPoint(entryPoint, graph);
    }

    @Override
    protected void visitSubcomponentFactoryMethod(
        BindingGraph graph, BindingGraph parent, ExecutableElement factoryMethod) {
      network.addEdge(
          parentComponent, currentComponent, new ChildFactoryMethodEdgeImpl(factoryMethod));
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
            source,
            dependency,
            new DependencyEdgeImpl(dependencyRequest, source instanceof ComponentNode));
      }
    }

    private boolean hasDependencyEdge(
        Node source, Node dependency, DependencyRequest dependencyRequest) {
      // An iterative approach is used instead of a Stream because this method is called in a hot
      // loop, and the Stream calculates the size of network.edgesConnecting(), which is slow. This
      // seems to be because caculating the edges connecting two nodes in a Network that supports
      // parallel edges is must check the equality of many nodes, and BindingNode's equality
      // semantics drag in the equality of many other expensive objects
      for (Edge edge : network.edgesConnecting(source, dependency)) {
        if (edge instanceof DependencyEdge) {
          if (((DependencyEdge) edge).dependencyRequest().equals(dependencyRequest)) {
            return true;
          }
        }
      }
      return false;
    }

    private ResolvedBindings resolvedDependencies(
        Node source, DependencyRequest dependencyRequest) {
      return graphForAncestor(source.componentPath().currentComponent())
          .resolvedBindings(bindingRequest(dependencyRequest));
    }

    /** Adds a binding and all its dependencies. */
    private void addBinding(BindingNode binding) {
      network.addNode(binding);
      for (DependencyRequest dependencyRequest : binding.dependencies()) {
        addDependencyEdges(binding, dependencyRequest);
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
        ResolvedBindings resolvedBindings, Binding binding, TypeElement owningComponent) {
      return BindingNode.create(
          pathFromRootToAncestor(owningComponent),
          binding,
          resolvedBindings.multibindingDeclarations(),
          resolvedBindings.optionalBindingDeclarations(),
          resolvedBindings.subcomponentDeclarations(),
          bindingDeclarationFormatter);
    }

    private MissingBinding missingBindingNode(ResolvedBindings dependencies) {
      // TODO(ronshapiro): Revisit whether missing binding nodes should all use the root component
      // path, and the component(s) that have the missing bindings should be determined by the
      // predecessors of missing binding nodes.
      return BindingGraphProxies.missingBindingNode(componentPath(), dependencies.key());
    }

    private ComponentNode subcomponentNode(TypeMirror subcomponentBuilderType, BindingGraph graph) {
      TypeElement subcomponentBuilderElement = asTypeElement(subcomponentBuilderType);
      ComponentDescriptor subcomponent =
          graph.componentDescriptor().getChildComponentWithBuilderType(subcomponentBuilderElement);
      return ComponentNodeImpl.create(
          componentPath().childPath(subcomponent.typeElement()), subcomponent);
    }
  }
}

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

package dagger.internal.codegen.binding;

import static com.google.common.base.Verify.verify;
import static dagger.internal.codegen.binding.BindingRequest.bindingRequest;
import static dagger.internal.codegen.extension.DaggerGraphs.unreachableNodes;
import static dagger.internal.codegen.model.BindingKind.SUBCOMPONENT_CREATOR;

import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableList;
import com.google.common.graph.ImmutableNetwork;
import com.google.common.graph.MutableNetwork;
import com.google.common.graph.NetworkBuilder;
import dagger.internal.codegen.binding.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.binding.LegacyBindingGraphFactory.LegacyBindingGraph;
import dagger.internal.codegen.binding.LegacyBindingGraphFactory.LegacyResolvedBindings;
import dagger.internal.codegen.model.BindingGraph.ComponentNode;
import dagger.internal.codegen.model.BindingGraph.DependencyEdge;
import dagger.internal.codegen.model.BindingGraph.Edge;
import dagger.internal.codegen.model.BindingGraph.MissingBinding;
import dagger.internal.codegen.model.BindingGraph.Node;
import dagger.internal.codegen.model.ComponentPath;
import dagger.internal.codegen.model.DaggerTypeElement;
import dagger.internal.codegen.model.DependencyRequest;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;

/** Converts {@link BindingGraph}s to {@link dagger.internal.codegen.model.BindingGraph}s. */
final class LegacyBindingGraphConverter {
  @Inject
  LegacyBindingGraphConverter() {}

  /**
   * Creates the external {@link dagger.internal.codegen.model.BindingGraph} representing the given
   * internal {@link BindingGraph}.
   */
  BindingGraph convert(LegacyBindingGraph legacyBindingGraph, boolean isFullBindingGraph) {
    MutableNetwork<Node, Edge> network = Converter.convertToNetwork(legacyBindingGraph);
    ComponentNode rootNode = legacyBindingGraph.componentNode();

    // When bindings are copied down into child graphs because they transitively depend on local
    // multibindings or optional bindings, the parent-owned binding is still there. If that
    // parent-owned binding is not reachable from its component, it doesn't need to be in the graph
    // because it will never be used. So remove all nodes that are not reachable from the root
    // component—unless we're converting a full binding graph.
    if (!isFullBindingGraph) {
      unreachableNodes(network.asGraph(), rootNode).forEach(network::removeNode);
    }

    return BindingGraph.create(
        ImmutableNetwork.copyOf(network),
        isFullBindingGraph);
  }

  private static final class Converter {
    static MutableNetwork<Node, Edge> convertToNetwork(LegacyBindingGraph graph) {
      Converter converter = new Converter();
      converter.visitRootComponent(graph);
      return converter.network;
    }

    /** The path from the root graph to the currently visited graph. */
    private final Deque<LegacyBindingGraph> bindingGraphPath = new ArrayDeque<>();

    private final MutableNetwork<Node, Edge> network =
        NetworkBuilder.directed().allowsParallelEdges(true).allowsSelfLoops(true).build();
    private final Set<BindingNode> bindings = new HashSet<>();

    private void visitRootComponent(LegacyBindingGraph graph) {
      visitComponent(graph);
    }

    /**
     * Called once for each component in a component hierarchy.
     *
     * <p>This implementation does the following:
     *
     * <ol>
     *   <li>If this component is installed in its parent by a subcomponent factory method, adds
     *       an edge between the parent and child components.
     *   <li>For each entry point, adds an edge between the component and the entry point.
     *   <li>For each child component, calls {@link #visitComponent(LegacyBindingGraph)},
     *       updating the traversal state.
     * </ol>
     *
     * @param graph the currently visited graph
     */
    private void visitComponent(LegacyBindingGraph graph) {
      bindingGraphPath.addLast(graph);

      network.addNode(graph.componentNode());

      for (ComponentMethodDescriptor entryPointMethod :
          graph.componentDescriptor().entryPointMethods()) {
        addDependencyEdges(graph.componentNode(), entryPointMethod.dependencyRequest().get());
      }

      for (LegacyResolvedBindings resolvedBindings : graph.resolvedBindings()) {
        for (BindingNode binding : resolvedBindings.bindingNodes()) {
          if (bindings.add(binding)) {
            network.addNode(binding);
            for (DependencyRequest dependencyRequest : binding.dependencies()) {
              addDependencyEdges(binding, dependencyRequest);
            }
          }
          if (binding.kind().equals(SUBCOMPONENT_CREATOR)
              && binding.componentPath().equals(graph.componentPath())) {
            network.addEdge(
                binding,
                subcomponentNode(binding.key().type().xprocessing(), graph),
                new SubcomponentCreatorBindingEdgeImpl(binding.subcomponentDeclarations()));
          }
        }
      }

      for (LegacyBindingGraph childGraph : graph.subgraphs()) {
        visitComponent(childGraph);
        graph
            .componentDescriptor()
            .getFactoryMethodForChildComponent(childGraph.componentDescriptor())
            .ifPresent(
                childFactoryMethod ->
                    network.addEdge(
                        graph.componentNode(),
                        childGraph.componentNode(),
                        new ChildFactoryMethodEdgeImpl(childFactoryMethod.methodElement())));
      }

      verify(bindingGraphPath.removeLast().equals(graph));
    }

    /**
     * Returns an immutable snapshot of the path from the root component to the currently visited
     * component.
     */
    private ComponentPath componentPath() {
      return bindingGraphPath.getLast().componentPath();
    }

    /**
     * Returns the LegacyBindingGraph for {@code ancestor}, where {@code ancestor} is in the
     * component path of the current traversal.
     */
    private LegacyBindingGraph graphForAncestor(XTypeElement ancestor) {
      for (LegacyBindingGraph graph : bindingGraphPath) {
        if (graph.componentDescriptor().typeElement().equals(ancestor)) {
          return graph;
        }
      }
      throw new IllegalArgumentException(
          String.format(
              "%s is not in the current path: %s", ancestor.getQualifiedName(), componentPath()));
    }

    /**
     * Adds a {@link dagger.internal.codegen.model.BindingGraph.DependencyEdge} from a node to the
     * binding(s) that satisfy a dependency request.
     */
    private void addDependencyEdges(Node source, DependencyRequest dependencyRequest) {
      LegacyResolvedBindings dependencies = resolvedDependencies(source, dependencyRequest);
      if (dependencies.isEmpty()) {
        addDependencyEdge(source, dependencyRequest, missingBindingNode(dependencies));
      } else {
        for (BindingNode dependency : dependencies.bindingNodes()) {
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

    private LegacyResolvedBindings resolvedDependencies(
        Node source, DependencyRequest dependencyRequest) {
      return graphForAncestor(source.componentPath().currentComponent().xprocessing())
          .resolvedBindings(bindingRequest(dependencyRequest));
    }

    private MissingBinding missingBindingNode(LegacyResolvedBindings dependencies) {
      // Put all missing binding nodes in the root component. This simplifies the binding graph
      // and produces better error messages for users since all dependents point to the same node.
      return MissingBindingImpl.create(
          ComponentPath.create(ImmutableList.of(componentPath().rootComponent())),
          dependencies.key());
    }

    private ComponentNode subcomponentNode(
        XType subcomponentBuilderType, LegacyBindingGraph graph) {
      XTypeElement subcomponentBuilderElement = subcomponentBuilderType.getTypeElement();
      ComponentDescriptor subcomponent =
          graph.componentDescriptor().getChildComponentWithBuilderType(subcomponentBuilderElement);
      return ComponentNodeImpl.create(
          componentPath().childPath(DaggerTypeElement.from(subcomponent.typeElement())),
          subcomponent);
    }
  }
}

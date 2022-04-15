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
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.spi.model.BindingKind.SUBCOMPONENT_CREATOR;

import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.graph.ImmutableNetwork;
import com.google.common.graph.MutableNetwork;
import com.google.common.graph.Network;
import com.google.common.graph.NetworkBuilder;
import dagger.internal.codegen.binding.BindingGraph.TopLevelBindingGraph;
import dagger.internal.codegen.binding.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.spi.model.BindingGraph.ComponentNode;
import dagger.spi.model.BindingGraph.DependencyEdge;
import dagger.spi.model.BindingGraph.Edge;
import dagger.spi.model.BindingGraph.MissingBinding;
import dagger.spi.model.BindingGraph.Node;
import dagger.spi.model.ComponentPath;
import dagger.spi.model.DaggerExecutableElement;
import dagger.spi.model.DaggerTypeElement;
import dagger.spi.model.DependencyRequest;
import dagger.spi.model.Key;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;

/** Converts {@link BindingGraph}s to {@link dagger.spi.model.BindingGraph}s. */
final class BindingGraphConverter {
  private final BindingDeclarationFormatter bindingDeclarationFormatter;

  @Inject
  BindingGraphConverter(BindingDeclarationFormatter bindingDeclarationFormatter) {
    this.bindingDeclarationFormatter = bindingDeclarationFormatter;
  }

  /**
   * Creates the external {@link dagger.spi.model.BindingGraph} representing the given internal
   * {@link BindingGraph}.
   */
  BindingGraph convert(LegacyBindingGraph legacyBindingGraph, boolean isFullBindingGraph) {
    MutableNetwork<Node, Edge> network = asNetwork(legacyBindingGraph);
    ComponentNode rootNode = rootComponentNode(network);

    // When bindings are copied down into child graphs because they transitively depend on local
    // multibindings or optional bindings, the parent-owned binding is still there. If that
    // parent-owned binding is not reachable from its component, it doesn't need to be in the graph
    // because it will never be used. So remove all nodes that are not reachable from the root
    // component—unless we're converting a full binding graph.
    if (!isFullBindingGraph) {
      unreachableNodes(network.asGraph(), rootNode).forEach(network::removeNode);
    }

    TopLevelBindingGraph topLevelBindingGraph =
        TopLevelBindingGraph.create(ImmutableNetwork.copyOf(network), isFullBindingGraph);
    return BindingGraph.create(rootNode, topLevelBindingGraph);
  }

  private MutableNetwork<Node, Edge> asNetwork(LegacyBindingGraph graph) {
    Converter converter = new Converter();
    converter.visitRootComponent(graph);
    return converter.network;
  }

  // TODO(dpb): Example of BindingGraph logic applied to derived networks.
  private ComponentNode rootComponentNode(Network<Node, Edge> network) {
    return (ComponentNode)
        Iterables.find(
            network.nodes(),
            node -> node instanceof ComponentNode && node.componentPath().atRoot());
  }

  /**
   * Used as a cache key to make sure resolved bindings are cached per component path.
   * This is required so that binding nodes are not reused across different branches of the
   * graph since the ResolvedBindings class only contains the component and not the path.
   */
  @AutoValue
  abstract static class ResolvedBindingsWithPath {
    abstract ResolvedBindings resolvedBindings();
    abstract ComponentPath componentPath();

    static ResolvedBindingsWithPath create(
        ResolvedBindings resolvedBindings, ComponentPath componentPath) {
      return new AutoValue_BindingGraphConverter_ResolvedBindingsWithPath(
          resolvedBindings, componentPath);
    }
  }

  private final class Converter {
    /** The path from the root graph to the currently visited graph. */
    private final Deque<LegacyBindingGraph> bindingGraphPath = new ArrayDeque<>();

    /** The {@link ComponentPath} for each component in {@link #bindingGraphPath}. */
    private final Deque<ComponentPath> componentPaths = new ArrayDeque<>();

    private final MutableNetwork<Node, Edge> network =
        NetworkBuilder.directed().allowsParallelEdges(true).allowsSelfLoops(true).build();
    private final Set<BindingNode> bindings = new HashSet<>();

    private final Map<ResolvedBindingsWithPath, ImmutableSet<BindingNode>> resolvedBindingsMap =
        new HashMap<>();

    private void visitRootComponent(LegacyBindingGraph graph) {
      visitComponent(graph, null);
    }

    /**
     * Called once for each component in a component hierarchy.
     *
     * <p>This implementation does the following:
     *
     * <ol>
     *   <li>If this component is installed in its parent by a subcomponent factory method, calls
     *       {@link #visitSubcomponentFactoryMethod(ComponentNode, ComponentNode, XMethodElement)}.
     *   <li>For each entry point in the component, calls {@link #visitEntryPoint(ComponentNode,
     *       DependencyRequest)}.
     *   <li>For each child component, calls {@link #visitComponent(LegacyBindingGraph,
     *       ComponentNode)}, updating the traversal state.
     * </ol>
     *
     * @param graph the currently visited graph
     */
    private void visitComponent(LegacyBindingGraph graph, ComponentNode parentComponent) {
      bindingGraphPath.addLast(graph);
      ComponentPath graphPath =
          ComponentPath.create(
              bindingGraphPath.stream()
                  .map(LegacyBindingGraph::componentDescriptor)
                  .map(ComponentDescriptor::typeElement)
                  .map(DaggerTypeElement::from)
                  .collect(toImmutableList()));
      componentPaths.addLast(graphPath);
      ComponentNode currentComponent =
          ComponentNodeImpl.create(componentPath(), graph.componentDescriptor());

      network.addNode(currentComponent);

      for (ComponentMethodDescriptor entryPointMethod :
          graph.componentDescriptor().entryPointMethods()) {
        visitEntryPoint(currentComponent, entryPointMethod.dependencyRequest().get());
      }

      for (ResolvedBindings resolvedBindings : graph.resolvedBindings()) {
        for (BindingNode binding : bindingNodes(resolvedBindings)) {
          if (bindings.add(binding)) {
            network.addNode(binding);
            for (DependencyRequest dependencyRequest : binding.dependencies()) {
              addDependencyEdges(binding, dependencyRequest);
            }
          }
          if (binding.kind().equals(SUBCOMPONENT_CREATOR)
              && binding.componentPath().equals(currentComponent.componentPath())) {
            network.addEdge(
                binding,
                subcomponentNode(binding.key().type().xprocessing(), graph),
                new SubcomponentCreatorBindingEdgeImpl(
                    resolvedBindings.subcomponentDeclarations()));
          }
        }
      }

      if (bindingGraphPath.size() > 1) {
        LegacyBindingGraph parent = Iterators.get(bindingGraphPath.descendingIterator(), 1);
        parent
            .componentDescriptor()
            .getFactoryMethodForChildComponent(graph.componentDescriptor())
            .ifPresent(
                childFactoryMethod ->
                    visitSubcomponentFactoryMethod(
                        parentComponent, currentComponent, childFactoryMethod.methodElement()));
      }

      for (LegacyBindingGraph child : graph.subgraphs()) {
        visitComponent(child, currentComponent);
      }

      verify(bindingGraphPath.removeLast().equals(graph));
      verify(componentPaths.removeLast().equals(graphPath));
    }

    /**
     * Called once for each entry point in a component.
     *
     * @param componentNode the component that contains the entry point
     * @param entryPoint the entry point to visit
     */
    private void visitEntryPoint(ComponentNode componentNode, DependencyRequest entryPoint) {
      addDependencyEdges(componentNode, entryPoint);
    }

    /**
     * Called if this component was installed in its parent by a subcomponent factory method.
     *
     * @param parentComponent the parent graph
     * @param currentComponent the currently visited graph
     * @param factoryMethod the factory method in the parent component that declares that the
     *     current component is a child
     */
    private void visitSubcomponentFactoryMethod(
        ComponentNode parentComponent,
        ComponentNode currentComponent,
        XMethodElement factoryMethod) {
      network.addEdge(
          parentComponent,
          currentComponent,
          new ChildFactoryMethodEdgeImpl(DaggerExecutableElement.from(factoryMethod)));
    }

    /**
     * Returns an immutable snapshot of the path from the root component to the currently visited
     * component.
     */
    private ComponentPath componentPath() {
      return componentPaths.getLast();
    }

    /**
     * Returns the subpath from the root component to the matching {@code ancestor} of the current
     * component.
     */
    private ComponentPath pathFromRootToAncestor(XTypeElement ancestor) {
      for (ComponentPath componentPath : componentPaths) {
        if (componentPath.currentComponent().xprocessing().equals(ancestor)) {
          return componentPath;
        }
      }
      throw new IllegalArgumentException(
          String.format(
              "%s is not in the current path: %s", ancestor.getQualifiedName(), componentPath()));
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
     * Adds a {@link dagger.spi.model.BindingGraph.DependencyEdge} from a node to the binding(s)
     * that satisfy a dependency request.
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
      return graphForAncestor(source.componentPath().currentComponent().xprocessing())
          .resolvedBindings(bindingRequest(dependencyRequest));
    }

    private ImmutableSet<BindingNode> bindingNodes(ResolvedBindings resolvedBindings) {
      ResolvedBindingsWithPath resolvedBindingsWithPath =
          ResolvedBindingsWithPath.create(resolvedBindings, componentPath());
      return resolvedBindingsMap.computeIfAbsent(
          resolvedBindingsWithPath, this::uncachedBindingNodes);
    }

    private ImmutableSet<BindingNode> uncachedBindingNodes(
        ResolvedBindingsWithPath resolvedBindingsWithPath) {
      ImmutableSet.Builder<BindingNode> bindingNodes = ImmutableSet.builder();
      resolvedBindingsWithPath.resolvedBindings()
          .allBindings()
          .asMap()
          .forEach(
              (component, bindings) -> {
                for (Binding binding : bindings) {
                  bindingNodes.add(
                      bindingNode(resolvedBindingsWithPath.resolvedBindings(), binding, component));
                }
              });
      return bindingNodes.build();
    }

    private BindingNode bindingNode(
        ResolvedBindings resolvedBindings, Binding binding, XTypeElement owningComponent) {
      return BindingNode.create(
          pathFromRootToAncestor(owningComponent),
          binding,
          resolvedBindings.multibindingDeclarations(),
          resolvedBindings.optionalBindingDeclarations(),
          resolvedBindings.subcomponentDeclarations(),
          bindingDeclarationFormatter);
    }

    private MissingBinding missingBindingNode(ResolvedBindings dependencies) {
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

  @AutoValue
  abstract static class MissingBindingImpl extends MissingBinding {
    static MissingBinding create(ComponentPath component, Key key) {
      return new AutoValue_BindingGraphConverter_MissingBindingImpl(component, key);
    }

    @Memoized
    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(Object o);
  }
}

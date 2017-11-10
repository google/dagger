/*
 * Copyright (C) 2016 The Dagger Authors.
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

import static com.google.auto.common.MoreElements.asType;
import static com.google.auto.common.MoreTypes.asTypeElement;
import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Sets.intersection;
import static com.google.common.graph.Graphs.inducedSubgraph;
import static com.google.common.graph.Graphs.reachableNodes;
import static com.google.common.graph.Graphs.transpose;
import static dagger.internal.codegen.BindingKey.contribution;
import static dagger.internal.codegen.BindingKey.membersInjection;
import static dagger.internal.codegen.ContributionBinding.Kind.SUBCOMPONENT_BUILDER;
import static dagger.internal.codegen.DaggerStreams.instancesOf;
import static dagger.internal.codegen.DaggerStreams.toImmutableMap;
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableNetwork;
import com.google.common.graph.MutableNetwork;
import com.google.common.graph.Network;
import com.google.common.graph.NetworkBuilder;
import dagger.BindsOptionalOf;
import dagger.Module;
import dagger.internal.codegen.ComponentTreeTraverser.ComponentTreePath;
import dagger.multibindings.Multibinds;
import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/**
 * The immutable graph of bindings, dependency requests, and components for a valid root component.
 *
 * <h3>Nodes</h3>
 *
 * <p>There is a <b>{@linkplain BindingNode binding node}</b> for each owned binding in the graph.
 * If a binding is owned by more than one component, there is one binding node for that binding for
 * every owning component.
 *
 * <p>There is a <b>{@linkplain ComponentNode component node}</b> (without a binding) for each
 * component in the graph.
 *
 * <h3>Edges</h3>
 *
 * <p>There is a <b>{@linkplain DependencyEdge dependency edge}</b> for each dependency request in
 * the graph. Its target node is the binding node for the binding that satisfies the request. For
 * entry point dependency requests, the source node is the component node for the component for
 * which it is an entry point. For other dependency requests, the source node is the binding node
 * for the binding that contains the request.
 *
 * <p>There is a <b>subcomponent edge</b> for each parent-child component relationship in the graph.
 * The target node is the component node for the child component. For subcomponents defined by a
 * {@linkplain SubcomponentBuilderBindingEdge subcomponent builder binding} (either a method on the
 * component or a set of {@code @Module.subcomponents} annotation values), the source node is the
 * binding node for the {@code @Subcomponent.Builder} type. For subcomponents defined by {@linkplain
 * ChildFactoryMethodEdge subcomponent factory methods}, the source node is the component node for
 * the parent.
 */
// TODO(dpb): Represent graphs with missing or conflicting bindings.
public final class BindingNetwork
    extends ForwardingNetwork<BindingNetwork.Node, BindingNetwork.Edge> {

  /** Creates a {@link BindingNetwork} representing the given root {@link BindingGraph}. */
  static BindingNetwork create(BindingGraph graph) {
    Factory factory = new Factory(graph);
    factory.traverseComponents();
    return factory.bindingNetwork();
  }

  private BindingNetwork(Network<Node, Edge> bindingNetwork) {
    super(ImmutableNetwork.copyOf(bindingNetwork));
  }

  /** Returns the binding nodes. */
  public ImmutableSet<BindingNode> bindingNodes() {
    return bindingNodesStream().collect(toImmutableSet());
  }

  /** Returns the binding nodes for a binding. */
  public ImmutableSet<BindingNode> bindingNodes(BindingKey bindingKey) {
    return bindingNodesStream()
        .filter(node -> node.bindingKey().equals(bindingKey))
        .collect(toImmutableSet());
  }

  /** Returns the component nodes. */
  public ImmutableSet<ComponentNode> componentNodes() {
    return componentNodeStream().collect(toImmutableSet());
  }

  /** Returns the component node for a component. */
  public Optional<ComponentNode> componentNode(ComponentTreePath component) {
    return componentNodeStream()
        .filter(node -> node.componentTreePath().equals(component))
        .findFirst();
  }

  /** Returns the component nodes for a component. */
  public ImmutableSet<ComponentNode> componentNodes(TypeElement component) {
    return componentNodeStream()
        .filter(node -> node.componentTreePath().currentComponent().equals(component))
        .collect(toImmutableSet());
  }

  /** Returns the component node for the root component. */
  public ComponentNode rootComponentNode() {
    return componentNodeStream()
        .filter(node -> node.componentTreePath().atRoot())
        .findFirst()
        .get();
  }

  /** Returns the dependency edges. */
  public ImmutableSet<DependencyEdge> dependencyEdges() {
    return dependencyEdgeStream().collect(toImmutableSet());
  }

  /** Returns the dependency edges for the dependencies of a binding. */
  public ImmutableMap<DependencyRequest, DependencyEdge> dependencyEdges(BindingNode bindingNode) {
    return outEdges(bindingNode)
        .stream()
        .flatMap(instancesOf(DependencyEdge.class))
        .collect(toImmutableMap(DependencyEdge::dependencyRequest, edge -> edge));
  }

  /** Returns the dependency edges from any binding for a dependency request. */
  public ImmutableSet<DependencyEdge> dependencyEdges(DependencyRequest dependencyRequest) {
    return dependencyEdgeStream()
        .filter(edge -> edge.dependencyRequest().equals(dependencyRequest))
        .collect(toImmutableSet());
  }

  /**
   * Returns the dependency edges for all entry points for all components and subcomponents. Each
   * edge's source node is a component node.
   */
  public ImmutableSet<DependencyEdge> entryPointEdges() {
    return entryPointEdgeStream().collect(toImmutableSet());
  }

  /**
   * Returns the dependency edges for the entry points of a given {@code component}. Each edge's
   * source node is that component's component node.
   */
  public ImmutableSet<DependencyEdge> entryPointEdges(ComponentTreePath component) {
    return outEdges(componentNode(component).get())
        .stream()
        .flatMap(instancesOf(DependencyEdge.class))
        .collect(toImmutableSet());
  }

  /** Returns the binding nodes for bindings that directly satisfy entry points. */
  public ImmutableSet<BindingNode> entryPointBindingNodes() {
    return entryPointEdgeStream()
        .map(edge -> (BindingNode) incidentNodes(edge).target())
        .collect(toImmutableSet());
  }

  /** Returns the edges for entry points that transitively depend on a binding. */
  public ImmutableSet<DependencyEdge> entryPointEdgesDependingOnBindingNode(
      BindingNode bindingNode) {
    Network<Node, Edge> subgraphDependingOnBindingNode =
        inducedSubgraph(this, reachableNodes(transpose(this).asGraph(), bindingNode));
    return ImmutableSet.copyOf(
        intersection(entryPointEdges(), subgraphDependingOnBindingNode.edges()));
  }

  private Stream<BindingNode> bindingNodesStream() {
    return nodes().stream().flatMap(instancesOf(BindingNode.class));
  }

  private Stream<ComponentNode> componentNodeStream() {
    return nodes().stream().flatMap(instancesOf(ComponentNode.class));
  }

  private Stream<DependencyEdge> dependencyEdgeStream() {
    return edges().stream().flatMap(instancesOf(DependencyEdge.class));
  }

  private Stream<DependencyEdge> entryPointEdgeStream() {
    return dependencyEdgeStream().filter(DependencyEdge::isEntryPoint);
  }

  /**
   * An edge in the binding graph. Either a {@link DependencyEdge}, a {@link
   * ChildFactoryMethodEdge}, or a {@link SubcomponentBuilderBindingEdge}.
   */
  public interface Edge {}

  /**
   * An edge that represents a dependency on a binding.
   *
   * <p>Because one {@link DependencyRequest} may represent a dependency from two bindings (e.g., a
   * dependency of {@code Foo<String>} and {@code Foo<Number>} may have the same key and request
   * element), this class does not override {@link #equals(Object)} to use value semantics.
   */
  public static final class DependencyEdge implements Edge {

    private final DependencyRequest dependencyRequest;
    private final boolean entryPoint;

    private DependencyEdge(DependencyRequest dependencyRequest, boolean entryPoint) {
      this.dependencyRequest = dependencyRequest;
      this.entryPoint = entryPoint;
    }

    /** The dependency request. */
    public DependencyRequest dependencyRequest() {
      return dependencyRequest;
    }

    /** Returns {@code true} if this edge represents an entry point. */
    public boolean isEntryPoint() {
      return entryPoint;
    }

    @Override
    public String toString() {
      return toStringHelper(this)
          .add("dependencyRequest", dependencyRequest)
          .add("entryPoint", entryPoint)
          .toString();
    }
  }

  /**
   * An edge that represents a subcomponent factory method linking a parent component to a child
   * subcomponent.
   */
  public static final class ChildFactoryMethodEdge implements Edge {

    private final ExecutableElement factoryMethod;

    private ChildFactoryMethodEdge(ExecutableElement factoryMethod) {
      this.factoryMethod = factoryMethod;
    }

    /** The subcomponent factory method element. */
    public ExecutableElement factoryMethod() {
      return factoryMethod;
    }

    @Override
    public String toString() {
      return toStringHelper(this).add("factoryMethod", factoryMethod).toString();
    }
  }

  /**
   * An edge that represents the link between a parent component and a child subcomponent implied by
   * a subcomponent builder binding.
   */
  public static final class SubcomponentBuilderBindingEdge implements Edge {

    private final ImmutableSet<SubcomponentDeclaration> subcomponentDeclarations;

    private SubcomponentBuilderBindingEdge(
        Iterable<SubcomponentDeclaration> subcomponentDeclarations) {
      this.subcomponentDeclarations = ImmutableSet.copyOf(subcomponentDeclarations);
    }

    /**
     * The {@code @Module.subcomponents} declarations that generated this edge. May be empty if the
     * parent component has a subcomponent builder method.
     */
    public ImmutableSet<SubcomponentDeclaration> subcomponentDeclarations() {
      return subcomponentDeclarations;
    }

    @Override
    public String toString() {
      return toStringHelper(this)
          .add("subcomponentDeclarations", subcomponentDeclarations)
          .toString();
    }
  }

  /** A node in the binding graph. Either a {@link BindingNode} or a {@link ComponentNode}. */
  public interface Node {
    /** The component this node belongs to. */
    ComponentTreePath componentTreePath();
  }

  /**
   * A <b>binding node</b> in the binding graph. If a binding is owned by more than one component,
   * there is one binding node for that binding for every owning component.
   */
  // TODO(dpb): Should this be a value type?
  public static final class BindingNode implements Node {

    private final ComponentTreePath component;
    private final Binding binding;
    private final ImmutableSet<BindingDeclaration> associatedDeclarations;

    private BindingNode(
        ComponentTreePath component,
        Binding binding,
        Iterable<BindingDeclaration> associatedDeclarations) {
      this.component = component;
      this.binding = binding;
      this.associatedDeclarations = ImmutableSet.copyOf(associatedDeclarations);
    }

    /** The component that owns the {@link #binding()}. */
    @Override
    public ComponentTreePath componentTreePath() {
      return component;
    }

    /** The binding. */
    Binding binding() {
      return binding;
    }

    /** The binding key for this binding. */
    // TODO(dpb): Put this on Binding.
    public BindingKey bindingKey() {
      switch (binding.bindingType()) {
        case MEMBERS_INJECTION:
          return membersInjection(binding.key());

        case PRODUCTION:
        case PROVISION:
          return contribution(binding.key());

        default:
          throw new AssertionError(binding);
      }
    }

    /**
     * The declarations (other than the binding's {@link Binding#bindingElement()}) that are
     * associated with the binding.
     *
     * <ul>
     *   <li>For {@linkplain BindsOptionalOf optional bindings}, the {@link
     *       OptionalBindingDeclaration}s.
     *   <li>For {@linkplain Module#subcomponents() module subcomponents}, the {@link
     *       SubcomponentDeclaration}s.
     *   <li>For {@linkplain Multibinds multibindings}, the {@link MultibindingDeclaration}s.
     * </ul>
     */
    public ImmutableSet<BindingDeclaration> associatedDeclarations() {
      return associatedDeclarations;
    }

    @Override
    public String toString() {
      return toStringHelper(this)
          .add("component", component)
          .add("binding", binding)
          .add("associatedDeclarations", associatedDeclarations)
          .toString();
    }
  }

  /**
   * A <b>component node</b> in the graph. Every entry point {@linkplain DependencyEdge dependency
   * edge}'s source node is a component node for the component containing the entry point.
   */
  @AutoValue
  public abstract static class ComponentNode implements Node {

    /** The component represented by this node. */
    @Override
    public abstract ComponentTreePath componentTreePath();

    private static ComponentNode create(ComponentTreePath component) {
      return new AutoValue_BindingNetwork_ComponentNode(component);
    }
  }

  private static class Factory extends ComponentTreeTraverser {

    private final MutableNetwork<Node, Edge> network =
        NetworkBuilder.directed().allowsParallelEdges(true).allowsSelfLoops(true).build();

    private ComponentNode parentComponent;
    private ComponentNode currentComponent;

    Factory(BindingGraph graph) {
      super(graph);
    }

    @Override
    protected void visitComponent(BindingGraph graph) {
      ComponentNode grandparentNode = parentComponent;
      parentComponent = currentComponent;
      currentComponent = ComponentNode.create(componentTreePath());
      network.addNode(currentComponent);
      super.visitComponent(graph);
      currentComponent = parentComponent;
      parentComponent = grandparentNode;
    }

    @Override
    protected void visitSubcomponentFactoryMethod(
        BindingGraph graph, BindingGraph parent, ExecutableElement factoryMethod) {
      network.addEdge(parentComponent, currentComponent, new ChildFactoryMethodEdge(factoryMethod));
      super.visitSubcomponentFactoryMethod(graph, parent, factoryMethod);
    }

    @Override
    protected BindingGraphTraverser bindingGraphTraverser(
        ComponentTreePath componentPath, DependencyRequest entryPoint) {
      return new BindingGraphVisitor(componentPath, entryPoint);
    }

    BindingNetwork bindingNetwork() {
      return new BindingNetwork(network);
    }

    private final class BindingGraphVisitor extends BindingGraphTraverser {

      private Node current;

      BindingGraphVisitor(ComponentTreePath componentPath, DependencyRequest entryPoint) {
        super(componentPath, entryPoint);
        current = currentComponent;
        network.addNode(current);
      }

      @Override
      protected void visitBinding(Binding binding, ComponentDescriptor owningComponent) {
        // TODO(dpb): Should we visit only bindings owned by the current component, since other
        // bindings will be visited in the parent?
        Node previous = current;
        current = newBindingNode(resolvedBindings(), binding, owningComponent);
        network.addNode(current);
        if (binding instanceof ContributionBinding) {
          ContributionBinding contributionBinding = (ContributionBinding) binding;
          if (contributionBinding.bindingKind().equals(SUBCOMPONENT_BUILDER)) {
            network.addEdge(
                current,
                subcomponentNode(contributionBinding, owningComponent),
                new SubcomponentBuilderBindingEdge(resolvedBindings().subcomponentDeclarations()));
          }
        }
        if (network
            .edgesConnecting(previous, current)
            .stream()
            .flatMap(instancesOf(DependencyEdge.class))
            .noneMatch(e -> e.dependencyRequest().equals(dependencyRequest()))) {
          network.addEdge(
              previous, current, new DependencyEdge(dependencyRequest(), atEntryPoint()));
          super.visitBinding(binding, owningComponent);
        }
        current = previous;
      }

      private ComponentNode subcomponentNode(
          ContributionBinding binding, ComponentDescriptor subcomponentParent) {
        checkArgument(binding.bindingKind().equals(SUBCOMPONENT_BUILDER));
        TypeElement builderType = asTypeElement(binding.key().type());
        TypeElement subcomponentType = asType(builderType.getEnclosingElement());
        ComponentTreePath childPath =
            componentTreePath()
                .pathFromRootToAncestor(subcomponentParent)
                .childPath(subcomponentType);
        ComponentNode childNode = ComponentNode.create(childPath);
        network.addNode(childNode);
        return childNode;
      }

      private BindingNode newBindingNode(
          ResolvedBindings resolvedBindings, Binding binding, ComponentDescriptor owningComponent) {
        return new BindingNode(
            componentTreePath().pathFromRootToAncestor(owningComponent),
            binding,
            concat(
                resolvedBindings.multibindingDeclarations(),
                resolvedBindings.optionalBindingDeclarations(),
                resolvedBindings.subcomponentDeclarations()));
      }
    }
  }
}

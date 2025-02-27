/*
 * Copyright (C) 2014 The Dagger Authors.
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

import static com.google.common.collect.Iterables.transform;
import static dagger.internal.codegen.binding.BindingRequest.bindingRequest;
import static dagger.internal.codegen.extension.DaggerCollectors.toOptional;
import static dagger.internal.codegen.extension.DaggerStreams.instancesOf;
import static dagger.internal.codegen.extension.DaggerStreams.presentValues;
import static dagger.internal.codegen.extension.DaggerStreams.stream;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableMap;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import androidx.room.compiler.processing.XExecutableElement;
import androidx.room.compiler.processing.XExecutableParameterElement;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.google.common.graph.ImmutableNetwork;
import com.google.common.graph.Traverser;
import dagger.internal.codegen.base.TarjanSCCs;
import dagger.internal.codegen.binding.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.model.BindingGraph.ChildFactoryMethodEdge;
import dagger.internal.codegen.model.BindingGraph.ComponentNode;
import dagger.internal.codegen.model.BindingGraph.DependencyEdge;
import dagger.internal.codegen.model.BindingGraph.Edge;
import dagger.internal.codegen.model.BindingGraph.Node;
import dagger.internal.codegen.model.ComponentPath;
import dagger.internal.codegen.model.DaggerTypeElement;
import dagger.internal.codegen.model.DependencyRequest;
import dagger.internal.codegen.model.Key;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A graph that represents a single component or subcomponent within a fully validated top-level
 * binding graph.
 */
@AutoValue
public abstract class BindingGraph {

  /**
   * A graph that represents the entire network of nodes from all components, subcomponents and
   * their bindings.
   */
  @AutoValue
  public abstract static class TopLevelBindingGraph
      extends dagger.internal.codegen.model.BindingGraph {
    private static TopLevelBindingGraph create(
        ImmutableNetwork<Node, Edge> network,
        boolean isFullBindingGraph) {
      TopLevelBindingGraph topLevelBindingGraph =
          new AutoValue_BindingGraph_TopLevelBindingGraph(network, isFullBindingGraph);

      ImmutableMap<ComponentPath, ComponentNode> componentNodes =
          topLevelBindingGraph.componentNodes().stream()
              .collect(
                  toImmutableMap(ComponentNode::componentPath, componentNode -> componentNode));

      ImmutableSetMultimap.Builder<ComponentNode, ComponentNode> subcomponentNodesBuilder =
          ImmutableSetMultimap.builder();
      topLevelBindingGraph.componentNodes().stream()
          .filter(componentNode -> !componentNode.componentPath().atRoot())
          .forEach(
              componentNode ->
                  subcomponentNodesBuilder.put(
                      componentNodes.get(componentNode.componentPath().parent()), componentNode));

      // Set these fields directly on the instance rather than passing these in as input to the
      // AutoValue to prevent exposing this data outside of the class.
      topLevelBindingGraph.componentNodes = componentNodes;
      topLevelBindingGraph.subcomponentNodes = subcomponentNodesBuilder.build();
      topLevelBindingGraph.frameworkTypeBindings =
          frameworkRequestBindingSet(network, topLevelBindingGraph.bindings());
      return topLevelBindingGraph;
    }

    private ImmutableMap<ComponentPath, ComponentNode> componentNodes;
    private ImmutableSetMultimap<ComponentNode, ComponentNode> subcomponentNodes;
    private ImmutableSet<Binding> frameworkTypeBindings;

    TopLevelBindingGraph() {}

    // This overrides dagger.internal.codegen.model.BindingGraph with a more efficient
    // implementation.
    @Override
    public Optional<ComponentNode> componentNode(ComponentPath componentPath) {
      return componentNodes.containsKey(componentPath)
          ? Optional.of(componentNodes.get(componentPath))
          : Optional.empty();
    }

    /** Returns the set of subcomponent nodes of the given component node. */
    ImmutableSet<ComponentNode> subcomponentNodes(ComponentNode componentNode) {
      return subcomponentNodes.get(componentNode);
    }

    @Override
    @Memoized
    public ImmutableSetMultimap<Class<? extends Node>, ? extends Node> nodesByClass() {
      return super.nodesByClass();
    }

    /**
     * Returns an index of each {@link BindingNode} by its {@link ComponentPath}. Accessing this for
     * a component and its parent components is faster than doing a graph traversal.
     */
    @Memoized
    ImmutableListMultimap<ComponentPath, BindingNode> bindingsByComponent() {
      return Multimaps.index(transform(bindings(), BindingNode.class::cast), Node::componentPath);
    }

    /** Returns a {@link Comparator} in the same order as {@link Network#nodes()}. */
    @Memoized
    Comparator<Node> nodeOrder() {
      Map<Node, Integer> nodeOrderMap = Maps.newHashMapWithExpectedSize(network().nodes().size());
      int i = 0;
      for (Node node : network().nodes()) {
        nodeOrderMap.put(node, i++);
      }
      return (n1, n2) -> nodeOrderMap.get(n1).compareTo(nodeOrderMap.get(n2));
    }

    /** Returns the set of strongly connected nodes in this graph in reverse topological order. */
    @Memoized
    public ImmutableList<ImmutableSet<Node>> stronglyConnectedNodes() {
      return TarjanSCCs.<Node>compute(
          ImmutableSet.copyOf(network().nodes()),
          // NetworkBuilder does not have a stable successor order, so we have to roll our own
          // based on the node order, which is stable.
          // TODO(bcorso): Fix once https://github.com/google/guava/issues/2650 is fixed.
          node ->
              network().successors(node).stream().sorted(nodeOrder()).collect(toImmutableList()));
    }

    public boolean hasFrameworkRequest(Binding binding) {
      return frameworkTypeBindings.contains(binding);
    }

    private static ImmutableSet<Binding> frameworkRequestBindingSet(
        ImmutableNetwork<Node, Edge> network,
        ImmutableSet<dagger.internal.codegen.model.Binding> bindings) {
      Set<Binding> frameworkRequestBindings = new HashSet<>();
      for (dagger.internal.codegen.model.Binding binding : bindings) {
        ImmutableList<DependencyEdge> edges =
            network.inEdges(binding).stream()
                .flatMap(instancesOf(DependencyEdge.class))
                .collect(toImmutableList());
        for (DependencyEdge edge : edges) {
          DependencyRequest request = edge.dependencyRequest();
          switch (request.kind()) {
            case INSTANCE:
            case FUTURE:
              continue;
            case PRODUCED:
            case PRODUCER:
            case MEMBERS_INJECTION:
            case PROVIDER_OF_LAZY:
            case LAZY:
            case PROVIDER:
              frameworkRequestBindings.add(((BindingNode) binding).delegate());
              break;
          }
        }
      }
      return ImmutableSet.copyOf(frameworkRequestBindings);
    }
  }

  static BindingGraph create(
        ImmutableNetwork<Node, Edge> network,
        boolean isFullBindingGraph) {
    TopLevelBindingGraph topLevelBindingGraph =
        TopLevelBindingGraph.create(
            network,
            isFullBindingGraph);
    return create(Optional.empty(), topLevelBindingGraph.rootComponentNode(), topLevelBindingGraph);
  }

  private static BindingGraph create(
      Optional<BindingGraph> parent,
      ComponentNode componentNode,
      TopLevelBindingGraph topLevelBindingGraph) {
    // TODO(bcorso): Mapping binding nodes by key is flawed since bindings that depend on local
    // multibindings can have multiple nodes (one in each component). In this case, we choose the
    // node in the child-most component since this is likely the node that users of this
    // BindingGraph will want (and to remain consistent with LegacyBindingGraph). However, ideally
    // we would avoid this ambiguity by getting dependencies directly from the top-level network.
    // In particular, rather than using a Binding's list of DependencyRequests (which only
    // contains the key) we would use the top-level network to find the DependencyEdges for a
    // particular BindingNode.
    Map<Key, BindingNode> contributionBindings = new LinkedHashMap<>();
    Map<Key, BindingNode> membersInjectionBindings = new LinkedHashMap<>();
    topLevelBindingGraph.bindingsByComponent().get(componentNode.componentPath())
        .forEach(
            bindingNode -> {
              if (bindingNode.delegate() instanceof ContributionBinding) {
                contributionBindings.putIfAbsent(bindingNode.key(), bindingNode);
              } else if (bindingNode.delegate() instanceof MembersInjectionBinding) {
                membersInjectionBindings.putIfAbsent(bindingNode.key(), bindingNode);
              } else {
                throw new AssertionError("Unexpected binding node type: " + bindingNode.delegate());
              }
            });

    BindingGraph bindingGraph = new AutoValue_BindingGraph(componentNode, topLevelBindingGraph);

    ImmutableSet<XTypeElement> modules =
        ((ComponentNodeImpl) componentNode).componentDescriptor().modules().stream()
            .map(ModuleDescriptor::moduleElement)
            .collect(toImmutableSet());

    ImmutableSet<XTypeElement> inheritedModules =
        parent.isPresent()
            ? Sets.union(parent.get().ownedModules, parent.get().inheritedModules).immutableCopy()
            : ImmutableSet.of();

    // Set these fields directly on the instance rather than passing these in as input to the
    // AutoValue to prevent exposing this data outside of the class.
    bindingGraph.parent = parent;
    bindingGraph.inheritedModules = inheritedModules;
    bindingGraph.ownedModules = Sets.difference(modules, inheritedModules).immutableCopy();
    bindingGraph.contributionBindings = ImmutableMap.copyOf(contributionBindings);
    bindingGraph.membersInjectionBindings = ImmutableMap.copyOf(membersInjectionBindings);
    bindingGraph.bindingModules =
        contributionBindings.values().stream()
            .map(BindingNode::contributingModule)
            .flatMap(presentValues())
            .map(DaggerTypeElement::xprocessing)
            .collect(toImmutableSet());

    return bindingGraph;
  }

  private Optional<BindingGraph> parent;
  private ImmutableMap<Key, BindingNode> contributionBindings;
  private ImmutableMap<Key, BindingNode> membersInjectionBindings;
  private ImmutableSet<XTypeElement> inheritedModules;
  private ImmutableSet<XTypeElement> ownedModules;
  private ImmutableSet<XTypeElement> bindingModules;

  BindingGraph() {}

  /** Returns the {@link ComponentNode} for this graph. */
  public abstract ComponentNode componentNode();

  /** Returns the {@link ComponentPath} for this graph. */
  public final ComponentPath componentPath() {
    return componentNode().componentPath();
  }

  /** Returns the {@link TopLevelBindingGraph} from which this graph is contained. */
  public abstract TopLevelBindingGraph topLevelBindingGraph();

  /** Returns the {@link ComponentDescriptor} for this graph */
  public final ComponentDescriptor componentDescriptor() {
    return ((ComponentNodeImpl) componentNode()).componentDescriptor();
  }

  /** Returns all entry point methods for this component. */
  @Memoized
  public ImmutableSet<ComponentMethodDescriptor> entryPointMethods() {
    return componentDescriptor().entryPointMethods().stream()
        .collect(toImmutableSet());
  }

  public Optional<ComponentMethodDescriptor> findFirstMatchingComponentMethod(
      BindingRequest request) {
    return Optional.ofNullable(firstMatchingComponentMethods().get(request));
  }

  @Memoized
  ImmutableMap<BindingRequest, ComponentMethodDescriptor> firstMatchingComponentMethods() {
    Map<BindingRequest, ComponentMethodDescriptor> componentMethodDescriptorsByRequest =
        new HashMap<>();
    for (ComponentMethodDescriptor method : entryPointMethods()) {
      componentMethodDescriptorsByRequest.putIfAbsent(
          bindingRequest(method.dependencyRequest().get()), method);
    }
    return ImmutableMap.copyOf(componentMethodDescriptorsByRequest);
  }

  /**
   * Returns the {@link ContributionBinding} for the given {@link Key} in this component or {@link
   * Optional#empty()} if one doesn't exist.
   */
  public final Optional<Binding> localContributionBinding(Key key) {
    return contributionBindings.containsKey(key)
        ? Optional.of(contributionBindings.get(key).delegate())
        : Optional.empty();
  }

  /**
   * Returns the {@link MembersInjectionBinding} for the given {@link Key} in this component or
   * {@link Optional#empty()} if one doesn't exist.
   */
  public final Optional<Binding> localMembersInjectionBinding(Key key) {
    return membersInjectionBindings.containsKey(key)
        ? Optional.of(membersInjectionBindings.get(key).delegate())
        : Optional.empty();
  }

  /** Returns the {@link ContributionBinding} for the given {@link Key}. */
  public final ContributionBinding contributionBinding(Key key) {
    if (contributionBindings.containsKey(key)) {
      return (ContributionBinding) contributionBindings.get(key).delegate();
    } else if (parent.isPresent()) {
      return parent.get().contributionBinding(key);
    }
    throw new AssertionError("Contribution binding not found for key: " + key);
  }

  /**
   * Returns the {@link MembersInjectionBinding} for the given {@link Key} or {@link
   * Optional#empty()} if one does not exist.
   */
  public final Optional<MembersInjectionBinding> membersInjectionBinding(Key key) {
    if (membersInjectionBindings.containsKey(key)) {
      return Optional.of((MembersInjectionBinding) membersInjectionBindings.get(key).delegate());
    } else if (parent.isPresent()) {
      return parent.get().membersInjectionBinding(key);
    }
    return Optional.empty();
  }

  /** Returns the {@link XTypeElement} for the component this graph represents. */
  public final XTypeElement componentTypeElement() {
    return componentPath().currentComponent().xprocessing();
  }

  /**
   * Returns the set of modules that are owned by this graph regardless of whether or not any of
   * their bindings are used in this graph. For graphs representing top-level {@link
   * dagger.Component components}, this set will be the same as {@linkplain
   * ComponentDescriptor#modules() the component's transitive modules}. For {@linkplain Subcomponent
   * subcomponents}, this set will be the transitive modules that are not owned by any of their
   * ancestors.
   */
  public final ImmutableSet<XTypeElement> ownedModuleTypes() {
    return ownedModules;
  }

  /**
   * Returns the factory method for this subcomponent, if it exists.
   *
   * <p>This factory method is the one defined in the parent component's interface.
   *
   * <p>In the example below, the {@link BindingGraph#factoryMethod} for {@code ChildComponent}
   * would return the {@link XExecutableElement}: {@code childComponent(ChildModule1)} .
   *
   * <pre><code>
   *   {@literal @Component}
   *   interface ParentComponent {
   *     ChildComponent childComponent(ChildModule1 childModule);
   *   }
   * </code></pre>
   */
  // TODO(b/73294201): Consider returning the resolved ExecutableType for the factory method.
  public final Optional<XMethodElement> factoryMethod() {
    return topLevelBindingGraph().network().inEdges(componentNode()).stream()
        .filter(edge -> edge instanceof ChildFactoryMethodEdge)
        .map(edge -> ((ChildFactoryMethodEdge) edge).factoryMethod().xprocessing())
        // Factory methods are represented by XMethodElement (rather than XConstructorElement)
        // TODO(bcorso): consider adding DaggerMethodElement so this cast isn't needed.
        .map(XMethodElement.class::cast)
        .collect(toOptional());
  }

  /**
   * Returns a map between the {@linkplain ComponentRequirement component requirement} and the
   * corresponding {@link XExecutableParameterElement} for each module parameter in the {@linkplain
   * BindingGraph#factoryMethod factory method}.
   */
  // TODO(dpb): Consider disallowing modules if none of their bindings are used.
  public final ImmutableMap<ComponentRequirement, XExecutableParameterElement>
      factoryMethodParameters() {
    return factoryMethod().get().getParameters().stream()
        .collect(
            toImmutableMap(
                parameter -> ComponentRequirement.forModule(parameter.getType()),
                parameter -> parameter));
  }

  /**
   * The types for which the component needs instances.
   *
   * <ul>
   *   <li>component dependencies
   *   <li>owned modules with concrete instance bindings that are used in the graph
   *   <li>bound instances
   * </ul>
   */
  @Memoized
  public ImmutableSet<ComponentRequirement> componentRequirements() {
    ImmutableSet<XTypeElement> requiredModules =
        stream(Traverser.forTree(BindingGraph::subgraphs).depthFirstPostOrder(this))
            .flatMap(graph -> graph.bindingModules.stream())
            .filter(ownedModules::contains)
            .collect(toImmutableSet());
    ImmutableSet.Builder<ComponentRequirement> requirements = ImmutableSet.builder();
    componentDescriptor().requirements().stream()
        .filter(
            requirement ->
                !requirement.kind().isModule()
                    || requiredModules.contains(requirement.typeElement()))
        .forEach(requirements::add);
    if (factoryMethod().isPresent()) {
      requirements.addAll(factoryMethodParameters().keySet());
    }
    return requirements.build();
  }

  /**
   * Returns all {@link ComponentDescriptor}s in the {@link TopLevelBindingGraph} mapped by the
   * component path.
   */
  @Memoized
  public ImmutableMap<ComponentPath, ComponentDescriptor> componentDescriptorsByPath() {
    return topLevelBindingGraph().componentNodes().stream()
        .map(ComponentNodeImpl.class::cast)
        .collect(
            toImmutableMap(ComponentNode::componentPath, ComponentNodeImpl::componentDescriptor));
  }

  @Memoized
  public ImmutableList<BindingGraph> subgraphs() {
    return topLevelBindingGraph().subcomponentNodes(componentNode()).stream()
        .map(subcomponent -> create(Optional.of(this), subcomponent, topLevelBindingGraph()))
        .collect(toImmutableList());
  }

  /** Returns the list of all {@link BindingNode}s local to this component. */
  public ImmutableList<BindingNode> localBindingNodes() {
    return topLevelBindingGraph().bindingsByComponent().get(componentPath());
  }

  // TODO(bcorso): This method can be costly. Consider removing this method and inlining it into its
  // only usage, BindingGraphJsonGenerator.
  public ImmutableSet<BindingNode> bindingNodes() {
    // Construct the set of bindings by iterating bindings from this component and then from each
    // successive parent. If a binding exists in multiple components, this order ensures that the
    // child-most binding is always chosen first.
    Map<Key, BindingNode> bindings = new LinkedHashMap<>();
    Stream.iterate(componentPath(), ComponentPath::parent)
        // Stream.iterate() is infinite stream so we need limit it to the known size of the path.
        .limit(componentPath().components().size())
        .flatMap(path -> topLevelBindingGraph().bindingsByComponent().get(path).stream())
        .forEach(bindingNode -> bindings.putIfAbsent(bindingNode.key(), bindingNode));
    return ImmutableSet.copyOf(bindings.values());
  }
}

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
import static dagger.internal.codegen.extension.DaggerCollectors.toOptional;
import static dagger.internal.codegen.extension.DaggerStreams.presentValues;
import static dagger.internal.codegen.extension.DaggerStreams.stream;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableMap;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.google.common.graph.ImmutableNetwork;
import com.google.common.graph.Traverser;
import dagger.model.BindingGraph.ChildFactoryMethodEdge;
import dagger.model.BindingGraph.ComponentNode;
import dagger.model.BindingGraph.Edge;
import dagger.model.BindingGraph.Node;
import dagger.model.ComponentPath;
import dagger.model.Key;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

/**
 * A graph that represents a single component or subcomponent within a fully validated top-level
 * binding graph.
 */
@AutoValue
public abstract class BindingGraph {

  @AutoValue
  abstract static class TopLevelBindingGraph extends dagger.model.BindingGraph {
    static TopLevelBindingGraph create(
        ImmutableNetwork<Node, Edge> network, boolean isFullBindingGraph) {
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
      return topLevelBindingGraph;
    }

    private ImmutableMap<ComponentPath, ComponentNode> componentNodes;
    private ImmutableSetMultimap<ComponentNode, ComponentNode> subcomponentNodes;

    TopLevelBindingGraph() {}

    // This overrides dagger.model.BindingGraph with a more efficient implementation.
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
  }

  static BindingGraph create(
      ComponentNode componentNode, TopLevelBindingGraph topLevelBindingGraph) {
    return create(Optional.empty(), componentNode, topLevelBindingGraph);
  }

  private static BindingGraph create(
      Optional<BindingGraph> parent,
      ComponentNode componentNode,
      TopLevelBindingGraph topLevelBindingGraph) {
    List<BindingNode> reachableBindingNodes = new ArrayList<>();
    for (ComponentPath path = componentNode.componentPath();
        !path.components().isEmpty();
        path = ComponentPath.create(path.components().subList(0, path.components().size() - 1))) {
      reachableBindingNodes.addAll(topLevelBindingGraph.bindingsByComponent().get(path));
    }

    // Construct the maps of the ContributionBindings and MembersInjectionBindings.
    Map<Key, BindingNode> contributionBindings = new HashMap<>();
    Map<Key, BindingNode> membersInjectionBindings = new HashMap<>();
    for (BindingNode bindingNode : reachableBindingNodes) {
      Map<Key, BindingNode> bindingsMap;
      if (bindingNode.delegate() instanceof ContributionBinding) {
        bindingsMap = contributionBindings;
      } else if (bindingNode.delegate() instanceof MembersInjectionBinding) {
        bindingsMap = membersInjectionBindings;
      } else {
        throw new AssertionError("Unexpected binding node type: " + bindingNode.delegate());
      }

      // TODO(bcorso): Mapping binding nodes by key is flawed since bindings that depend on local
      // multibindings can have multiple nodes (one in each component). In this case, we choose the
      // node in the child-most component since this is likely the node that users of this
      // BindingGraph will want (and to remain consisted with LegacyBindingGraph). However, ideally
      // we would avoid this ambiguity by getting dependencies directly from the top-level network.
      // In particular, rather than using a Binding's list of DependencyRequests (which only
      // contains the key) we would use the top-level network to find the DependencyEdges for a
      // particular BindingNode.
      Key key = bindingNode.key();
      if (!bindingsMap.containsKey(key)
          // Always choose the child-most binding node.
          || bindingNode.componentPath().components().size()
              > bindingsMap.get(key).componentPath().components().size()) {
        bindingsMap.put(key, bindingNode);
      }
    }

    BindingGraph bindingGraph = new AutoValue_BindingGraph(componentNode, topLevelBindingGraph);

    ImmutableSet<ModuleDescriptor> modules =
        ((ComponentNodeImpl) componentNode).componentDescriptor().modules();

    ImmutableSet<ModuleDescriptor> inheritedModules =
        parent.isPresent()
            ? Sets.union(parent.get().ownedModules, parent.get().inheritedModules).immutableCopy()
            : ImmutableSet.of();

    // Set these fields directly on the instance rather than passing these in as input to the
    // AutoValue to prevent exposing this data outside of the class.
    bindingGraph.inheritedModules = inheritedModules;
    bindingGraph.ownedModules = Sets.difference(modules, inheritedModules).immutableCopy();
    bindingGraph.contributionBindings = ImmutableMap.copyOf(contributionBindings);
    bindingGraph.membersInjectionBindings = ImmutableMap.copyOf(membersInjectionBindings);
    bindingGraph.bindingModules =
        contributionBindings.values().stream()
            .map(BindingNode::contributingModule)
            .flatMap(presentValues())
            .collect(toImmutableSet());

    return bindingGraph;
  }

  private ImmutableMap<Key, BindingNode> contributionBindings;
  private ImmutableMap<Key, BindingNode> membersInjectionBindings;
  private ImmutableSet<ModuleDescriptor> inheritedModules;
  private ImmutableSet<ModuleDescriptor> ownedModules;
  private ImmutableSet<TypeElement> bindingModules;

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

  /** Returns the {@link ContributionBinding} for the given {@link Key}. */
  public final ContributionBinding contributionBinding(Key key) {
    return (ContributionBinding) contributionBindings.get(key).delegate();
  }

  /**
   * Returns the {@link MembersInjectionBinding} for the given {@link Key} or {@link
   * Optional#empty()} if one does not exist.
   */
  public final Optional<MembersInjectionBinding> membersInjectionBinding(Key key) {
    return membersInjectionBindings.containsKey(key)
        ? Optional.of((MembersInjectionBinding) membersInjectionBindings.get(key).delegate())
        : Optional.empty();
  }

  /** Returns the {@link TypeElement} for the component this graph represents. */
  public final TypeElement componentTypeElement() {
    return componentPath().currentComponent();
  }

  /**
   * Returns the set of modules that are owned by this graph regardless of whether or not any of
   * their bindings are used in this graph. For graphs representing top-level {@link
   * dagger.Component components}, this set will be the same as {@linkplain
   * ComponentDescriptor#modules() the component's transitive modules}. For {@linkplain Subcomponent
   * subcomponents}, this set will be the transitive modules that are not owned by any of their
   * ancestors.
   */
  public final ImmutableSet<TypeElement> ownedModuleTypes() {
    return ownedModules.stream().map(ModuleDescriptor::moduleElement).collect(toImmutableSet());
  }

  /**
   * Returns the factory method for this subcomponent, if it exists.
   *
   * <p>This factory method is the one defined in the parent component's interface.
   *
   * <p>In the example below, the {@link BindingGraph#factoryMethod} for {@code ChildComponent}
   * would return the {@link ExecutableElement}: {@code childComponent(ChildModule1)} .
   *
   * <pre><code>
   *   {@literal @Component}
   *   interface ParentComponent {
   *     ChildComponent childComponent(ChildModule1 childModule);
   *   }
   * </code></pre>
   */
  // TODO(b/73294201): Consider returning the resolved ExecutableType for the factory method.
  public final Optional<ExecutableElement> factoryMethod() {
    return topLevelBindingGraph().network().inEdges(componentNode()).stream()
        .filter(edge -> edge instanceof ChildFactoryMethodEdge)
        .map(edge -> ((ChildFactoryMethodEdge) edge).factoryMethod())
        .collect(toOptional());
  }

  /**
   * Returns a map between the {@linkplain ComponentRequirement component requirement} and the
   * corresponding {@link VariableElement} for each module parameter in the {@linkplain
   * BindingGraph#factoryMethod factory method}.
   */
  // TODO(dpb): Consider disallowing modules if none of their bindings are used.
  public final ImmutableMap<ComponentRequirement, VariableElement> factoryMethodParameters() {
    return factoryMethod().get().getParameters().stream()
        .collect(
            toImmutableMap(
                parameter -> ComponentRequirement.forModule(parameter.asType()),
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
    ImmutableSet<TypeElement> requiredModules =
        stream(Traverser.forTree(BindingGraph::subgraphs).depthFirstPostOrder(this))
            .flatMap(graph -> graph.bindingModules.stream())
            .filter(ownedModuleTypes()::contains)
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

  /** Returns all {@link ComponentDescriptor}s in the {@link TopLevelBindingGraph}. */
  public final ImmutableSet<ComponentDescriptor> componentDescriptors() {
    return topLevelBindingGraph().componentNodes().stream()
        .map(componentNode -> ((ComponentNodeImpl) componentNode).componentDescriptor())
        .collect(toImmutableSet());
  }

  @Memoized
  public ImmutableList<BindingGraph> subgraphs() {
    return topLevelBindingGraph().subcomponentNodes(componentNode()).stream()
        .map(subcomponent -> create(Optional.of(this), subcomponent, topLevelBindingGraph()))
        .collect(toImmutableList());
  }

  public final ImmutableSet<BindingNode> bindingNodes(Key key) {
    ImmutableSet.Builder<BindingNode> builder = ImmutableSet.builder();
    if (contributionBindings.containsKey(key)) {
      builder.add(contributionBindings.get(key));
    }
    if (membersInjectionBindings.containsKey(key)) {
      builder.add(membersInjectionBindings.get(key));
    }
    return builder.build();
  }

  @Memoized
  public ImmutableSet<BindingNode> bindingNodes() {
    return ImmutableSet.<BindingNode>builder()
        .addAll(contributionBindings.values())
        .addAll(membersInjectionBindings.values())
        .build();
  }
}

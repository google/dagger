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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Predicates.equalTo;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.collect.Iterables.indexOf;
import static com.google.common.collect.Iterables.skip;
import static com.google.common.collect.Multimaps.asMap;
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;
import static java.util.Spliterator.ORDERED;
import static java.util.Spliterator.SIZED;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.LinkedHashMultiset;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodKind;
import dagger.model.ComponentPath;
import dagger.model.DependencyRequest;
import dagger.model.Key;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Spliterators.AbstractSpliterator;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/**
 * An object that traverses the entire component hierarchy, starting from the root component.
 *
 * <p>Subclasses can override {@link #visitComponent(BindingGraph)} to perform custom logic at each
 * component in the tree, and {@link #visitSubcomponentFactoryMethod(BindingGraph, BindingGraph,
 * ExecutableElement)} to perform custom logic at each subcomponent factory method.
 *
 * <p>Subclasses can override {@link #bindingGraphTraverser(ComponentTreePath,
 * ComponentMethodDescriptor)} to traverse each entry point within each component in the tree.
 */
public class ComponentTreeTraverser {

  /** The path from the root graph to the currently visited graph. */
  private final Deque<BindingGraph> bindingGraphPath = new ArrayDeque<>();

  /** Constructs a traverser for a root (component, not subcomponent) binding graph. */
  public ComponentTreeTraverser(BindingGraph rootGraph, CompilerOptions compilerOptions) {
    checkArgument(
        rootGraph.componentDescriptor().kind().isTopLevel()
            || compilerOptions.aheadOfTimeSubcomponents(),
        "only top-level graphs can be traversed, not %s",
        rootGraph.componentDescriptor().componentDefinitionType().getQualifiedName());
    bindingGraphPath.add(rootGraph);
  }

  /**
   * Calls {@link #visitComponent(BindingGraph)} for the root component.
   *
   * @throws IllegalStateException if a traversal is in progress
   */
  public final void traverseComponents() {
    checkState(bindingGraphPath.size() == 1);
    visitComponent(bindingGraphPath.getFirst());
  }

  /**
   * Called once for each component in a component hierarchy.
   *
   * <p>Subclasses can override this method to perform whatever logic is required per component.
   * They should call the {@code super} implementation if they want to continue the traversal in the
   * standard order.
   *
   * <p>This implementation does the following:
   *
   * <ol>
   *   <li>If this component is installed in its parent by a subcomponent factory method, calls
   *       {@link #visitSubcomponentFactoryMethod(BindingGraph, BindingGraph, ExecutableElement)}.
   *   <li>For each entry point in the component, calls {@link
   *       #visitEntryPoint(ComponentMethodDescriptor, BindingGraph)}.
   *   <li>For each child component, calls {@link #visitComponent(BindingGraph)}, updating the
   *       traversal state.
   * </ol>
   *
   * @param graph the currently visited graph
   */
  protected void visitComponent(BindingGraph graph) {
    if (bindingGraphPath.size() > 1) {
      BindingGraph parent = Iterators.get(bindingGraphPath.descendingIterator(), 1);
      ComponentMethodDescriptor childFactoryMethod =
          parent
              .componentDescriptor()
              .subcomponentsByFactoryMethod()
              .inverse()
              .get(graph.componentDescriptor());
      if (childFactoryMethod != null) {
        visitSubcomponentFactoryMethod(graph, parent, childFactoryMethod.methodElement());
      }
    }

    for (ComponentMethodDescriptor entryPointMethod :
        graph.componentDescriptor().entryPointMethods()) {
      visitEntryPoint(entryPointMethod, graph);
    }

    for (BindingGraph child : graph.subgraphs()) {
      bindingGraphPath.addLast(child);
      try {
        visitComponent(child);
      } finally {
        verify(bindingGraphPath.removeLast().equals(child));
      }
    }
  }

  /**
   * Called if this component was installed in its parent by a subcomponent factory method.
   *
   * <p>This implementation does nothing.
   *
   * @param graph the currently visited graph
   * @param parent the parent graph
   * @param factoryMethod the factory method in the parent component that declares that the current
   *     component is a child
   */
  protected void visitSubcomponentFactoryMethod(
      BindingGraph graph, BindingGraph parent, ExecutableElement factoryMethod) {}

  /**
   * Called once for each entry point in a component.
   *
   * <p>Subclasses can override this method to perform whatever logic is required per entry point.
   * They should call the {@code super} implementation if they want to continue the traversal in the
   * standard order.
   *
   * <p>This implementation passes the entry point and the current component tree path to {@link
   * #bindingGraphTraverser(ComponentTreePath, ComponentMethodDescriptor)}, and calls {@link
   * BindingGraphTraverser#traverseDependencies()} on the returned object.
   *
   * @param graph the graph for the component that contains the entry point
   */
  protected void visitEntryPoint(ComponentMethodDescriptor entryPointMethod, BindingGraph graph) {
    bindingGraphTraverser(componentTreePath(), entryPointMethod).traverseDependencies();
  }

  /**
   * Returns an object that traverses the binding graph starting from an entry point.
   *
   * <p>This implementation returns a no-op object that does nothing. Subclasses should override in
   * order to perform custom logic within the binding graph.
   *
   * @param componentPath the path from the root component to the component that includes the entry
   *     point
   * @param entryPointMethod the entry point method
   */
  protected BindingGraphTraverser bindingGraphTraverser(
      ComponentTreePath componentPath, ComponentMethodDescriptor entryPointMethod) {
    return new NoOpBindingGraphTraverser(componentPath, entryPointMethod);
  }

  /**
   * Returns an immutable snapshot of the path from the root component to the currently visited
   * component.
   */
  protected final ComponentTreePath componentTreePath() {
    return ComponentTreePath.create(bindingGraphPath);
  }

  /**
   * An object that traverses the binding graph of a component, starting from an entry point.
   *
   * <p>Note that the traversal includes bindings that are owned by an ancestor component, which may
   * already have been traversed when traversing that ancestor's binding graph.
   */
  public static class BindingGraphTraverser {

    private final ComponentTreePath componentTreePath;
    private final ComponentMethodDescriptor entryPointMethod;
    private final Deque<DependencyRequest> dependencyRequestPath = new ArrayDeque<>();
    private final Deque<ResolvedBindings> resolvedBindingsPath = new ArrayDeque<>();
    private final LinkedHashMultiset<Key> keysInPath = LinkedHashMultiset.create();
    private final Set<DependencyRequest> visitedDependencyRequests = new HashSet<>();

    /**
     * Constructs a traverser for an entry point.
     *
     * @param componentPath the path from the root component to the component that includes the
     *     entry point to be traversed
     * @param entryPointMethod the entry point method to be traversed
     */
    public BindingGraphTraverser(
        ComponentTreePath componentPath, ComponentMethodDescriptor entryPointMethod) {
      this.componentTreePath = componentPath;
      this.entryPointMethod = entryPointMethod;
    }

    /**
     * Calls {@link #visitDependencyRequest(DependencyRequest)} for the {@linkplain
     * #entryPointElement() entry point}.
     *
     * @throws IllegalStateException if a traversal is in progress
     */
    public void traverseDependencies() {
      checkState(dependencyRequestPath.isEmpty());
      checkState(resolvedBindingsPath.isEmpty());
      checkState(keysInPath.isEmpty());
      checkState(visitedDependencyRequests.isEmpty());
      nextDependencyRequest(entryPointMethod.dependencyRequest().get(), currentGraph());
    }

    /**
     * Called once for each dependency request that is reachable from an entry point.
     *
     * <p>Subclasses can override this method to perform whatever logic is required per dependency
     * request. They should call the {@code super} implementation if they want to continue the
     * traversal in the standard order.
     *
     * <p>This implementation calls {@link #visitResolvedBindings(ResolvedBindings)}.
     *
     * @param dependencyRequest the object returned by {@link #dependencyRequest()}
     */
    protected void visitDependencyRequest(DependencyRequest dependencyRequest) {
      if (visitedDependencyRequests.add(dependencyRequest)) {
        visitResolvedBindings(resolvedBindingsPath.getLast());
      }
    }

    /**
     * Called once for each dependency request that is reachable from an entry point.
     *
     * <p>Subclasses can override this method to perform whatever logic is required per resolved
     * bindings. They should call the {@code super} implementation if they want to continue the
     * traversal in the standard order.
     *
     * <p>This implementation calls either {@link #visitMembersInjectionBindings(ResolvedBindings)}
     * or {@link #visitContributionBindings(ResolvedBindings)}, depending on the binding key kind.
     *
     * @param resolvedBindings the object returned by {@link #resolvedBindings()}
     */
    protected void visitResolvedBindings(ResolvedBindings resolvedBindings) {
      if (resolvedBindings.membersInjectionBinding().isPresent()) {
        visitMembersInjectionBindings(resolvedBindings);
      }

      if (!resolvedBindings.contributionBindings().isEmpty()) {
        visitContributionBindings(resolvedBindings);
      }
    }

    /**
     * Called once for each dependency request for a members injector that is reachable from an
     * entry point.
     *
     * <p>Subclasses can override this method to perform whatever logic is required per resolved
     * members injection bindings. They should call the {@code super} implementation if they want to
     * continue the traversal in the standard order.
     *
     * <p>This implementation calls {@link #visitMembersInjectionBinding(MembersInjectionBinding,
     * ComponentDescriptor)}.
     *
     * @param resolvedBindings the object returned by {@link #resolvedBindings()}
     */
    protected void visitMembersInjectionBindings(ResolvedBindings resolvedBindings) {
      if (!resolvedBindings.contributionBindings().isEmpty()) {
        // TODO(dpb): How could this ever happen, even in an invalid graph?
        throw new AssertionError(
            "members injection binding keys should never have contribution bindings");
      }
      if (resolvedBindings.membersInjectionBinding().isPresent()) {
        visitMembersInjectionBinding(
            resolvedBindings.membersInjectionBinding().get(),
            getOnlyElement(resolvedBindings.allMembersInjectionBindings().keySet()));
      }
    }

    /**
     * Called once for each members injection binding that is reachable from an entry point.
     *
     * <p>Subclasses can override this method to perform whatever logic is required per members
     * injection binding. They should call the {@code super} implementation if they want to continue
     * the traversal in the standard order.
     *
     * <p>This implementation calls {@link #visitBinding(Binding, ComponentDescriptor)}.
     *
     * @param binding the only value of {@code resolvedBindings().allMembersInjectionBindings()}
     * @param owningComponent the only key of {@code
     *     resolvedBindings().allMembersInjectionBindings()}. The binding's dependencies should be
     *     resolved within this component.
     */
    protected void visitMembersInjectionBinding(
        MembersInjectionBinding binding, ComponentDescriptor owningComponent) {
      visitBinding(binding, owningComponent);
    }

    /**
     * Called once for each dependency request for a contribution that is reachable from an entry
     * point.
     *
     * <p>Subclasses can override this method to perform whatever logic is required per resolved
     * contribution bindings. They should call the {@code super} implementation if they want to
     * continue the traversal in the standard order.
     *
     * <p>This implementation calls {@link #visitContributionBinding(ContributionBinding,
     * ComponentDescriptor)} for each contribution binding.
     *
     * @param resolvedBindings the object returned by {@link #resolvedBindings()}
     */
    protected void visitContributionBindings(ResolvedBindings resolvedBindings) {
      if (resolvedBindings.membersInjectionBinding().isPresent()) {
        throw new AssertionError(
            "contribution binding keys should never have members injection bindings");
      }
      asMap(resolvedBindings.allContributionBindings())
          .forEach(
              (owningComponent, bindings) -> {
                bindings.forEach(binding -> visitContributionBinding(binding, owningComponent));
              });
    }

    /**
     * Called once for each contribution binding that is reachable from an entry point.
     *
     * <p>Subclasses can override this method to perform whatever logic is required per contribution
     * binding. They should call the {@code super} implementation if they want to continue the
     * traversal in the standard order.
     *
     * <p>This implementation calls {@link #visitBinding(Binding, ComponentDescriptor)}.
     *
     * @param binding a value of {@code resolvedBindings().allContributionBindings()}
     * @param owningComponent the key of {@code resolvedBindings().allContributionBindings()} for
     *     {@code binding}. The binding's dependencies should be resolved within this component.
     */
    protected void visitContributionBinding(
        ContributionBinding binding, ComponentDescriptor owningComponent) {
      visitBinding(binding, owningComponent);
    }

    /**
     * Called once for each binding that is reachable from an entry point.
     *
     * <p>Subclasses can override this method to perform whatever logic is required per binding.
     * They should call the {@code super} implementation if they want to continue the traversal in
     * the standard order.
     *
     * <p>This implementation calls {@link #visitDependencyRequest(DependencyRequest)} for each
     * dependency of the binding, resolved within {@code owningComponent}, that has not already been
     * visited while traversing the current entry point — unless the dependency request introduces a
     * cycle.
     *
     * @param binding a value of {@code resolvedBindings().allBindings()}
     * @param owningComponent the key of {@code resolvedBindings().allBindings()} for {@code
     *     binding}. The binding's dependencies should be resolved within this component.
     */
    protected void visitBinding(Binding binding, ComponentDescriptor owningComponent) {
      if (atDependencyCycle()) {
        return;
      }
      BindingGraph owningGraph = componentTreePath.graphForComponent(owningComponent);
      for (DependencyRequest dependency : binding.dependencies()) {
        nextDependencyRequest(dependency, owningGraph);
      }
    }

    private void nextDependencyRequest(
        DependencyRequest dependencyRequest, BindingGraph bindingGraph) {
      ResolvedBindings resolvedBindings =
          bindingGraph.resolvedBindings(dependencyRequest.kind(), dependencyRequest.key());
      dependencyRequestPath.addLast(dependencyRequest);
      resolvedBindingsPath.addLast(resolvedBindings);
      // Don't add the key of a members injection request, as it doesn't participate in cycles
      if (!isComponentMembersInjectionRequest(dependencyRequest)) {
        keysInPath.add(dependencyRequest.key());
      }
      try {
        visitDependencyRequest(dependencyRequest);
      } finally {
        verify(dependencyRequestPath.removeLast().equals(dependencyRequest));
        verify(resolvedBindingsPath.removeLast().equals(resolvedBindings));
        if (!isComponentMembersInjectionRequest(dependencyRequest)) {
          verify(keysInPath.remove(dependencyRequest.key()));
        }
      }
    }

    /**
     * Returns {@code true} if {@code dependencyRequest} is the {@link #entryPointMethod}'s request
     * and the entry point is a members injection method.
     */
    private boolean isComponentMembersInjectionRequest(DependencyRequest dependencyRequest) {
      return entryPointMethod.kind().equals(ComponentMethodKind.MEMBERS_INJECTION)
          && entryPointMethod.dependencyRequest().get().equals(dependencyRequest);
    }

    /**
     * Returns the path from the root component to the component that includes the {@linkplain
     * #entryPointElement()} entry point.
     */
    protected final ComponentTreePath componentTreePath() {
      return componentTreePath;
    }

    /**
     * Returns the rootmost of the binding graphs in the component path that own each binding.
     *
     * <p>For arguments {@code [x, y]}, if binding {@code x} is owned by component {@code A} and
     * binding {@code y} is owned by component {@code B}, and {@code A} is an ancestor of {@code B},
     * then this method returns the binding graph for {@code A}.
     */
    public BindingGraph owningGraph(Iterable<ContributionBinding> bindings) {
      ImmutableSet.Builder<ComponentDescriptor> owningComponents = ImmutableSet.builder();
      for (ContributionBinding binding : bindings) {
        ResolvedBindings resolvedBindings =
            currentGraph().contributionBindings().get(binding.key());
        owningComponents.add(resolvedBindings.owningComponent(binding));
      }
      return componentTreePath.rootmostGraph(owningComponents.build());
    }

    /**
     * Returns {@code true} if the {@linkplain #dependencyRequest() current dependency request} is
     * also higher in the dependency path.
     *
     * @throws IllegalStateException if this object is not currently traversing dependencies
     */
    protected final boolean atDependencyCycle() {
      checkState(!dependencyRequestPath.isEmpty());
      return keysInPath.count(dependencyRequest().key()) > 1;
    }

    /**
     * Returns the dependency request currently being visited.
     *
     * @throws IllegalStateException if this object is not currently traversing dependencies
     */
    protected final DependencyRequest dependencyRequest() {
      return dependencyRequestPath.getLast();
    }

    /**
     * Returns the resolved bindings for the {@linkplain #dependencyRequest() current dependency
     * request}.
     *
     * @throws IllegalStateException if this object is not currently traversing dependencies
     */
    protected final ResolvedBindings resolvedBindings() {
      return resolvedBindingsPath.getLast();
    }

    /**
     * Returns the bindings that depend directly on the {@linkplain #dependencyRequest() current
     * dependency request}.
     */
    protected final ImmutableSet<? extends Binding> dependentBindings() {
      if (atEntryPoint()) {
        return ImmutableSet.of();
      }
      ResolvedBindings dependentResolvedBindings =
          Iterators.get(resolvedBindingsPath.descendingIterator(), 1);
      return dependentResolvedBindings
          .bindings()
          .stream()
          .filter(binding -> binding.dependencies().contains(dependencyRequest()))
          .collect(toImmutableSet());
    }

    /**
     * Returns the entry point whose dependencies are currently being traversed.
     *
     * @throws IllegalStateException if this object is not currently traversing dependencies
     */
    protected final Element entryPointElement() {
      return entryPointMethod.dependencyRequest().get().requestElement().get();
    }

    /**
     * Returns {@code true} if the {@linkplain #dependencyRequest() current dependency request} is
     * an entry point.
     */
    protected final boolean atEntryPoint() {
      return dependencyRequestPath.size() == 1;
    }

    /** Returns the binding graph for the component that is currently being visited. */
    protected final BindingGraph currentGraph() {
      return componentTreePath.currentGraph();
    }

    /**
     * Returns the dependency requests and resolved bindings starting with the entry point and
     * ending with the {@linkplain #dependencyRequest() current dependency request}.
     */
    protected final DependencyTrace dependencyTrace() {
      checkState(!dependencyRequestPath.isEmpty());
      return new DependencyTrace(dependencyRequestPath, resolvedBindingsPath);
    }

    /**
     * Returns the dependency requests and resolved bindings in the {@linkplain #atDependencyCycle()
     * dependency cycle}, starting with the request closest to the entry point and ending with the
     * {@linkplain #dependencyRequest() current dependency request}.
     *
     * <p>The first request and the last request in the trace will have the same {@linkplain
     * DependencyRequest#key()} key}.
     */
    protected final DependencyTrace cycleDependencyTrace() {
      checkState(atDependencyCycle(), "no cycle");
      int skip = indexOf(keysInPath, equalTo(dependencyRequest().key()));
      return new DependencyTrace(
          skip(dependencyRequestPath, skip), skip(resolvedBindingsPath, skip));
    }
  }

  /** A traverser that does nothing. */
  private static final class NoOpBindingGraphTraverser extends BindingGraphTraverser {
    private NoOpBindingGraphTraverser(
        ComponentTreePath componentPath, ComponentMethodDescriptor entryPointMethod) {
      super(componentPath, entryPointMethod);
    }

    @Override
    public void traverseDependencies() {}
  }

  /**
   * A path from the root component to a component within the component tree during a {@linkplain
   * ComponentTreeTraverser traversal}.
   */
  @AutoValue
  public abstract static class ComponentTreePath {

    private static ComponentTreePath create(Iterable<BindingGraph> path) {
      return new AutoValue_ComponentTreeTraverser_ComponentTreePath(ImmutableList.copyOf(path));
    }

    /**
     * Returns the binding graphs in the path, starting from the {@linkplain #rootGraph() root
     * graph} and ending with the {@linkplain #currentGraph() current graph}.
     */
    public abstract ImmutableList<BindingGraph> graphsInPath();

    /** Returns the binding graph for the component at the end of the path. */
    public BindingGraph currentGraph() {
      return Iterables.getLast(graphsInPath());
    }

    /** Returns the type of the component at the end of the path. */
    public TypeElement currentComponent() {
      return currentGraph().componentDescriptor().componentDefinitionType();
    }

    /**
     * Returns the binding graph for the parent of the {@linkplain #currentGraph() current
     * component}.
     *
     * @throws IllegalStateException if the current graph is the {@linkplain #atRoot() root graph}
     */
    public BindingGraph parentGraph() {
      checkState(!atRoot());
      return graphsInPath().reverse().get(1);
    }

    /** Returns the binding graph for the root component. */
    public BindingGraph rootGraph() {
      return graphsInPath().get(0);
    }

    /**
     * Returns {@code true} if the {@linkplain #currentGraph() current graph} is the {@linkplain
     * #rootGraph() root graph}.
     */
    public boolean atRoot() {
      return graphsInPath().size() == 1;
    }

    /** Returns the rootmost binding graph in the component path among the given components. */
    public BindingGraph rootmostGraph(Iterable<ComponentDescriptor> components) {
      ImmutableSet<ComponentDescriptor> set = ImmutableSet.copyOf(components);
      return rootmostGraph(graph -> set.contains(graph.componentDescriptor()));
    }

    /** Returns the binding graph within this path that represents the given component. */
    public BindingGraph graphForComponent(ComponentDescriptor component) {
      checkNotNull(component);
      return rootmostGraph(graph -> graph.componentDescriptor().equals(component));
    }

    /**
     * Returns the subpath from the root component to the matching {@code ancestor} of the current
     * component.
     */
    ComponentTreePath pathFromRootToAncestor(TypeElement ancestor) {
      ImmutableList.Builder<BindingGraph> path = ImmutableList.builder();
      for (BindingGraph graph : graphsInPath()) {
        path.add(graph);
        if (graph.componentDescriptor().componentDefinitionType().equals(ancestor)) {
          return create(path.build());
        }
      }
      throw new IllegalArgumentException(
          String.format("%s is not in the current path: %s", ancestor.getQualifiedName(), this));
    }

    /**
     * Returns the path from the root component to the child of the current component for a {@code
     * subcomponent}.
     *
     * @throws IllegalArgumentException if {@code subcomponent} is not a child of the current
     *     component
     */
    ComponentTreePath childPath(TypeElement subcomponent) {
      for (BindingGraph child : currentGraph().subgraphs()) {
        if (child.componentType().equals(subcomponent)) {
          return create(
              ImmutableList.<BindingGraph>builder().addAll(graphsInPath()).add(child).build());
        }
      }
      throw new IllegalArgumentException(
          String.format(
              "%s is not a child of %s",
              subcomponent.getQualifiedName(), currentGraph().componentType().getQualifiedName()));
    }

    private BindingGraph rootmostGraph(Predicate<? super BindingGraph> predicate) {
      return graphsInPath().stream().filter(predicate).findFirst().get();
    }

    /** Converts this {@link ComponentTreePath} into a {@link ComponentPath}. */
    ComponentPath toComponentPath() {
      return ComponentPath.create(
          graphsInPath().stream().map(BindingGraph::componentType).collect(toList()));
    }

    @Override
    public String toString() {
      return graphsInPath()
          .stream()
          .map(BindingGraph::componentType)
          .map(TypeElement::getQualifiedName)
          .collect(joining(" → "));
    }
  }

  /**
   * An immutable snapshot of a path through the binding graph.
   *
   * <p>The path contains pairs of a dependency request and the bindings resolved for it. At each
   * step after the first the dependency request is contained by one of the bindings resolved for
   * the previous dependency request.
   */
  public static final class DependencyTrace {
    private final ImmutableList<DependencyRequest> dependencyRequests;
    private final ImmutableList<ResolvedBindings> resolvedBindings;

    private DependencyTrace(
        Iterable<DependencyRequest> dependencyRequests,
        Iterable<ResolvedBindings> resolvedBindings) {
      this.dependencyRequests = ImmutableList.copyOf(dependencyRequests);
      this.resolvedBindings = ImmutableList.copyOf(resolvedBindings);
      checkArgument(
          this.dependencyRequests.size() == this.resolvedBindings.size(),
          "dependency requests and resolved bindings must have the same size: %s vs. %s",
          this.dependencyRequests,
          this.resolvedBindings);
    }

    /** Calls {@code consumer} for every dependency request and the bindings resolved for it. */
    protected final void forEach(
        BiConsumer<? super DependencyRequest, ? super ResolvedBindings> consumer) {
      Iterator<DependencyRequest> dependencyRequestIterator = dependencyRequests.iterator();
      Iterator<ResolvedBindings> resolvedBindingsIterator = resolvedBindings.iterator();
      while (dependencyRequestIterator.hasNext()) {
        consumer.accept(dependencyRequestIterator.next(), resolvedBindingsIterator.next());
      }
    }

    /**
     * Returns an ordered stream of the results of calling {@code function} on every dependency
     * request and the bindings resolved for it.
     */
    protected final <T> Stream<T> transform(
        BiFunction<? super DependencyRequest, ? super ResolvedBindings, T> function) {
      Iterator<DependencyRequest> dependencyRequestIterator = dependencyRequests.iterator();
      Iterator<ResolvedBindings> resolvedBindingsIterator = resolvedBindings.iterator();
      return StreamSupport.stream(
          new AbstractSpliterator<T>(dependencyRequests.size(), ORDERED | SIZED) {
            @Override
            public boolean tryAdvance(Consumer<? super T> action) {
              if (!dependencyRequestIterator.hasNext()) {
                return false;
              }
              action.accept(
                  function.apply(
                      dependencyRequestIterator.next(), resolvedBindingsIterator.next()));
              return true;
            }
          },
          false);
    }
  }
}

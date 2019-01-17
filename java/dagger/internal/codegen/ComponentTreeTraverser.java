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
import static com.google.common.base.Verify.verify;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.model.ComponentPath;
import dagger.model.DependencyRequest;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Predicate;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/**
 * An object that traverses the entire component hierarchy, starting from the root component.
 *
 * <p>Subclasses can override {@link #visitComponent(BindingGraph)} to perform custom logic at each
 * component in the tree, and {@link #visitSubcomponentFactoryMethod(BindingGraph, BindingGraph,
 * ExecutableElement)} to perform custom logic at each subcomponent factory method.
 */
public class ComponentTreeTraverser {

  /** The path from the root graph to the currently visited graph. */
  private final Deque<BindingGraph> bindingGraphPath = new ArrayDeque<>();

  /** Constructs a traverser for a root (component, not subcomponent) binding graph. */
  public ComponentTreeTraverser(BindingGraph rootGraph, CompilerOptions compilerOptions) {
    checkArgument(
        !rootGraph.componentDescriptor().isSubcomponent()
            || compilerOptions.aheadOfTimeSubcomponents(),
        "only root graphs can be traversed, not %s",
        rootGraph.componentTypeElement().getQualifiedName());
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
   *   <li>For each entry point in the component, calls {@link #visitEntryPoint(DependencyRequest,
   *       BindingGraph)}.
   *   <li>For each child component, calls {@link #visitComponent(BindingGraph)}, updating the
   *       traversal state.
   * </ol>
   *
   * @param graph the currently visited graph
   */
  protected void visitComponent(BindingGraph graph) {
    if (bindingGraphPath.size() > 1) {
      BindingGraph parent = Iterators.get(bindingGraphPath.descendingIterator(), 1);
      parent
          .componentDescriptor()
          .getFactoryMethodForChildComponent(graph.componentDescriptor())
          .ifPresent(
              childFactoryMethod ->
                  visitSubcomponentFactoryMethod(
                      graph, parent, childFactoryMethod.methodElement()));
    }

    for (ComponentMethodDescriptor entryPointMethod :
        graph.componentDescriptor().entryPointMethods()) {
      visitEntryPoint(entryPointMethod.dependencyRequest().get(), graph);
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
   * <p>This implementation does nothing.
   *
   * @param graph the graph for the component that contains the entry point
   */
  protected void visitEntryPoint(DependencyRequest entryPoint, BindingGraph graph) {}

  /**
   * Returns an immutable snapshot of the path from the root component to the currently visited
   * component.
   */
  protected final ComponentTreePath componentTreePath() {
    return ComponentTreePath.create(bindingGraphPath);
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
      return currentGraph().componentTypeElement();
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
        if (graph.componentTypeElement().equals(ancestor)) {
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
        if (child.componentTypeElement().equals(subcomponent)) {
          return create(
              ImmutableList.<BindingGraph>builder().addAll(graphsInPath()).add(child).build());
        }
      }
      throw new IllegalArgumentException(
          String.format(
              "%s is not a child of %s",
              subcomponent.getQualifiedName(),
              currentGraph().componentTypeElement().getQualifiedName()));
    }

    private BindingGraph rootmostGraph(Predicate<? super BindingGraph> predicate) {
      return graphsInPath().stream().filter(predicate).findFirst().get();
    }

    /** Converts this {@link ComponentTreePath} into a {@link ComponentPath}. */
    ComponentPath toComponentPath() {
      return ComponentPath.create(
          graphsInPath().stream().map(BindingGraph::componentTypeElement).collect(toList()));
    }

    @Override
    public final String toString() {
      return graphsInPath().stream()
          .map(BindingGraph::componentTypeElement)
          .map(TypeElement::getQualifiedName)
          .collect(joining(" → "));
    }
  }
}

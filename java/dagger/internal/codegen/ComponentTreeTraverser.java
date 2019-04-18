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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static dagger.internal.codegen.DaggerStreams.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.model.ComponentPath;
import dagger.model.DependencyRequest;
import java.util.ArrayDeque;
import java.util.Deque;
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

  /** The {@link ComponentPath} for each component in {@link #bindingGraphPath}. */
  private final Deque<ComponentPath> componentPaths = new ArrayDeque<>();

  /** Constructs a traverser for a root (component, not subcomponent) binding graph. */
  public ComponentTreeTraverser(BindingGraph rootGraph) {
    bindingGraphPath.add(rootGraph);
    componentPaths.add(ComponentPath.create(ImmutableList.of(rootGraph.componentTypeElement())));
  }

  /**
   * Calls {@link #visitComponent(BindingGraph)} for the root component.
   *
   * @throws IllegalStateException if a traversal is in progress
   */
  public final void traverseComponents() {
    checkState(bindingGraphPath.size() == 1);
    checkState(componentPaths.size() == 1);
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
      ComponentPath childPath =
          ComponentPath.create(
              bindingGraphPath.stream()
                  .map(BindingGraph::componentTypeElement)
                  .collect(toImmutableList()));
      componentPaths.addLast(childPath);
      try {
        visitComponent(child);
      } finally {
        verify(bindingGraphPath.removeLast().equals(child));
        verify(componentPaths.removeLast().equals(childPath));
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
  protected final ComponentPath componentPath() {
    return componentPaths.getLast();
  }

  /**
   * Returns the subpath from the root component to the matching {@code ancestor} of the current
   * component.
   */
  protected final ComponentPath pathFromRootToAncestor(TypeElement ancestor) {
    for (ComponentPath componentPath : componentPaths) {
      if (componentPath.currentComponent().equals(ancestor)) {
        return componentPath;
      }
    }
    throw new IllegalArgumentException(
        String.format("%s is not in the current path: %s", ancestor.getQualifiedName(), this));
  }

  /**
   * Returns the BindingGraph for {@code ancestor}, where {@code ancestor} is in the component path
   * of the current traversal.
   */
  protected final BindingGraph graphForAncestor(TypeElement ancestor) {
    for (BindingGraph graph : bindingGraphPath) {
      if (graph.componentTypeElement().equals(ancestor)) {
        return graph;
      }
    }
    throw new IllegalArgumentException(
        String.format("%s is not in the current path: %s", ancestor.getQualifiedName(), this));
  }
}

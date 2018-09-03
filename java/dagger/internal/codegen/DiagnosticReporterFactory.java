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
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Predicates.equalTo;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.indexOf;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Lists.asList;
import static dagger.internal.codegen.DaggerElements.DECLARATION_ORDER;
import static dagger.internal.codegen.DaggerElements.elementEncloses;
import static dagger.internal.codegen.DaggerElements.elementToString;
import static dagger.internal.codegen.DaggerGraphs.shortestPath;
import static java.util.Collections.min;
import static java.util.Comparator.comparing;
import static java.util.Comparator.comparingInt;

import com.google.auto.common.MoreElements;
import com.google.common.base.Function;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.FormatMethod;
import dagger.model.BindingGraph;
import dagger.model.BindingGraph.BindingNode;
import dagger.model.BindingGraph.ChildFactoryMethodEdge;
import dagger.model.BindingGraph.ComponentNode;
import dagger.model.BindingGraph.DependencyEdge;
import dagger.model.BindingGraph.Edge;
import dagger.model.BindingGraph.Node;
import dagger.model.ComponentPath;
import dagger.model.DependencyRequest;
import dagger.spi.BindingGraphPlugin;
import dagger.spi.DiagnosticReporter;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

/** A factory for {@link DiagnosticReporter}s. */
// TODO(ronshapiro): If multiple plugins print errors on the same node/edge, should we condense the
// messages and only print the dependency trace once?
final class DiagnosticReporterFactory {
  private final DaggerTypes types;
  private final Messager messager;
  private final DependencyRequestFormatter dependencyRequestFormatter;

  @Inject
  DiagnosticReporterFactory(
      DaggerTypes types, Messager messager, DependencyRequestFormatter dependencyRequestFormatter) {
    this.types = types;
    this.messager = messager;
    this.dependencyRequestFormatter = dependencyRequestFormatter;

  }

  /** Creates a reporter for a binding graph and a plugin. */
  DiagnosticReporterImpl reporter(BindingGraph graph, BindingGraphPlugin plugin) {
    return new DiagnosticReporterImpl(graph, plugin.pluginName());
  }

  private static <K, V> Function<K, V> memoize(Function<K, V> uncached) {
    return CacheBuilder.newBuilder().build(CacheLoader.from(uncached));
  }

  /**
   * A {@link DiagnosticReporter} that keeps track of which {@linkplain Diagnostic.Kind kinds} of
   * diagnostics were reported.
   */
  final class DiagnosticReporterImpl implements DiagnosticReporter {

    /** A cached function from type to all of its supertypes in breadth-first order. */
    private final Function<TypeElement, Iterable<TypeElement>> supertypes =
        memoize(
            component ->
                transform(types.supertypes(component.asType()), type -> asTypeElement(type)));

    /** The shortest path (value) from an entry point (column) to a binding (row). */
    private final Table<BindingNode, DependencyEdge, ImmutableList<Node>> shortestPaths =
        HashBasedTable.create();

    private final BindingGraph graph;
    private final String plugin;
    private final TypeElement rootComponent;
    private final ImmutableSet.Builder<Diagnostic.Kind> reportedDiagnosticKinds =
        ImmutableSet.builder();

    DiagnosticReporterImpl(BindingGraph graph, String plugin) {
      this.graph = graph;
      this.plugin = plugin;
      this.rootComponent = graph.rootComponentNode().componentPath().currentComponent();
    }

    /** Returns which {@linkplain Diagnostic.Kind kinds} of diagnostics were reported. */
    ImmutableSet<Diagnostic.Kind> reportedDiagnosticKinds() {
      return reportedDiagnosticKinds.build();
    }

    @Override
    public void reportComponent(
        Diagnostic.Kind diagnosticKind, ComponentNode componentNode, String messageFormat) {
      StringBuilder messageBuilder = new StringBuilder(messageFormat);
      appendComponentPathUnlessAtRoot(messageBuilder, componentNode);
      printMessage(diagnosticKind, messageBuilder, rootComponent);
    }

    @Override
    @FormatMethod
    public void reportComponent(
        Diagnostic.Kind diagnosticKind,
        ComponentNode componentNode,
        String messageFormat,
        Object firstArg,
        Object... moreArgs) {
      reportComponent(
          diagnosticKind, componentNode, formatMessage(messageFormat, firstArg, moreArgs));
    }

    // TODO(ronshapiro): should this also include the binding element?
    @Override
    public void reportBinding(
        Diagnostic.Kind diagnosticKind, BindingNode bindingNode, String message) {
      StringBuilder messageBuilder = new StringBuilder(message);
      appendEntryPointsAndOneTrace(messageBuilder, bindingNode);
      printMessage(diagnosticKind, messageBuilder, rootComponent);
    }

    @Override
    public void reportBinding(
        Diagnostic.Kind diagnosticKind,
        BindingNode bindingNode,
        String messageFormat,
        Object firstArg,
        Object... moreArgs) {
      reportBinding(diagnosticKind, bindingNode, formatMessage(messageFormat, firstArg, moreArgs));
    }

    @Override
    public void reportDependency(
        Diagnostic.Kind diagnosticKind, DependencyEdge dependencyEdge, String message) {
      StringBuilder messageBuilder = new StringBuilder(message);
      appendEntryPointsAndOneTrace(messageBuilder, dependencyEdge);
      printMessage(diagnosticKind, messageBuilder, rootComponent);
    }

    @Override
    public void reportDependency(
        Diagnostic.Kind diagnosticKind,
        DependencyEdge dependencyEdge,
        String messageFormat,
        Object firstArg,
        Object... moreArgs) {
      reportDependency(
          diagnosticKind, dependencyEdge, formatMessage(messageFormat, firstArg, moreArgs));
    }

    @Override
    public void reportSubcomponentFactoryMethod(
        Diagnostic.Kind diagnosticKind,
        ChildFactoryMethodEdge childFactoryMethodEdge,
        String message) {
      printMessage(
          diagnosticKind, new StringBuilder(message), childFactoryMethodEdge.factoryMethod());
    }

    @Override
    public void reportSubcomponentFactoryMethod(
        Diagnostic.Kind diagnosticKind,
        ChildFactoryMethodEdge childFactoryMethodEdge,
        String messageFormat,
        Object firstArg,
        Object... moreArgs) {
      reportSubcomponentFactoryMethod(
          diagnosticKind, childFactoryMethodEdge, formatMessage(messageFormat, firstArg, moreArgs));
    }

    private String formatMessage(String messageFormat, Object firstArg, Object[] moreArgs) {
      return String.format(messageFormat, asList(firstArg, moreArgs).toArray());
    }

    /**
     * Appends the dependency trace to {@code dependencyEdge} from one of the entry points from
     * which it is reachable, and any remaining entry points, to {@code message}.
     */
    private void appendEntryPointsAndOneTrace(
        StringBuilder message, DependencyEdge dependencyEdge) {
      if (dependencyEdge.isEntryPoint()) {
        appendEntryPoint(message, dependencyEdge);
      } else { // it's part of a binding
        dependencyRequestFormatter.appendFormatLine(message, dependencyEdge.dependencyRequest());
        appendEntryPointsAndOneTrace(message, (BindingNode) source(dependencyEdge));
      }
    }

    /**
     * Appends the dependency trace to {@code bindingNode} from one of the entry points from which
     * it is reachable, and any remaining entry points, to {@code message}.
     */
    private void appendEntryPointsAndOneTrace(StringBuilder message, BindingNode bindingNode) {
      ImmutableSet<DependencyEdge> entryPoints =
          graph.entryPointEdgesDependingOnBindingNode(bindingNode);
      // Show the full dependency trace for one entry point.
      DependencyEdge entryPointForTrace =
          min(
              entryPoints,
              // prefer entry points in components closest to the root
              rootComponentFirst()
                  // then prefer entry points with a short dependency path to the error
                  .thenComparing(shortestDependencyPathFirst(bindingNode))
                  // then prefer entry points declared in the component to those declared in a
                  // supertype
                  .thenComparing(nearestComponentSupertypeFirst())
                  // finally prefer entry points declared first in their enclosing type
                  .thenComparing(requestElementDeclarationOrder()));
      appendDependencyTrace(message, entryPointForTrace, bindingNode);

      // List the remaining entry points, showing which component they're in.
      if (entryPoints.size() > 1) {
        message.append("\nThe following other entry points also depend on it:");
        entryPoints
            .stream()
            .filter(entryPoint -> !entryPoint.equals(entryPointForTrace))
            .sorted(
                // start with entry points in components closest to the root
                rootComponentFirst()
                    // then list entry points declared in the component before those declared in a
                    // supertype
                    .thenComparing(nearestComponentSupertypeFirst())
                    // finally list entry points in declaration order in their declaring type
                    .thenComparing(requestElementDeclarationOrder()))
            .forEachOrdered(
                entryPoint -> {
                  message.append("\n    ");
                  Element requestElement = entryPoint.dependencyRequest().requestElement().get();
                  message.append(elementToString(requestElement));

                  // For entry points declared in subcomponents or supertypes of the root component,
                  // append the component path to make clear to the user which component it's in.
                  ComponentPath componentPath = source(entryPoint).componentPath();
                  if (!componentPath.atRoot()
                      || !requestElement.getEnclosingElement().equals(rootComponent)) {
                    message.append(String.format(" [%s]", componentPath));
                  }
                });
      }
    }

    // TODO(ronshapiro): Adding a DependencyPath type to dagger.model could be useful, i.e.
    // bindingGraph.shortestPathFromEntryPoint(DependencyEdge, BindingNode)
    private void appendDependencyTrace(
        StringBuilder message, DependencyEdge entryPoint, BindingNode bindingNode) {
      checkArgument(entryPoint.isEntryPoint());
      Node entryPointBinding = graph.incidentNodes(entryPoint).target();
      ImmutableList<Node> shortestBindingPath =
          shortestPath(
              node -> Sets.filter(graph.successors(node), BindingNode.class::isInstance),
              entryPointBinding,
              bindingNode);
      verify(
          !shortestBindingPath.isEmpty(),
          "no dependency path from %s to %s in %s",
          entryPoint,
          bindingNode,
          graph);

      message.ensureCapacity(
          message.capacity() + shortestBindingPath.size() * 100 /* a guess heuristic */);
      for (int i = shortestBindingPath.size() - 1; i > 0; i--) {
        Set<Edge> dependenciesBetween =
            graph.edgesConnecting(shortestBindingPath.get(i - 1), shortestBindingPath.get(i));
        DependencyRequest dependencyRequest =
            // If a binding requests a key more than once, any of them should be fine to get to
            // the shortest path
            ((DependencyEdge) Iterables.get(dependenciesBetween, 0)).dependencyRequest();
        dependencyRequestFormatter.appendFormatLine(message, dependencyRequest);
      }
      appendEntryPoint(message, entryPoint);
    }

    private void appendEntryPoint(StringBuilder message, DependencyEdge entryPoint) {
      checkArgument(entryPoint.isEntryPoint());
      dependencyRequestFormatter.appendFormatLine(message, entryPoint.dependencyRequest());
      appendComponentPathUnlessAtRoot(message, source(entryPoint));
    }

    private Node source(Edge edge) {
      return graph.incidentNodes(edge).source();
    }

    private void printMessage(
        Diagnostic.Kind diagnosticKind, StringBuilder message, Element elementToReport) {
      reportedDiagnosticKinds.add(diagnosticKind);
      // TODO(ronshapiro): should we create a HashSet out of elementEncloses() so we don't
      // need to do an O(n) contains() each time?
      if (!elementEncloses(rootComponent, elementToReport)) {
        insertBracketPrefix(message, elementToString(elementToReport));
        elementToReport = rootComponent;
      }
      messager.printMessage(diagnosticKind, insertBracketPrefix(message, plugin), elementToReport);
    }

    private void appendComponentPathUnlessAtRoot(StringBuilder message, Node node) {
      if (!node.componentPath().equals(graph.rootComponentNode().componentPath())) {
        message.append(String.format(" [%s]", node.componentPath()));
      }
    }

    @CanIgnoreReturnValue
    private StringBuilder insertBracketPrefix(StringBuilder messageBuilder, String prefix) {
      return messageBuilder.insert(0, String.format("[%s] ", prefix));
    }

    /**
     * Returns a comparator that sorts entry points in components whose paths from the root are
     * shorter first.
     */
    private Comparator<DependencyEdge> rootComponentFirst() {
      return comparingInt(entryPoint -> source(entryPoint).componentPath().components().size());
    }

    /**
     * Returns a comparator that puts entry points whose shortest dependency path to {@code
     * bindingNode} is shortest first.
     */
    private Comparator<DependencyEdge> shortestDependencyPathFirst(BindingNode bindingNode) {
      Map<DependencyEdge, ImmutableList<Node>> shortestPathsToBinding =
          shortestPaths.row(bindingNode);
      return comparing(
          entryPoint ->
              shortestPathsToBinding
                  .computeIfAbsent(
                      entryPoint, computeShortestPathToBindingFromEntryNode(bindingNode))
                  .size());
    }

    private Function<DependencyEdge, ImmutableList<Node>> computeShortestPathToBindingFromEntryNode(
        BindingNode bindingNode) {
      return entryPoint ->
          shortestPath(
              node -> filter(graph.successors(node), successor -> successor instanceof BindingNode),
              graph.incidentNodes(entryPoint).target(),
              bindingNode);
    }

    /**
     * Returns a comparator that sorts entry points in by the distance of the type that declares
     * them from the type of the component that contains them.
     *
     * <p>For instance, an entry point declared directly in the component type would sort before one
     * declared in a direct supertype, which would sort before one declared in a supertype of a
     * supertype.
     */
    private Comparator<DependencyEdge> nearestComponentSupertypeFirst() {
      return comparingInt(
          entryPoint ->
              indexOf(
                  supertypes.apply(componentContainingEntryPoint(entryPoint)),
                  equalTo(typeDeclaringEntryPoint(entryPoint))));
    }

    private TypeElement componentContainingEntryPoint(DependencyEdge entryPoint) {
      return source(entryPoint).componentPath().currentComponent();
    }

    private TypeElement typeDeclaringEntryPoint(DependencyEdge entryPoint) {
      return MoreElements.asType(
          entryPoint.dependencyRequest().requestElement().get().getEnclosingElement());
    }

    /**
     * Returns a comparator that sorts entry points in the order in which they were declared in
     * their declaring type.
     *
     * <p>Only useful to compare entry points declared in the same type.
     */
    private Comparator<DependencyEdge> requestElementDeclarationOrder() {
      return comparing(
          entryPoint -> entryPoint.dependencyRequest().requestElement().get(), DECLARATION_ORDER);
    }
  }
}

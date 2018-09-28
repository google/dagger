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
import static com.google.common.base.Predicates.equalTo;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Iterables.getLast;
import static com.google.common.collect.Iterables.indexOf;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Lists.asList;
import static com.google.common.collect.Sets.filter;
import static dagger.internal.codegen.DaggerElements.DECLARATION_ORDER;
import static dagger.internal.codegen.DaggerElements.closestEnclosingTypeElement;
import static dagger.internal.codegen.DaggerElements.elementEncloses;
import static dagger.internal.codegen.DaggerElements.elementToString;
import static dagger.internal.codegen.DaggerGraphs.shortestPath;
import static dagger.internal.codegen.DaggerStreams.instancesOf;
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;
import static java.util.Collections.min;
import static java.util.Comparator.comparing;
import static java.util.Comparator.comparingInt;

import com.google.auto.common.MoreElements;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Table;
import com.google.errorprone.annotations.FormatMethod;
import dagger.model.BindingGraph;
import dagger.model.BindingGraph.BindingNode;
import dagger.model.BindingGraph.ChildFactoryMethodEdge;
import dagger.model.BindingGraph.ComponentNode;
import dagger.model.BindingGraph.DependencyEdge;
import dagger.model.BindingGraph.Edge;
import dagger.model.BindingGraph.MaybeBindingNode;
import dagger.model.BindingGraph.Node;
import dagger.model.ComponentPath;
import dagger.spi.BindingGraphPlugin;
import dagger.spi.DiagnosticReporter;
import java.util.Comparator;
import java.util.Formatter;
import java.util.Set;
import java.util.function.Function;
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
    // If Android Guava is on the processor path, then c.g.c.b.Function (which LoadingCache
    // implements) does not extend j.u.f.Function.

    // First, explicitly convert uncached to c.g.c.b.Function because CacheLoader.from() expects
    // one.
    com.google.common.base.Function<K, V> uncachedAsBaseFunction = uncached::apply;

    LoadingCache<K, V> cache =
        CacheBuilder.newBuilder().build(CacheLoader.from(uncachedAsBaseFunction));

    // Second, explicitly convert LoadingCache to j.u.f.Function.
    @SuppressWarnings("deprecation") // uncachedAsBaseFunction throws only unchecked exceptions
    Function<K, V> memoized = cache::apply;

    return memoized;
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
    private final Table<MaybeBindingNode, DependencyEdge, ImmutableList<Node>> shortestPaths =
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
      StringBuilder message = new StringBuilder(messageFormat);
      appendComponentPathUnlessAtRoot(message, componentNode);
      printMessage(diagnosticKind, message, rootComponent);
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
        Diagnostic.Kind diagnosticKind, MaybeBindingNode bindingNode, String message) {
      printMessage(diagnosticKind, message + new DiagnosticInfo(bindingNode), rootComponent);
    }

    @Override
    public void reportBinding(
        Diagnostic.Kind diagnosticKind,
        MaybeBindingNode bindingNode,
        String messageFormat,
        Object firstArg,
        Object... moreArgs) {
      reportBinding(diagnosticKind, bindingNode, formatMessage(messageFormat, firstArg, moreArgs));
    }

    @Override
    public void reportDependency(
        Diagnostic.Kind diagnosticKind, DependencyEdge dependencyEdge, String message) {
      printMessage(diagnosticKind, message + new DiagnosticInfo(dependencyEdge), rootComponent);
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
      printMessage(diagnosticKind, message, childFactoryMethodEdge.factoryMethod());
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

    private Node source(Edge edge) {
      return graph.network().incidentNodes(edge).source();
    }

    void printMessage(
        Diagnostic.Kind diagnosticKind, CharSequence message, Element elementToReport) {
      reportedDiagnosticKinds.add(diagnosticKind);
      StringBuilder fullMessage = new StringBuilder();
      appendBracketPrefix(fullMessage, plugin);
      // TODO(ronshapiro): should we create a HashSet out of elementEncloses() so we don't
      // need to do an O(n) contains() each time?
      if (!elementEncloses(rootComponent, elementToReport)) {
        appendBracketPrefix(fullMessage, elementToString(elementToReport));
        elementToReport = rootComponent;
      }
      messager.printMessage(diagnosticKind, fullMessage.append(message), elementToReport);
    }

    private void appendComponentPathUnlessAtRoot(StringBuilder message, Node node) {
      if (!node.componentPath().equals(graph.rootComponentNode().componentPath())) {
        new Formatter(message).format(" [%s]", node.componentPath());
      }
    }

    private void appendBracketPrefix(StringBuilder message, String prefix) {
      new Formatter(message).format("[%s] ", prefix);
    }

    /** The diagnostic information associated with an error. */
    private final class DiagnosticInfo {
      final ImmutableList<DependencyEdge> dependencyTrace;
      final ImmutableSet<DependencyEdge> requests;
      final ImmutableSet<DependencyEdge> entryPoints;

      DiagnosticInfo(MaybeBindingNode bindingNode) {
        entryPoints = graph.entryPointEdgesDependingOnBindingNode(bindingNode);
        requests = requests(bindingNode);
        dependencyTrace = dependencyTrace(bindingNode, entryPoints);
      }

      DiagnosticInfo(DependencyEdge dependencyEdge) {
        requests = ImmutableSet.of(dependencyEdge);
        ImmutableList.Builder<DependencyEdge> dependencyTraceBuilder = ImmutableList.builder();
        dependencyTraceBuilder.add(dependencyEdge);

        if (dependencyEdge.isEntryPoint()) {
          entryPoints = ImmutableSet.of(dependencyEdge);
        } else {
          // It's not an entry point, so it's part of a binding
          BindingNode bindingNode = (BindingNode) source(dependencyEdge);
          entryPoints = graph.entryPointEdgesDependingOnBindingNode(bindingNode);
          dependencyTraceBuilder.addAll(dependencyTrace(bindingNode, entryPoints));
        }
        dependencyTrace = dependencyTraceBuilder.build();
      }

      @Override
      public String toString() {
        StringBuilder message =
            new StringBuilder(dependencyTrace.size() * 100 /* a guess heuristic */);

        // Print the dependency trace.
        dependencyTrace.forEach(
            edge -> dependencyRequestFormatter.appendFormatLine(message, edge.dependencyRequest()));
        appendComponentPathUnlessAtRoot(message, source(getLast(dependencyTrace)));

        // List any other dependency requests.
        ImmutableSet<Element> otherRequests =
            requests.stream()
                .filter(request -> !request.isEntryPoint()) // skip entry points, listed below
                // skip the request from the dependency trace above
                .filter(request -> !request.equals(dependencyTrace.get(0)))
                .map(request -> request.dependencyRequest().requestElement().get())
                .collect(toImmutableSet());
        if (!otherRequests.isEmpty()) {
          message.append("\nIt is also requested at:");
          for (Element otherRequest : otherRequests) {
            message.append("\n    ").append(elementToString(otherRequest));
          }
        }

        // List the remaining entry points, showing which component they're in.
        if (entryPoints.size() > 1) {
          message.append("\nThe following other entry points also depend on it:");
          entryPoints.stream()
              .filter(entryPoint -> !entryPoint.equals(getLast(dependencyTrace)))
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

                    // For entry points declared in subcomponents or supertypes of the root
                    // component, append the component path to make clear to the user which
                    // component it's in.
                    ComponentPath componentPath = source(entryPoint).componentPath();
                    if (!componentPath.atRoot()
                        || !requestElement.getEnclosingElement().equals(rootComponent)) {
                      message.append(String.format(" [%s]", componentPath));
                    }
                  });
        }
        return message.toString();
      }

      /**
       * Returns the dependency trace from one of the {@code entryPoints} to {@code bindingNode} to
       * {@code message} as a list <i>ending with</i> the entry point.
       */
      // TODO(ronshapiro): Adding a DependencyPath type to dagger.model could be useful, i.e.
      // bindingGraph.shortestPathFromEntryPoint(DependencyEdge, MaybeBindingNode)
      ImmutableList<DependencyEdge> dependencyTrace(
          MaybeBindingNode bindingNode, ImmutableSet<DependencyEdge> entryPoints) {
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

        ImmutableList<Node> shortestBindingPath =
            shortestPathFromEntryPoint(entryPointForTrace, bindingNode);
        verify(
            !shortestBindingPath.isEmpty(),
            "no dependency path from %s to %s in %s",
            entryPointForTrace,
            bindingNode,
            graph);

        ImmutableList.Builder<DependencyEdge> dependencyTrace = ImmutableList.builder();
        dependencyTrace.add(entryPointForTrace);
        for (int i = 0; i < shortestBindingPath.size() - 1; i++) {
          Set<Edge> dependenciesBetween =
              graph
                  .network()
                  .edgesConnecting(shortestBindingPath.get(i), shortestBindingPath.get(i + 1));
          // If a binding requests a key more than once, any of them should be fine to get to the
          // shortest path
          dependencyTrace.add((DependencyEdge) Iterables.get(dependenciesBetween, 0));
        }
        return dependencyTrace.build().reverse();
      }

      /** Returns all the nonsynthetic dependency requests for a binding node. */
      ImmutableSet<DependencyEdge> requests(MaybeBindingNode bindingNode) {
        return graph.network().inEdges(bindingNode).stream()
            .flatMap(instancesOf(DependencyEdge.class))
            .filter(edge -> edge.dependencyRequest().requestElement().isPresent())
            .sorted(requestEnclosingTypeName().thenComparing(requestElementDeclarationOrder()))
            .collect(toImmutableSet());
      }

      /**
       * Returns a comparator that sorts entry points in components whose paths from the root are
       * shorter first.
       */
      Comparator<DependencyEdge> rootComponentFirst() {
        return comparingInt(entryPoint -> source(entryPoint).componentPath().components().size());
      }

      /**
       * Returns a comparator that puts entry points whose shortest dependency path to {@code
       * bindingNode} is shortest first.
       */
      Comparator<DependencyEdge> shortestDependencyPathFirst(MaybeBindingNode bindingNode) {
        return comparing(entryPoint -> shortestPathFromEntryPoint(entryPoint, bindingNode).size());
      }

      ImmutableList<Node> shortestPathFromEntryPoint(
          DependencyEdge entryPoint, MaybeBindingNode bindingNode) {
        return shortestPaths
            .row(bindingNode)
            .computeIfAbsent(
                entryPoint,
                ep ->
                    shortestPath(
                        node ->
                            filter(
                                graph.network().successors(node),
                                MaybeBindingNode.class::isInstance),
                        graph.network().incidentNodes(ep).target(),
                        bindingNode));
      }

      /**
       * Returns a comparator that sorts entry points in by the distance of the type that declares
       * them from the type of the component that contains them.
       *
       * <p>For instance, an entry point declared directly in the component type would sort before
       * one declared in a direct supertype, which would sort before one declared in a supertype of
       * a supertype.
       */
      Comparator<DependencyEdge> nearestComponentSupertypeFirst() {
        return comparingInt(
            entryPoint ->
                indexOf(
                    supertypes.apply(componentContainingEntryPoint(entryPoint)),
                    equalTo(typeDeclaringEntryPoint(entryPoint))));
      }

      TypeElement componentContainingEntryPoint(DependencyEdge entryPoint) {
        return source(entryPoint).componentPath().currentComponent();
      }

      TypeElement typeDeclaringEntryPoint(DependencyEdge entryPoint) {
        return MoreElements.asType(
            entryPoint.dependencyRequest().requestElement().get().getEnclosingElement());
      }

      /**
       * Returns a comparator that sorts dependency edges lexicographically by the qualified name of
       * the type that contains them. Only appropriate for edges with request elements.
       */
      Comparator<DependencyEdge> requestEnclosingTypeName() {
        return comparing(
            edge ->
                closestEnclosingTypeElement(edge.dependencyRequest().requestElement().get())
                    .getQualifiedName()
                    .toString());
      }

      /**
       * Returns a comparator that sorts edges in the order in which their request elements were
       * declared in their declaring type.
       *
       * <p>Only useful to compare edges whose request elements were declared in the same type.
       */
      Comparator<DependencyEdge> requestElementDeclarationOrder() {
        return comparing(
            edge -> edge.dependencyRequest().requestElement().get(), DECLARATION_ORDER);
      }
    }
  }
}

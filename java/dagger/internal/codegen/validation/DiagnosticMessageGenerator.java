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

package dagger.internal.codegen.validation;

import static androidx.room.compiler.processing.XElementKt.isTypeElement;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Predicates.equalTo;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.getLast;
import static com.google.common.collect.Iterables.indexOf;
import static com.google.common.collect.Iterables.transform;
import static dagger.internal.codegen.base.ElementFormatter.elementToString;
import static dagger.internal.codegen.extension.DaggerGraphs.shortestPath;
import static dagger.internal.codegen.extension.DaggerStreams.instancesOf;
import static dagger.internal.codegen.extension.DaggerStreams.presentValues;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.xprocessing.XElements.asExecutable;
import static dagger.internal.codegen.xprocessing.XElements.asTypeElement;
import static dagger.internal.codegen.xprocessing.XElements.closestEnclosingTypeElement;
import static dagger.internal.codegen.xprocessing.XElements.isExecutable;
import static java.util.Collections.min;
import static java.util.Comparator.comparing;
import static java.util.Comparator.comparingInt;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Table;
import dagger.internal.codegen.base.ElementFormatter;
import dagger.internal.codegen.base.Formatter;
import dagger.internal.codegen.binding.DependencyRequestFormatter;
import dagger.internal.codegen.model.Binding;
import dagger.internal.codegen.model.BindingGraph;
import dagger.internal.codegen.model.BindingGraph.DependencyEdge;
import dagger.internal.codegen.model.BindingGraph.Edge;
import dagger.internal.codegen.model.BindingGraph.MaybeBinding;
import dagger.internal.codegen.model.BindingGraph.Node;
import dagger.internal.codegen.model.ComponentPath;
import dagger.internal.codegen.model.DaggerElement;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import javax.inject.Inject;

/** Helper class for generating diagnostic messages. */
public final class DiagnosticMessageGenerator {

  /** Injectable factory for {@code DiagnosticMessageGenerator}. */
  public static final class Factory {
    private final DependencyRequestFormatter dependencyRequestFormatter;
    private final ElementFormatter elementFormatter;

    @Inject
    Factory(
        DependencyRequestFormatter dependencyRequestFormatter,
        ElementFormatter elementFormatter) {
      this.dependencyRequestFormatter = dependencyRequestFormatter;
      this.elementFormatter = elementFormatter;
    }

    /** Creates a {@code DiagnosticMessageGenerator} for the given binding graph. */
    public DiagnosticMessageGenerator create(BindingGraph graph) {
      return new DiagnosticMessageGenerator(graph, dependencyRequestFormatter, elementFormatter);
    }
  }

  private final BindingGraph graph;
  private final DependencyRequestFormatter dependencyRequestFormatter;
  private final ElementFormatter elementFormatter;

  /** A cached function from type to all of its supertypes in breadth-first order. */
  private final Function<XTypeElement, Iterable<XTypeElement>> supertypes;

  /** The shortest path (value) from an entry point (column) to a binding (row). */
  private final Table<MaybeBinding, DependencyEdge, ImmutableList<Node>> shortestPaths =
      HashBasedTable.create();

  private static <K, V> Function<K, V> memoize(Function<K, V> uncached) {
    // If Android Guava is on the processor path, then c.g.c.b.Function (which LoadingCache
    // implements) does not extend j.u.f.Function.
    // TODO(erichang): Fix current breakages and try to remove this to enforce not having this on
    // processor path.

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

  private DiagnosticMessageGenerator(
      BindingGraph graph,
      DependencyRequestFormatter dependencyRequestFormatter,
      ElementFormatter elementFormatter) {
    this.graph = graph;
    this.dependencyRequestFormatter = dependencyRequestFormatter;
    this.elementFormatter = elementFormatter;
    supertypes =
        memoize(component -> transform(component.getType().getSuperTypes(), XType::getTypeElement));
  }

  public String getMessage(MaybeBinding binding) {
    ImmutableSet<DependencyEdge> entryPoints = graph.entryPointEdgesDependingOnBinding(binding);
    ImmutableSet<DependencyEdge> requests = requests(binding);
    ImmutableList<DependencyEdge> dependencyTrace = dependencyTrace(binding, entryPoints);

    return getMessageInternal(dependencyTrace, requests, entryPoints);
  }

  public String getMessage(DependencyEdge dependencyEdge) {
    ImmutableSet<DependencyEdge> requests = ImmutableSet.of(dependencyEdge);

    ImmutableSet<DependencyEdge> entryPoints;
    ImmutableList<DependencyEdge> dependencyTrace;
    if (dependencyEdge.isEntryPoint()) {
      entryPoints = ImmutableSet.of(dependencyEdge);
      dependencyTrace = ImmutableList.of(dependencyEdge);
    } else {
      // It's not an entry point, so it's part of a binding
      Binding binding = (Binding) source(dependencyEdge);
      entryPoints = graph.entryPointEdgesDependingOnBinding(binding);
      dependencyTrace =
          ImmutableList.<DependencyEdge>builder()
              .add(dependencyEdge)
              .addAll(dependencyTrace(binding, entryPoints))
              .build();
    }

    return getMessageInternal(dependencyTrace, requests, entryPoints);
  }

  private String getMessageInternal(
      ImmutableList<DependencyEdge> dependencyTrace,
      ImmutableSet<DependencyEdge> requests,
      ImmutableSet<DependencyEdge> entryPoints) {
    StringBuilder message =
        graph.isFullBindingGraph()
            ? new StringBuilder()
            : new StringBuilder(dependencyTrace.size() * 100 /* a guess heuristic */);

    // Print the dependency trace unless it's a full binding graph
    if (!graph.isFullBindingGraph()) {
      dependencyTrace.forEach(
          edge -> dependencyRequestFormatter.appendFormatLine(message, edge.dependencyRequest()));
      if (!dependencyTrace.isEmpty()) {
        appendComponentPathUnlessAtRoot(message, source(getLast(dependencyTrace)));
      }
    }
    message.append(getRequestsNotInTrace(dependencyTrace, requests, entryPoints));
    return message.toString();
  }

  public String getRequestsNotInTrace(
      ImmutableList<DependencyEdge> dependencyTrace,
      ImmutableSet<DependencyEdge> requests,
      ImmutableSet<DependencyEdge> entryPoints) {
    StringBuilder message = new StringBuilder();
    // Print any dependency requests that aren't shown as part of the dependency trace.
    ImmutableSet<XElement> requestsToPrint =
        requests.stream()
            // if printing entry points, skip entry points and the traced request
            .filter(
                request ->
                    graph.isFullBindingGraph()
                        || (!request.isEntryPoint() && !isTracedRequest(dependencyTrace, request)))
            .map(request -> request.dependencyRequest().requestElement())
            .flatMap(presentValues())
            .map(DaggerElement::xprocessing)
            .collect(toImmutableSet());
    if (!requestsToPrint.isEmpty()) {
      message
          .append("\nIt is")
          .append(graph.isFullBindingGraph() ? " " : " also ")
          .append("requested at:");
      elementFormatter.formatIndentedList(message, requestsToPrint, 1);
    }

    // Print the remaining entry points, showing which component they're in, unless it's a full
    // binding graph
    if (!graph.isFullBindingGraph() && entryPoints.size() > 1) {
      message.append("\nThe following other entry points also depend on it:");
      entryPointFormatter.formatIndentedList(
          message,
          entryPoints.stream()
              .filter(entryPoint -> !entryPoint.equals(getLast(dependencyTrace)))
              .sorted(
                  // 1. List entry points in components closest to the root first.
                  // 2. List entry points declared in a component before those in a supertype.
                  // 3. List entry points in declaration order in their declaring type.
                  rootComponentFirst()
                      .thenComparing(nearestComponentSupertypeFirst())
                      .thenComparing(requestElementDeclarationOrder()))
              .collect(toImmutableList()),
          1);
    }
    return message.toString();
  }

  public void appendComponentPathUnlessAtRoot(StringBuilder message, Node node) {
    if (!node.componentPath().equals(graph.rootComponentNode().componentPath())) {
      message.append(String.format(" [%s]", node.componentPath()));
    }
  }

  private final Formatter<DependencyEdge> entryPointFormatter =
      new Formatter<DependencyEdge>() {
        @Override
        public String format(DependencyEdge object) {
          XElement requestElement = object.dependencyRequest().requestElement().get().xprocessing();
          StringBuilder builder = new StringBuilder(elementToString(requestElement));

          // For entry points declared in subcomponents or supertypes of the root component,
          // append the component path to make clear to the user which component it's in.
          ComponentPath componentPath = source(object).componentPath();
          if (!componentPath.atRoot()
              || !requestElement
                  .getEnclosingElement()
                  .equals(componentPath.rootComponent().xprocessing())) {
            builder.append(String.format(" [%s]", componentPath));
          }
          return builder.toString();
        }
      };

  private boolean isTracedRequest(
      ImmutableList<DependencyEdge> dependencyTrace, DependencyEdge request) {
    return !dependencyTrace.isEmpty()
        && request.dependencyRequest().equals(dependencyTrace.get(0).dependencyRequest())
        // Comparing the dependency request is not enough since the request is just the key.
        // Instead, we check that the target incident node is the same.
        && graph.network().incidentNodes(request).target()
            .equals(graph.network().incidentNodes(dependencyTrace.get(0)).target());
  }

  /**
   * Returns the dependency trace from one of the {@code entryPoints} to {@code binding} to {@code
   * message} as a list <i>ending with</i> the entry point.
   */
  // TODO(ronshapiro): Adding a DependencyPath type to dagger.internal.codegen.model could be
  // useful, i.e.
  // bindingGraph.shortestPathFromEntryPoint(DependencyEdge, MaybeBindingNode)
  public ImmutableList<DependencyEdge> dependencyTrace(
      MaybeBinding binding, ImmutableSet<DependencyEdge> entryPoints) {
    // Module binding graphs may have bindings unreachable from any entry points. If there are
    // no entry points for this DiagnosticInfo, don't try to print a dependency trace.
    if (entryPoints.isEmpty()) {
      return ImmutableList.of();
    }
    // Show the full dependency trace for one entry point.
    DependencyEdge entryPointForTrace =
        min(
            entryPoints,
            // prefer entry points in components closest to the root
            rootComponentFirst()
                // then prefer entry points with a short dependency path to the error
                .thenComparing(shortestDependencyPathFirst(binding))
                // then prefer entry points declared in the component to those declared in a
                // supertype
                .thenComparing(nearestComponentSupertypeFirst())
                // finally prefer entry points declared first in their enclosing type
                .thenComparing(requestElementDeclarationOrder()));

    ImmutableList<Node> shortestBindingPath =
        shortestPathFromEntryPoint(entryPointForTrace, binding);
    verify(
        !shortestBindingPath.isEmpty(),
        "no dependency path from %s to %s in %s",
        entryPointForTrace,
        binding,
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

  /** Returns all the nonsynthetic dependency requests for a binding. */
  public ImmutableSet<DependencyEdge> requests(MaybeBinding binding) {
    return graph.network().inEdges(binding).stream()
        .flatMap(instancesOf(DependencyEdge.class))
        .filter(edge -> edge.dependencyRequest().requestElement().isPresent())
        .sorted(requestEnclosingTypeName().thenComparing(requestElementDeclarationOrder()))
        .collect(toImmutableSet());
  }

  /**
   * Returns a comparator that sorts entry points in components whose paths from the root are
   * shorter first.
   */
  private Comparator<DependencyEdge> rootComponentFirst() {
    return comparingInt(entryPoint -> source(entryPoint).componentPath().components().size());
  }

  /**
   * Returns a comparator that puts entry points whose shortest dependency path to {@code binding}
   * is shortest first.
   */
  private Comparator<DependencyEdge> shortestDependencyPathFirst(MaybeBinding binding) {
    return comparing(entryPoint -> shortestPathFromEntryPoint(entryPoint, binding).size());
  }

  private ImmutableList<Node> shortestPathFromEntryPoint(
      DependencyEdge entryPoint, MaybeBinding binding) {
    return shortestPaths
        .row(binding)
        .computeIfAbsent(
            entryPoint,
            ep ->
                shortestPath(
                    node ->
                        filter(graph.network().successors(node), MaybeBinding.class::isInstance),
                    graph.network().incidentNodes(ep).target(),
                    binding));
  }

  /**
   * Returns a comparator that sorts entry points in by the distance of the type that declares them
   * from the type of the component that contains them.
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

  private XTypeElement componentContainingEntryPoint(DependencyEdge entryPoint) {
    return source(entryPoint).componentPath().currentComponent().xprocessing();
  }

  private XTypeElement typeDeclaringEntryPoint(DependencyEdge entryPoint) {
    return asTypeElement(
        entryPoint.dependencyRequest().requestElement().get().xprocessing().getEnclosingElement());
  }

  /**
   * Returns a comparator that sorts dependency edges lexicographically by the qualified name of the
   * type that contains them. Only appropriate for edges with request elements.
   */
  private Comparator<DependencyEdge> requestEnclosingTypeName() {
    return comparing(
        edge ->
            closestEnclosingTypeElement(
                    edge.dependencyRequest().requestElement().get().xprocessing())
                .getQualifiedName());
  }

  /**
   * Returns a comparator that sorts edges in the order in which their request elements were
   * declared in their declaring type.
   *
   * <p>Only useful to compare edges whose request elements were declared in the same type.
   */
  private Comparator<DependencyEdge> requestElementDeclarationOrder() {
    return comparing(
        edge -> edge.dependencyRequest().requestElement().get().xprocessing(),
        // TODO(bcorso): This is inefficient as it requires each element to iterate through all of
        // its siblings to find its order. Ideally, the order of all elements would be calculated in
        // a single pass and cached, but the organization of the current code makes that a bit
        // difficult. I'm leaving this for now since this is only called on failures.
        comparing(
            element -> {
              XElement enclosingElement = element.getEnclosingElement();
              checkState(isTypeElement(enclosingElement) || isExecutable(enclosingElement));
              List<? extends XElement> siblings =
                  isTypeElement(enclosingElement)
                      ? asTypeElement(enclosingElement).getEnclosedElements()
                      // For parameter elements, element.getEnclosingElement().getEnclosedElements()
                      // is empty, so instead look at the parameter list of the enclosing executable
                      : asExecutable(enclosingElement).getParameters();
              return siblings.indexOf(element);
            }));
  }

  private Node source(Edge edge) {
    return graph.network().incidentNodes(edge).source();
  }
}

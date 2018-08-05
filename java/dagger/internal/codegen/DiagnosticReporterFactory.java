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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Lists.asList;
import static dagger.internal.codegen.DaggerElements.elementEncloses;
import static dagger.internal.codegen.DaggerElements.elementToString;
import static dagger.internal.codegen.DaggerGraphs.shortestPath;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.FormatMethod;
import dagger.model.BindingGraph;
import dagger.model.BindingGraph.BindingNode;
import dagger.model.BindingGraph.ChildFactoryMethodEdge;
import dagger.model.BindingGraph.ComponentNode;
import dagger.model.BindingGraph.DependencyEdge;
import dagger.model.BindingGraph.Edge;
import dagger.model.BindingGraph.Node;
import dagger.model.DependencyRequest;
import dagger.spi.BindingGraphPlugin;
import dagger.spi.DiagnosticReporter;
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
  private final Messager messager;
  private final DependencyRequestFormatter dependencyRequestFormatter;

  @Inject
  DiagnosticReporterFactory(
      Messager messager, DependencyRequestFormatter dependencyRequestFormatter) {
    this.messager = messager;
    this.dependencyRequestFormatter = dependencyRequestFormatter;

  }

  /** Creates a reporter for a binding graph and a plugin. */
  DiagnosticReporterImpl reporter(BindingGraph graph, BindingGraphPlugin plugin) {
    return new DiagnosticReporterImpl(graph, plugin.pluginName());
  }

  /**
   * A {@link DiagnosticReporter} that keeps track of which {@linkplain Diagnostic.Kind kinds} of
   * diagnostics were reported.
   */
  final class DiagnosticReporterImpl implements DiagnosticReporter {
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
      if (!componentNode.componentPath().currentComponent().equals(rootComponent)) {
        appendComponentPath(messageBuilder, componentNode);
      }
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

    @Override
    public void reportBinding(
        Diagnostic.Kind diagnosticKind, BindingNode bindingNode, String message) {
      // TODO(ronshapiro): should this also include the binding element?
      reportAtEntryPointsWithDependencyTrace(
          diagnosticKind, new StringBuilder(message), bindingNode);
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
      dependencyRequestFormatter.appendFormatLine(
          messageBuilder, dependencyEdge.dependencyRequest());

      if (dependencyEdge.isEntryPoint()) {
        printAtEntryPoint(diagnosticKind, messageBuilder, dependencyEdge);
      } else {
        BindingNode sourceNode = (BindingNode) graph.incidentNodes(dependencyEdge).source();
        reportAtEntryPointsWithDependencyTrace(diagnosticKind, messageBuilder, sourceNode);
      }
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
     * For each entry point that depends on {@code targetNode}, appends the {@link
     * #dependencyTrace(DependencyEdge, BindingNode)} to the binding onto {@code message} and prints
     * to the messager.
     */
    private void reportAtEntryPointsWithDependencyTrace(
        Diagnostic.Kind diagnosticKind, CharSequence message, BindingNode bindingNode) {
      for (DependencyEdge entryPoint : graph.entryPointEdgesDependingOnBindingNode(bindingNode)) {
        printAtEntryPoint(
            diagnosticKind,
            new StringBuilder(message).append(dependencyTrace(entryPoint, bindingNode)),
            entryPoint);
      }
    }

    // TODO(ronshapiro): Adding a DependencyPath type to dagger.model could be useful, i.e.
    // bindingGraph.shortestPathFromEntryPoint(DependencyEdge, BindingNode)
    private CharSequence dependencyTrace(DependencyEdge entryPoint, BindingNode bindingNode) {
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

      StringBuilder trace =
          new StringBuilder(shortestBindingPath.size() * 100 /* a guess heuristic */);
      for (int i = shortestBindingPath.size() - 1; i > 0; i--) {
        Set<Edge> dependenciesBetween =
            graph.edgesConnecting(shortestBindingPath.get(i - 1), shortestBindingPath.get(i));
        DependencyRequest dependencyRequest =
            // If a binding requests a key more than once, any of them should be fine to get to
            // the shortest path
            ((DependencyEdge) Iterables.get(dependenciesBetween, 0)).dependencyRequest();
        dependencyRequestFormatter.appendFormatLine(trace, dependencyRequest);
      }
      dependencyRequestFormatter.appendFormatLine(trace, entryPoint.dependencyRequest());
      return trace;
    }

    /**
     * Prints {@code message} at {@code entryPoint}'s element if it is defined in the {@code
     * rootComponent}, otherwise at the root component.
     */
    private void printAtEntryPoint(
        Diagnostic.Kind diagnosticKind, CharSequence message, DependencyEdge entryPoint) {
      checkArgument(entryPoint.isEntryPoint());
      Element entryPointElement = entryPoint.dependencyRequest().requestElement().get();

      StringBuilder messageBuilder = new StringBuilder(message);
      Node component = graph.incidentNodes(entryPoint).source();
      if (!component.equals(graph.rootComponentNode())) {
        appendComponentPath(messageBuilder, component);
      }
      printMessage(diagnosticKind, messageBuilder, entryPointElement);
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

    @CanIgnoreReturnValue
    private StringBuilder appendComponentPath(StringBuilder message, Node node) {
      return message.append("\ncomponent path: ").append(node.componentPath());
    }

    @CanIgnoreReturnValue
    private StringBuilder insertBracketPrefix(StringBuilder messageBuilder, String prefix) {
      return messageBuilder.insert(0, String.format("[%s] ", prefix));
    }
  }
}

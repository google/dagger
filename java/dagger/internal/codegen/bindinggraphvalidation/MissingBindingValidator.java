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

package dagger.internal.codegen.bindinggraphvalidation;

import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Iterables.getLast;
import static dagger.internal.codegen.base.Keys.isValidImplicitProvisionKey;
import static dagger.internal.codegen.base.Keys.isValidMembersInjectionKey;
import static dagger.internal.codegen.base.RequestKinds.canBeSatisfiedByProductionBinding;
import static dagger.internal.codegen.binding.DependencyRequestFormatter.DOUBLE_INDENT;
import static dagger.internal.codegen.extension.DaggerStreams.instancesOf;
import static dagger.internal.codegen.xprocessing.XTypes.isWildcard;
import static javax.tools.Diagnostic.Kind.ERROR;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.binding.DependencyRequestFormatter;
import dagger.internal.codegen.binding.InjectBindingRegistry;
import dagger.internal.codegen.validation.DiagnosticMessageGenerator;
import dagger.spi.model.Binding;
import dagger.spi.model.BindingGraph;
import dagger.spi.model.BindingGraph.ComponentNode;
import dagger.spi.model.BindingGraph.DependencyEdge;
import dagger.spi.model.BindingGraph.Edge;
import dagger.spi.model.BindingGraph.MissingBinding;
import dagger.spi.model.BindingGraph.Node;
import dagger.spi.model.BindingGraphPlugin;
import dagger.spi.model.ComponentPath;
import dagger.spi.model.DiagnosticReporter;
import dagger.spi.model.Key;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;

/** Reports errors for missing bindings. */
final class MissingBindingValidator implements BindingGraphPlugin {

  private final InjectBindingRegistry injectBindingRegistry;
  private final DependencyRequestFormatter dependencyRequestFormatter;
  private final DiagnosticMessageGenerator.Factory diagnosticMessageGeneratorFactory;

  @Inject
  MissingBindingValidator(
      InjectBindingRegistry injectBindingRegistry,
      DependencyRequestFormatter dependencyRequestFormatter,
      DiagnosticMessageGenerator.Factory diagnosticMessageGeneratorFactory) {
    this.injectBindingRegistry = injectBindingRegistry;
    this.dependencyRequestFormatter = dependencyRequestFormatter;
    this.diagnosticMessageGeneratorFactory = diagnosticMessageGeneratorFactory;
  }

  @Override
  public String pluginName() {
    return "Dagger/MissingBinding";
  }

  @Override
  public void visitGraph(BindingGraph graph, DiagnosticReporter diagnosticReporter) {
    // Don't report missing bindings when validating a full binding graph or a graph built from a
    // subcomponent.
    if (graph.isFullBindingGraph() || graph.rootComponentNode().isSubcomponent()) {
      return;
    }
    graph
        .missingBindings()
        .forEach(missingBinding -> reportMissingBinding(missingBinding, graph, diagnosticReporter));
  }

  private void reportMissingBinding(
      MissingBinding missingBinding, BindingGraph graph, DiagnosticReporter diagnosticReporter) {
    List<ComponentPath> alternativeComponents =
        graph.bindings(missingBinding.key()).stream()
            .map(Binding::componentPath)
            .distinct()
            .collect(Collectors.toList());
    // Print component name for each binding along the dependency path if the missing binding
    // exists in a different component than expected
    if (alternativeComponents.isEmpty()) {
      diagnosticReporter.reportBinding(
          ERROR, missingBinding, missingBindingErrorMessage(missingBinding, graph));
    } else {
      diagnosticReporter.reportComponent(
          ERROR,
          graph.componentNode(missingBinding.componentPath()).get(),
          missingBindingErrorMessage(missingBinding, graph)
              + wrongComponentErrorMessage(missingBinding, alternativeComponents, graph));
    }
  }

  private String missingBindingErrorMessage(MissingBinding missingBinding, BindingGraph graph) {
    Key key = missingBinding.key();
    StringBuilder errorMessage = new StringBuilder();
    // Wildcards should have already been checked by DependencyRequestValidator.
    verify(!isWildcard(key.type().xprocessing()), "unexpected wildcard request: %s", key);
    // TODO(ronshapiro): replace "provided" with "satisfied"?
    errorMessage.append(key).append(" cannot be provided without ");
    if (isValidImplicitProvisionKey(key)) {
      errorMessage.append("an @Inject constructor or ");
    }
    errorMessage.append("an @Provides-"); // TODO(dpb): s/an/a
    if (allIncomingDependenciesCanUseProduction(missingBinding, graph)) {
      errorMessage.append(" or @Produces-");
    }
    errorMessage.append("annotated method.");
    if (isValidMembersInjectionKey(key) && typeHasInjectionSites(key)) {
      errorMessage.append(
          " This type supports members injection but cannot be implicitly provided.");
    }
    return errorMessage.toString();
  }

  private String wrongComponentErrorMessage(
      MissingBinding missingBinding,
      List<ComponentPath> alternativeComponentPath,
      BindingGraph graph) {
    ImmutableSet<DependencyEdge> entryPoints =
        graph.entryPointEdgesDependingOnBinding(missingBinding);
    DiagnosticMessageGenerator generator = diagnosticMessageGeneratorFactory.create(graph);
    ImmutableList<DependencyEdge> dependencyTrace =
        generator.dependencyTrace(missingBinding, entryPoints);
    StringBuilder message =
        graph.isFullBindingGraph()
            ? new StringBuilder()
            : new StringBuilder(dependencyTrace.size() * 100 /* a guess heuristic */);
    // Check in which component the missing binding is requested. This can be different from the
    // component the missing binding is in because we'll try to search up the parent components for
    // a binding which makes missing bindings end up at the root component. This is different from
    // the place we are logically requesting the binding from. Note that this is related to the
    // particular dependency trace being shown and so is not necessarily stable.
    String missingComponentName =
        getComponentFromDependencyEdge(dependencyTrace.get(0), graph, false);
    boolean hasSameComponentName = false;
    for (ComponentPath component : alternativeComponentPath) {
      message.append("\nA binding for ").append(missingBinding.key()).append(" exists in ");
      String currentComponentName = component.currentComponent().className().canonicalName();
      if (currentComponentName.contentEquals(missingComponentName)) {
        hasSameComponentName = true;
        message.append("[").append(component).append("]");
      } else {
        message.append(currentComponentName);
      }
      message.append(":");
    }
    for (DependencyEdge edge : dependencyTrace) {
      String line = dependencyRequestFormatter.format(edge.dependencyRequest());
      if (line.isEmpty()) {
        continue;
      }
      // If we ran into a rare case where the component names collide and we need to show the full
      // path, only show the full path for the first dependency request. This is guaranteed to be
      // the component in question since the logic for checking for a collision uses the first
      // edge in the trace. Do not expand subsequent component paths to reduce spam.
      String componentName =
          String.format("[%s] ", getComponentFromDependencyEdge(edge, graph, hasSameComponentName));
      hasSameComponentName = false;
      message.append("\n").append(line.replace(DOUBLE_INDENT, DOUBLE_INDENT + componentName));
    }
    if (!dependencyTrace.isEmpty()) {
      generator.appendComponentPathUnlessAtRoot(message, source(getLast(dependencyTrace), graph));
    }
    message.append(
        generator.getRequestsNotInTrace(
            dependencyTrace, generator.requests(missingBinding), entryPoints));
    return message.toString();
  }

  private boolean allIncomingDependenciesCanUseProduction(
      MissingBinding missingBinding, BindingGraph graph) {
    return graph.network().inEdges(missingBinding).stream()
        .flatMap(instancesOf(DependencyEdge.class))
        .allMatch(edge -> dependencyCanBeProduction(edge, graph));
  }

  // TODO(ronshapiro): merge with
  // ProvisionDependencyOnProduerBindingValidator.dependencyCanUseProduction
  private boolean dependencyCanBeProduction(DependencyEdge edge, BindingGraph graph) {
    Node source = graph.network().incidentNodes(edge).source();
    if (source instanceof ComponentNode) {
      return canBeSatisfiedByProductionBinding(edge.dependencyRequest().kind());
    }
    if (source instanceof dagger.spi.model.Binding) {
      return ((dagger.spi.model.Binding) source).isProduction();
    }
    throw new IllegalArgumentException(
        "expected a dagger.spi.model.Binding or ComponentNode: " + source);
  }

  private boolean typeHasInjectionSites(Key key) {
    return injectBindingRegistry
        .getOrFindMembersInjectionBinding(key)
        .map(binding -> !binding.injectionSites().isEmpty())
        .orElse(false);
  }

  private static String getComponentFromDependencyEdge(
      DependencyEdge edge, BindingGraph graph, boolean completePath) {
    ComponentPath componentPath = graph.network().incidentNodes(edge).source().componentPath();
    return completePath
        ? componentPath.toString()
        : componentPath.currentComponent().className().canonicalName();
  }

  private Node source(Edge edge, BindingGraph graph) {
    return graph.network().incidentNodes(edge).source();
  }
}

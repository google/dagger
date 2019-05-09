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

import static com.google.common.base.Verify.verify;
import static dagger.internal.codegen.DaggerStreams.instancesOf;
import static dagger.internal.codegen.Keys.isValidImplicitProvisionKey;
import static dagger.internal.codegen.Keys.isValidMembersInjectionKey;
import static dagger.internal.codegen.RequestKinds.canBeSatisfiedByProductionBinding;
import static javax.tools.Diagnostic.Kind.ERROR;

import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.BindingGraph;
import dagger.model.BindingGraph.ComponentNode;
import dagger.model.BindingGraph.DependencyEdge;
import dagger.model.BindingGraph.MissingBinding;
import dagger.model.BindingGraph.Node;
import dagger.model.Key;
import dagger.spi.BindingGraphPlugin;
import dagger.spi.DiagnosticReporter;
import javax.inject.Inject;
import javax.lang.model.type.TypeKind;

/** Reports errors for missing bindings. */
final class MissingBindingValidator implements BindingGraphPlugin {

  private final DaggerTypes types;
  private final InjectBindingRegistry injectBindingRegistry;

  @Inject
  MissingBindingValidator(
      DaggerTypes types, InjectBindingRegistry injectBindingRegistry) {
    this.types = types;
    this.injectBindingRegistry = injectBindingRegistry;
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
    diagnosticReporter.reportBinding(
        ERROR, missingBinding, missingBindingErrorMessage(missingBinding, graph));
  }

  private String missingBindingErrorMessage(MissingBinding missingBinding, BindingGraph graph) {
    Key key = missingBinding.key();
    StringBuilder errorMessage = new StringBuilder();
    // Wildcards should have already been checked by DependencyRequestValidator.
    verify(!key.type().getKind().equals(TypeKind.WILDCARD), "unexpected wildcard request: %s", key);
    // TODO(ronshapiro): replace "provided" with "satisfied"?
    errorMessage.append(key).append(" cannot be provided without ");
    if (isValidImplicitProvisionKey(key, types)) {
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
    graph.bindings(key).stream()
        .map(binding -> binding.componentPath().currentComponent())
        .distinct()
        .forEach(
            component ->
                errorMessage
                    .append("\nA binding with matching key exists in component: ")
                    .append(component.getQualifiedName()));
    return errorMessage.toString();
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
    if (source instanceof dagger.model.Binding) {
      return ((dagger.model.Binding) source).isProduction();
    }
    throw new IllegalArgumentException(
        "expected a dagger.model.Binding or ComponentNode: " + source);
  }

  private boolean typeHasInjectionSites(Key key) {
    return injectBindingRegistry
        .getOrFindMembersInjectionBinding(key)
        .map(binding -> !binding.injectionSites().isEmpty())
        .orElse(false);
  }
}

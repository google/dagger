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
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.DaggerStreams.instancesOf;
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.DaggerStreams.toImmutableSetMultimap;
import static dagger.internal.codegen.DuplicateBindingsValidation.SourceAndRequest.indexEdgesBySourceAndRequest;
import static dagger.internal.codegen.Formatter.INDENT;
import static dagger.internal.codegen.Optionals.emptiesLast;
import static java.util.Comparator.comparing;
import static javax.tools.Diagnostic.Kind.ERROR;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import dagger.model.BindingGraph;
import dagger.model.BindingGraph.BindingNode;
import dagger.model.BindingGraph.DependencyEdge;
import dagger.model.BindingGraph.Node;
import dagger.model.DependencyRequest;
import dagger.spi.BindingGraphPlugin;
import dagger.spi.DiagnosticReporter;
import java.util.Comparator;
import java.util.Set;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/** Reports errors for conflicting bindings with the same key. */
final class DuplicateBindingsValidation implements BindingGraphPlugin {

  // 1. contributing module or enclosing type
  // 2. binding element's simple name
  // 3. binding element's type
  private static final Comparator<BindingDeclaration> BINDING_DECLARATION_COMPARATOR =
      comparing(
              (BindingDeclaration declaration) ->
                  declaration.contributingModule().isPresent()
                      ? declaration.contributingModule()
                      : declaration.bindingTypeElement(),
              emptiesLast(comparing((TypeElement type) -> type.getQualifiedName().toString())))
          .thenComparing(
              (BindingDeclaration declaration) -> declaration.bindingElement(),
              emptiesLast(
                  comparing((Element element) -> element.getSimpleName().toString())
                      .thenComparing((Element element) -> element.asType().toString())));

  private final BindingDeclarationFormatter bindingDeclarationFormatter;

  @Inject
  DuplicateBindingsValidation(BindingDeclarationFormatter bindingDeclarationFormatter) {
    this.bindingDeclarationFormatter = bindingDeclarationFormatter;
  }

  @Override
  public String pluginName() {
    return "Dagger/DuplicateBindings";
  }

  @Override
  public void visitGraph(BindingGraph bindingGraph, DiagnosticReporter diagnosticReporter) {
    Multimaps.asMap(indexEdgesBySourceAndRequest(bindingGraph))
        .forEach(
            (sourceAndRequest, dependencyEdges) -> {
              if (dependencyEdges.size() > 1) {
                reportDuplicateBindings(
                    sourceAndRequest.request(), dependencyEdges, bindingGraph, diagnosticReporter);
              }
            });
  }

  private void reportDuplicateBindings(
      DependencyRequest dependencyRequest,
      Set<DependencyEdge> duplicateDependencies,
      BindingGraph bindingGraph,
      DiagnosticReporter diagnosticReporter) {
    ImmutableSet<BindingNode> duplicateBindings =
        duplicateDependencies
            .stream()
            .map(edge -> bindingGraph.incidentNodes(edge).target())
            .flatMap(instancesOf(BindingNode.class))
            .collect(toImmutableSet());
    diagnosticReporter.reportDependency(
        ERROR,
        Iterables.get(duplicateDependencies, 0),
        Iterables.any(duplicateBindings, node -> node.binding().kind().isMultibinding())
            ? incompatibleBindingsMessage(dependencyRequest, duplicateBindings, bindingGraph)
            : duplicateBindingMessage(dependencyRequest, duplicateBindings, bindingGraph));
  }

  private String duplicateBindingMessage(
      DependencyRequest dependencyRequest,
      ImmutableSet<BindingNode> duplicateBindings,
      BindingGraph graph) {
    StringBuilder message =
        new StringBuilder().append(dependencyRequest.key()).append(" is bound multiple times:");
    formatDeclarations(message, 1, declarations(graph, duplicateBindings));
    return message.toString();
  }

  private String incompatibleBindingsMessage(
      DependencyRequest dependencyRequest,
      ImmutableSet<BindingNode> duplicateBindings,
      BindingGraph graph) {
    ImmutableSet<BindingNode> multibindings =
        duplicateBindings
            .stream()
            .filter(node -> node.binding().kind().isMultibinding())
            .collect(toImmutableSet());
    verify(
        multibindings.size() == 1,
        "expected only one multibinding for %s: %s",
        dependencyRequest,
        multibindings);
    StringBuilder message = new StringBuilder();
    java.util.Formatter messageFormatter = new java.util.Formatter(message);
    messageFormatter.format(
        "%s has incompatible bindings or declarations:\n", dependencyRequest.key());
    message.append(INDENT);
    BindingNode multibinding = getOnlyElement(multibindings);
    messageFormatter.format("%s bindings and declarations:", multibindingTypeString(multibinding));
    formatDeclarations(message, 2, declarations(graph, multibindings));

    Set<BindingNode> uniqueBindings =
        Sets.filter(duplicateBindings, binding -> !binding.equals(multibinding));
    message.append('\n').append(INDENT).append("Unique bindings and declarations:");
    formatDeclarations(
        message,
        2,
        Sets.filter(
            declarations(graph, uniqueBindings),
            declaration -> !(declaration instanceof MultibindingDeclaration)));
    return message.toString();
  }

  private void formatDeclarations(
      StringBuilder builder,
      int indentLevel,
      Iterable<? extends BindingDeclaration> bindingDeclarations) {
    bindingDeclarationFormatter.formatIndentedList(
        builder, ImmutableList.copyOf(bindingDeclarations), indentLevel);
  }

  private ImmutableSet<BindingDeclaration> declarations(
      BindingGraph graph, Set<BindingNode> bindings) {
    return bindings
        .stream()
        .flatMap(node -> declarations(graph, node).stream())
        .distinct()
        .sorted(BINDING_DECLARATION_COMPARATOR)
        .collect(toImmutableSet());
  }

  private ImmutableSet<BindingDeclaration> declarations(BindingGraph graph, BindingNode node) {
    ImmutableSet.Builder<BindingDeclaration> declarations = ImmutableSet.builder();
    ((BindingNodeImpl) node).associatedDeclarations().forEach(declarations::add);
    if (node.binding() instanceof BindingDeclaration) {
      BindingDeclaration declaration = ((BindingDeclaration) node.binding());
      if (bindingDeclarationFormatter.canFormat(declaration)) {
        declarations.add(declaration);
      } else {
        graph
            .successors(node)
            .stream()
            .flatMap(instancesOf(BindingNode.class))
            .flatMap(dependency -> declarations(graph, dependency).stream())
            .forEach(declarations::add);
      }
    }
    return declarations.build();
  }

  private String multibindingTypeString(BindingNode multibinding) {
    switch (multibinding.binding().kind()) {
      case MULTIBOUND_MAP:
        return "Map";
      case MULTIBOUND_SET:
        return "Set";
      default:
        throw new AssertionError(multibinding);
    }
  }

  @AutoValue
  abstract static class SourceAndRequest {

    abstract Node source();

    abstract DependencyRequest request();

    static ImmutableSetMultimap<SourceAndRequest, DependencyEdge> indexEdgesBySourceAndRequest(
        BindingGraph bindingGraph) {
      return bindingGraph
          .dependencyEdges()
          .stream()
          .collect(
              toImmutableSetMultimap(
                  edge ->
                      create(bindingGraph.incidentNodes(edge).source(), edge.dependencyRequest()),
                  edge -> edge));
    }

    static SourceAndRequest create(Node source, DependencyRequest request) {
      return new AutoValue_DuplicateBindingsValidation_SourceAndRequest(source, request);
    }
  }
}

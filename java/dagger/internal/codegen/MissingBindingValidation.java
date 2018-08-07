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

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.auto.common.MoreTypes.asTypeElement;
import static com.google.auto.common.MoreTypes.isType;
import static com.google.auto.common.MoreTypes.isTypeOf;
import static com.google.common.base.Verify.verify;
import static dagger.internal.codegen.DaggerElements.isAnnotationPresent;
import static dagger.internal.codegen.DaggerStreams.instancesOf;
import static dagger.internal.codegen.Keys.isValidImplicitProvisionKey;
import static dagger.internal.codegen.Keys.isValidMembersInjectionKey;
import static dagger.internal.codegen.MoreAnnotationMirrors.getTypeValue;
import static dagger.internal.codegen.RequestKinds.entryPointCanUseProduction;
import static dagger.internal.codegen.Scopes.getReadableSource;
import static java.util.function.Predicate.isEqual;
import static java.util.stream.Collectors.toList;
import static javax.tools.Diagnostic.Kind.ERROR;

import com.google.auto.common.MoreTypes;
import dagger.model.BindingGraph;
import dagger.model.BindingGraph.BindingNode;
import dagger.model.BindingGraph.ComponentNode;
import dagger.model.BindingGraph.DependencyEdge;
import dagger.model.BindingGraph.Node;
import dagger.model.Key;
import dagger.model.Scope;
import dagger.releasablereferences.CanReleaseReferences;
import dagger.releasablereferences.ForReleasableReferences;
import dagger.releasablereferences.ReleasableReferenceManager;
import dagger.releasablereferences.TypedReleasableReferenceManager;
import dagger.spi.BindingGraphPlugin;
import dagger.spi.DiagnosticReporter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/** Reports errors for missing bindings. */
final class MissingBindingValidation implements BindingGraphPlugin {

  private final DaggerTypes types;
  private final DaggerElements elements;
  private final InjectBindingRegistry injectBindingRegistry;

  @Inject
  MissingBindingValidation(
      DaggerTypes types, DaggerElements elements, InjectBindingRegistry injectBindingRegistry) {
    this.types = types;
    this.injectBindingRegistry = injectBindingRegistry;
    this.elements = elements;
  }

  @Override
  public String pluginName() {
    return "Dagger/MissingBinding";
  }

  @Override
  public void visitGraph(BindingGraph graph, DiagnosticReporter diagnosticReporter) {
    // TODO(ronshapiro): Maybe report each missing binding once instead of each dependency.
    graph
        .missingBindingNodes()
        .stream()
        .flatMap(node -> graph.inEdges(node).stream())
        .flatMap(instancesOf(DependencyEdge.class))
        .forEach(edge -> reportMissingBinding(edge, graph, diagnosticReporter));
  }

  private void reportMissingBinding(
      DependencyEdge edge, BindingGraph graph, DiagnosticReporter diagnosticReporter) {
    diagnosticReporter.reportDependency(
        ERROR,
        edge,
        missingReleasableReferenceManagerBindingErrorMessage(edge, graph)
            .orElseGet(() -> missingBindingErrorMessage(edge, graph)));
  }

  private String missingBindingErrorMessage(DependencyEdge edge, BindingGraph graph) {
    Key key = edge.dependencyRequest().key();
    StringBuilder errorMessage = new StringBuilder();
    // Wildcards should have already been checked by DependencyRequestValidator.
    verify(
        !key.type().getKind().equals(TypeKind.WILDCARD), "unexpected wildcard request: %s", edge);
    // TODO(ronshapiro): replace "provided" with "satisfied"?
    errorMessage.append(key).append(" cannot be provided without ");
    if (isValidImplicitProvisionKey(key, types)) {
      errorMessage.append("an @Inject constructor or ");
    }
    errorMessage.append("an @Provides-"); // TODO(dpb): s/an/a
    if (dependencyCanBeProduction(edge, graph)) {
      errorMessage.append(" or @Produces-");
    }
    errorMessage.append("annotated method.");
    if (isValidMembersInjectionKey(key) && typeHasInjectionSites(key)) {
      errorMessage.append(
          " This type supports members injection but cannot be implicitly provided.");
    }
    graph
        .bindingNodes(key)
        .stream()
        .map(bindingNode -> bindingNode.componentPath().currentComponent())
        .distinct()
        .forEach(
            component ->
                errorMessage
                    .append("\nA binding with matching key exists in component: ")
                    .append(component.getQualifiedName()));
    return errorMessage.toString();
  }

  private boolean dependencyCanBeProduction(DependencyEdge edge, BindingGraph graph) {
    Node source = graph.incidentNodes(edge).source();
    if (source instanceof ComponentNode) {
      return entryPointCanUseProduction(edge.dependencyRequest().kind());
    }
    if (source instanceof BindingNode) {
      return ((BindingNode) source).binding().isProduction();
    }
    throw new IllegalArgumentException("expected a BindingNode or ComponentNode: " + source);
  }

  private boolean typeHasInjectionSites(Key key) {
    return injectBindingRegistry
        .getOrFindMembersInjectionBinding(key)
        .map(binding -> !binding.injectionSites().isEmpty())
        .orElse(false);
  }

  /**
   * If {@code edge} is missing a binding because it's an invalid {@code @ForReleasableReferences}
   * request, returns a more specific error message.
   *
   * <p>An invalid request is one whose type is either {@link ReleasableReferenceManager} or {@link
   * TypedReleasableReferenceManager}, and whose scope:
   *
   * <ul>
   *   <li>does not annotate any component in the hierarchy, or
   *   <li>is not annotated with the metadata annotation type that is the {@link
   *       TypedReleasableReferenceManager}'s type argument
   * </ul>
   */
  private Optional<String> missingReleasableReferenceManagerBindingErrorMessage(
      DependencyEdge edge, BindingGraph graph) {
    Key key = edge.dependencyRequest().key();
    if (!key.qualifier().isPresent()
        || !isTypeOf(ForReleasableReferences.class, key.qualifier().get().getAnnotationType())
        || !isType(key.type())) {
      return Optional.empty();
    }

    Optional<DeclaredType> metadataType;
    if (isTypeOf(ReleasableReferenceManager.class, key.type())) {
      metadataType = Optional.empty();
    } else if (isTypeOf(TypedReleasableReferenceManager.class, key.type())) {
      List<? extends TypeMirror> typeArguments =
          MoreTypes.asDeclared(key.type()).getTypeArguments();
      if (typeArguments.size() != 1 || !typeArguments.get(0).getKind().equals(TypeKind.DECLARED)) {
        return Optional.empty();
      }
      metadataType = Optional.of(MoreTypes.asDeclared(typeArguments.get(0)));
    } else {
      return Optional.empty();
    }

    Scope scope = Scopes.scope(asTypeElement(getTypeValue(key.qualifier().get(), "value")));
    if (releasableReferencesScopes(graph).noneMatch(isEqual(scope))) {
      return Optional.of(
          String.format(
              "There is no binding for %s because no component in %s's component hierarchy is "
                  + "annotated with %s. The available reference-releasing scopes are %s.",
              key,
              graph.rootComponentNode().componentPath().currentComponent().getQualifiedName(),
              getReadableSource(scope),
              releasableReferencesScopes(graph).map(Scopes::getReadableSource).collect(toList())));
    }
    if (metadataType.isPresent()) {
      TypeElement metadataTypeElement = asTypeElement(metadataType.get());
      if (!isAnnotationPresent(scope.scopeAnnotationElement(), metadataType.get())) {
        return Optional.of(notAnnotated(key, scope.scopeAnnotationElement(), metadataTypeElement));
      }
      if (!isAnnotationPresent(metadataTypeElement, CanReleaseReferences.class)) {
        return Optional.of(
            notAnnotated(
                key, metadataTypeElement, elements.getTypeElement(CanReleaseReferences.class)));
      }
    }
    return Optional.empty();
  }

  private static String notAnnotated(Key key, TypeElement type, TypeElement annotation) {
    return String.format(
        "There is no binding for %s because %s is not annotated with @%s.",
        key, type.getQualifiedName(), annotation);
  }

  private Stream<Scope> releasableReferencesScopes(BindingGraph graph) {
    return graph
        .componentNodes()
        .stream()
        .flatMap(node -> node.scopes().stream())
        .filter(Scope::canReleaseReferences);
  }
}

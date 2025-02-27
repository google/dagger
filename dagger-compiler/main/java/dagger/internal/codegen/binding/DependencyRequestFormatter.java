/*
 * Copyright (C) 2014 The Dagger Authors.
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

package dagger.internal.codegen.binding;

import static androidx.room.compiler.processing.XElementKt.isMethod;
import static androidx.room.compiler.processing.XElementKt.isTypeElement;
import static androidx.room.compiler.processing.XElementKt.isVariableElement;
import static dagger.internal.codegen.base.ElementFormatter.elementToString;
import static dagger.internal.codegen.base.RequestKinds.requestType;
import static java.util.stream.Collectors.joining;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableCollection;
import dagger.internal.codegen.base.Formatter;
import dagger.internal.codegen.model.BindingGraph;
import dagger.internal.codegen.model.BindingGraph.DependencyEdge;
import dagger.internal.codegen.model.BindingGraph.Node;
import dagger.internal.codegen.model.DaggerAnnotation;
import dagger.internal.codegen.model.DependencyRequest;
import dagger.internal.codegen.xprocessing.XTypes;
import java.util.Optional;
import javax.inject.Inject;

/**
 * Formats a {@link DependencyRequest} into a {@link String} suitable for an error message listing a
 * chain of dependencies.
 *
 * <dl>
 *   <dt>For component provision methods
 *   <dd>{@code @Qualifier SomeType is provided at\n ComponentType.method()}
 *   <dt>For component injection methods
 *   <dd>{@code SomeType is injected at\n ComponentType.method(foo)}
 *   <dt>For parameters to {@code @Provides}, {@code @Produces}, or {@code @Inject} methods:
 *   <dd>{@code @Qualified ResolvedType is injected at\n EnclosingType.method([…, ]param[, …])}
 *   <dt>For parameters to {@link Inject @Inject} constructors:
 *   <dd>{@code @Qualified ResolvedType is injected at\n EnclosingType([…, ]param[, …])}
 *   <dt>For {@link Inject @Inject} fields:
 *   <dd>{@code @Qualified ResolvedType is injected at\n EnclosingType.field}
 * </dl>
 */
public final class DependencyRequestFormatter extends Formatter<DependencyRequest> {

  private final XProcessingEnv processingEnv;

  @Inject
  DependencyRequestFormatter(XProcessingEnv processingEnv) {
    this.processingEnv = processingEnv;
  }

  public String formatEdges(ImmutableCollection<DependencyEdge> edges, BindingGraph graph) {
    return edges.stream()
        .map(edge -> formatEdge(edge, graph))
        .filter(line -> !line.isEmpty())
        .collect(joining("\n"));
  }

  public String formatEdge(DependencyEdge edge, BindingGraph graph) {
    Node sourceNode = graph.network().incidentNodes(edge).source();
    XTypeElement sourceComponent = sourceNode.componentPath().currentComponent().xprocessing();
    return format(Optional.of(sourceComponent), edge.dependencyRequest());
  }

  @Override
  public String format(DependencyRequest request) {
    return format(Optional.empty(), request);
  }

  private  String format(Optional<XTypeElement> optionalComponent, DependencyRequest request) {
    if (!request.requestElement().isPresent()) {
      return "";
    }
    XElement requestElement = request.requestElement().get().xprocessing();
    String componentReference =
        optionalComponent
            .map(component -> String.format("[%s] ", component.getQualifiedName()))
            .orElse("");
    if (isMethod(requestElement)) {
      return INDENT
          + request.key()
          + " is "
          + componentMethodRequestVerb(request)
          + " at\n"
          + DOUBLE_INDENT
          + componentReference
          + elementToString(requestElement);
    } else if (isVariableElement(requestElement)) {
      return INDENT
          + formatQualifier(request.key().qualifier())
          + XTypes.toStableString(
              requestType(request.kind(), request.key().type().xprocessing(), processingEnv))
          + " is injected at\n"
          + DOUBLE_INDENT
          + componentReference
          + elementToString(requestElement);
    } else if (isTypeElement(requestElement)) {
      return ""; // types by themselves provide no useful information.
    } else {
      throw new IllegalStateException("Invalid request element " + requestElement);
    }
  }

  private static String formatQualifier(Optional<DaggerAnnotation> maybeQualifier) {
    return maybeQualifier.map(qualifier -> qualifier + " ").orElse("");
  }

  /**
   * Returns the verb for a component method dependency request. Returns "produced", "provided", or
   * "injected", depending on the kind of request.
   */
  private static String componentMethodRequestVerb(DependencyRequest request) {
    switch (request.kind()) {
      case FUTURE:
      case PRODUCER:
      case INSTANCE:
      case LAZY:
      case PROVIDER:
      case PROVIDER_OF_LAZY:
        return "requested";

      case MEMBERS_INJECTION:
        return "injected";

      case PRODUCED:
        break;
    }
    throw new AssertionError("illegal request kind for method: " + request);
  }
}

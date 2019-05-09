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

package dagger.internal.codegen;

import static dagger.internal.codegen.ElementFormatter.elementToString;
import static dagger.internal.codegen.RequestKinds.requestType;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dagger.Provides;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.DependencyRequest;
import dagger.producers.Produces;
import java.util.Optional;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementKindVisitor8;

/**
 * Formats a {@link DependencyRequest} into a {@link String} suitable for an error message listing a
 * chain of dependencies.
 *
 * <dl>
 *   <dt>For component provision methods
 *   <dd>{@code @Qualifier SomeType is provided at\n ComponentType.method()}
 *   <dt>For component injection methods
 *   <dd>{@code SomeType is injected at\n ComponentType.method(foo)}
 *   <dt>For parameters to {@link Provides @Provides}, {@link Produces @Produces}, or {@link
 *       Inject @Inject} methods:
 *   <dd>{@code @Qualified ResolvedType is injected at\n EnclosingType.method([…, ]param[, …])}
 *   <dt>For parameters to {@link Inject @Inject} constructors:
 *   <dd>{@code @Qualified ResolvedType is injected at\n EnclosingType([…, ]param[, …])}
 *   <dt>For {@link Inject @Inject} fields:
 *   <dd>{@code @Qualified ResolvedType is injected at\n EnclosingType.field}
 * </dl>
 */
final class DependencyRequestFormatter extends Formatter<DependencyRequest> {

  private final DaggerTypes types;

  @Inject
  DependencyRequestFormatter(DaggerTypes types) {
    this.types = types;
  }

  @Override
  public String format(DependencyRequest request) {
    return request
        .requestElement()
        .map(element -> element.accept(formatVisitor, request))
        .orElse("");
  }

  /**
   * Appends a newline and the formatted dependency request unless {@link
   * #format(DependencyRequest)} returns the empty string.
   */
  @CanIgnoreReturnValue
  StringBuilder appendFormatLine(StringBuilder builder, DependencyRequest dependencyRequest) {
    String formatted = format(dependencyRequest);
    if (!formatted.isEmpty()) {
      builder.append('\n').append(formatted);
    }
    return builder;
  }

  private final ElementVisitor<String, DependencyRequest> formatVisitor =
      new ElementKindVisitor8<String, DependencyRequest>() {

        @Override
        public String visitExecutableAsMethod(ExecutableElement method, DependencyRequest request) {
          return INDENT
              + request.key()
              + " is "
              + componentMethodRequestVerb(request)
              + " at\n"
              + DOUBLE_INDENT
              + elementToString(method);
        }

        @Override
        public String visitVariable(VariableElement variable, DependencyRequest request) {
          TypeMirror requestedType = requestType(request.kind(), request.key().type(), types);
          return INDENT
              + formatQualifier(request.key().qualifier())
              + requestedType
              + " is injected at\n"
              + DOUBLE_INDENT
              + elementToString(variable);
        }

        @Override
        public String visitType(TypeElement e, DependencyRequest request) {
          return ""; // types by themselves provide no useful information.
        }

        @Override
        protected String defaultAction(Element element, DependencyRequest request) {
          throw new IllegalStateException(
              "Invalid request " + element.getKind() + " element " + element);
        }
      };

  private String formatQualifier(Optional<AnnotationMirror> maybeQualifier) {
    return maybeQualifier.map(qualifier -> qualifier + " ").orElse("");
  }

  /**
   * Returns the verb for a component method dependency request. Returns "produced", "provided", or
   * "injected", depending on the kind of request.
   */
  private String componentMethodRequestVerb(DependencyRequest request) {
    switch (request.kind()) {
      case FUTURE:
      case PRODUCER:
        return "produced";

      case INSTANCE:
      case LAZY:
      case PROVIDER:
      case PROVIDER_OF_LAZY:
        return "provided";

      case MEMBERS_INJECTION:
        return "injected";

      case PRODUCED:
        break;
    }
    throw new AssertionError("illegal request kind for method: " + request);
  }
}

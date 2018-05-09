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

import static dagger.internal.codegen.DaggerElements.elementToString;
import static dagger.internal.codegen.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.RequestKinds.requestType;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import dagger.Provides;
import dagger.internal.codegen.ComponentTreeTraverser.DependencyTrace;
import dagger.model.DependencyRequest;
import dagger.producers.Produces;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
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
 * Formats a {@link DependencyRequest} into a {@link String} suitable for an error message listing
 * a chain of dependencies.
 * 
 * <dl>
 * <dt>For component provision methods
 * <dd>{@code @Qualifier SomeType is provided at\n    ComponentType.method()}
 * 
 * <dt>For component injection methods
 * <dd>{@code SomeType is injected at\n    ComponentType.method(foo)}
 * 
 * <dt>For parameters to {@link Provides @Provides}, {@link Produces @Produces}, or
 * {@link Inject @Inject} methods:
 * <dd>{@code @Qualified ResolvedType is injected at\n    EnclosingType.method([…, ]param[, …])}
 * 
 * <dt>For parameters to {@link Inject @Inject} constructors:
 * <dd>{@code @Qualified ResolvedType is injected at\n    EnclosingType.<init>([…, ]param[, …])}
 * 
 * <dt>For {@link Inject @Inject} fields:
 * <dd>{@code @Qualified ResolvedType is injected at\n    EnclosingType.field}
 * </dl>
 */
final class DependencyRequestFormatter extends Formatter<DependencyRequest> {

  private final DaggerTypes types;

  @Inject
  DependencyRequestFormatter(DaggerTypes types) {
    this.types = types;
  }

  /** Returns a representation of the dependency trace, with the entry point at the bottom. */
  String format(DependencyTrace dependencyTrace) {
    AtomicReference<ImmutableSet<OptionalBindingDeclaration>> dependentOptionalBindingDeclarations =
        new AtomicReference<>(ImmutableSet.of());
    return Joiner.on('\n')
        .join(
            dependencyTrace
                .transform(
                    (dependencyRequest, resolvedBindings) -> {
                      ImmutableSet<OptionalBindingDeclaration> optionalBindingDeclarations =
                          dependentOptionalBindingDeclarations.getAndSet(
                              resolvedBindings.optionalBindingDeclarations());
                      return optionalBindingDeclarations.isEmpty()
                          ? format(dependencyRequest)
                          : formatSyntheticOptionalBindingDependency(optionalBindingDeclarations);
                    })
                .filter(f -> !f.isEmpty())
                .collect(toImmutableList())
                .reverse());
  }

  @Override
  public String format(DependencyRequest request) {
    return request
        .requestElement()
        .map(element -> element.accept(formatVisitor, request))
        .orElse("");
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
      default:
        throw new AssertionError("illegal request kind for method: " + request);
    }
  }

  /**
   * Returns a string of the form "{@code @BindsOptionalOf SomeKey is declared at Module.method()}",
   * where {@code Module.method()} is the declaration. If there is more than one such declaration,
   * one is chosen arbitrarily, and ", among others" is appended.
   */
  private String formatSyntheticOptionalBindingDependency(
      ImmutableSet<OptionalBindingDeclaration> optionalBindingDeclarations) {
    OptionalBindingDeclaration optionalBindingDeclaration =
        optionalBindingDeclarations.iterator().next();
    StringBuilder builder = new StringBuilder();
    builder
        .append(INDENT)
        .append("@BindsOptionalOf ")
        .append(optionalBindingDeclaration.key())
        .append(" is declared at\n")
        .append(DOUBLE_INDENT)
        .append(elementToString(optionalBindingDeclaration.bindingElement().get()));

    if (optionalBindingDeclarations.size() > 1) {
      builder.append(", among others");
    }

    return builder.toString();
  }
}

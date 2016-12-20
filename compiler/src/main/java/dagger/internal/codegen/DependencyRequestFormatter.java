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

import static com.google.auto.common.MoreElements.asExecutable;
import static dagger.internal.codegen.ErrorMessages.DOUBLE_INDENT;
import static dagger.internal.codegen.ErrorMessages.INDENT;
import static dagger.internal.codegen.Util.toImmutableList;

import com.google.auto.common.MoreElements;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dagger.Lazy;
import dagger.Provides;
import dagger.internal.codegen.ComponentTreeTraverser.DependencyTrace;
import dagger.producers.Produces;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementKindVisitor7;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

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

  private final Types types;
  private final Elements elements;

  DependencyRequestFormatter(Types types, Elements elements) {
    this.types = types;
    this.elements = elements;
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

  // TODO(cgruber): Sweep this class for TypeMirror.toString() usage and do some preventive format.
  // TODO(cgruber): consider returning a small structure containing strings to be indented later.
  @Override
  public String format(DependencyRequest request) {
    if (!request.requestElement().isPresent()) {
      return "";
    }
    return request
        .requestElement()
        .get()
        .accept(
            new ElementKindVisitor7<String, DependencyRequest>() {

              /** Returns the description for component methods. */
              @Override
              public String visitExecutableAsMethod(
                  ExecutableElement method, DependencyRequest request) {
                StringBuilder builder = new StringBuilder();
                builder
                    .append(INDENT)
                    .append(formatKey(request.key()))
                    .append(" is ")
                    .append(componentMethodRequestVerb(request))
                    .append(" at\n")
                    .append(DOUBLE_INDENT);
                appendEnclosingTypeAndMemberName(method, builder);
                builder.append('(');
                for (VariableElement parameter : method.getParameters()) {
                  builder.append(parameter.getSimpleName());
                }
                builder.append(')');
                return builder.toString();
              }

              /**
               * Returns the description for {@link javax.inject.Inject @Inject} constructor and
               * method parameters and for {@link dagger.Provides @Provides} and {@link
               * dagger.producers.Produces @Produces} method parameters.
               */
              @Override
              public String visitVariableAsParameter(
                  VariableElement variable, DependencyRequest request) {
                StringBuilder builder = new StringBuilder();
                appendRequestedTypeIsInjectedAt(builder, request);

                ExecutableElement methodOrConstructor =
                    asExecutable(variable.getEnclosingElement());
                appendEnclosingTypeAndMemberName(methodOrConstructor, builder).append('(');
                List<? extends VariableElement> parameters = methodOrConstructor.getParameters();
                int parameterIndex = parameters.indexOf(variable);
                builder.append(
                    formatArgumentInList(
                        parameterIndex, parameters.size(), variable.getSimpleName()));
                builder.append(')');
                return builder.toString();
              }

              /** Returns the description for {@link javax.inject.Inject @Inject} fields. */
              @Override
              public String visitVariableAsField(
                  VariableElement variable, DependencyRequest request) {
                StringBuilder builder = new StringBuilder();
                appendRequestedTypeIsInjectedAt(builder, request);
                appendEnclosingTypeAndMemberName(variable, builder);
                return builder.toString();
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
            },
            request);
  }

  @CanIgnoreReturnValue
  private StringBuilder appendRequestedTypeIsInjectedAt(
      StringBuilder builder, DependencyRequest request) {
    return builder
        .append(INDENT)
        .append(formatKey(request.key().qualifier(), requestedType(request)))
        .append(" is injected at\n")
        .append(DOUBLE_INDENT);
  }

  private TypeMirror requestedType(DependencyRequest request) {
    TypeMirror keyType = request.key().type();
    switch (request.kind()) {
      case FUTURE:
        return wrapType(ListenableFuture.class, keyType);

      case PROVIDER_OF_LAZY:
        return wrapType(Provider.class, wrapType(Lazy.class, keyType));

      default:
        if (request.kind().frameworkClass.isPresent()) {
          return wrapType(request.kind().frameworkClass.get(), keyType);
        } else {
          return keyType;
        }
    }
  }

  private DeclaredType wrapType(Class<?> wrapperType, TypeMirror wrappedType) {
    return types.getDeclaredType(
        elements.getTypeElement(wrapperType.getCanonicalName()), wrappedType);
  }

  private String formatKey(Key key) {
    return formatKey(key.qualifier(), key.type());
  }

  private String formatKey(Optional<AnnotationMirror> qualifier, TypeMirror type) {
    StringBuilder builder = new StringBuilder();
    if (qualifier.isPresent()) {
      builder.append(qualifier.get()).append(' ');
    }
    builder.append(type);
    return builder.toString();
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

      case MEMBERS_INJECTOR:
        return "injected";

      case PRODUCED:
      default:
        throw new AssertionError("illegal request kind for method: " + request);
    }
  }

  @CanIgnoreReturnValue
  private StringBuilder appendEnclosingTypeAndMemberName(Element member, StringBuilder builder) {
    TypeElement type = MoreElements.asType(member.getEnclosingElement());
    return builder
        .append(type.getQualifiedName())
        .append('.')
        .append(member.getSimpleName());
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
        .append(formatKey(optionalBindingDeclaration.key()))
        .append(" is declared at\n")
        .append(DOUBLE_INDENT);

    appendEnclosingTypeAndMemberName(optionalBindingDeclaration.bindingElement().get(), builder);
    builder.append("()");
    if (optionalBindingDeclarations.size() > 1) {
      builder.append(", among others");
    }

    return builder.toString();
  }
}

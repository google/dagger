/*
 * Copyright (C) 2014 Google, Inc.
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

import com.google.auto.common.MoreElements;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dagger.Provides;
import dagger.internal.codegen.BindingGraphValidator.DependencyPath;
import dagger.producers.Produces;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementKindVisitor7;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static com.google.auto.common.MoreElements.asExecutable;
import static dagger.internal.codegen.ErrorMessages.INDENT;

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

  /**
   * A string representation of the dependency trace, starting with the
   * {@linkplain DependencyPath#currentDependencyRequest() current request} and ending with the
   * entry point.
   */
  String toDependencyTrace(DependencyPath dependencyPath) {
    return Joiner.on('\n')
        .join(
            dependencyPath
                .nonsyntheticRequests()
                .transform(this)
                .filter(Predicates.not(Predicates.equalTo("")))
                .toList()
                .reverse());
  }

  // TODO(cgruber): Sweep this class for TypeMirror.toString() usage and do some preventive format.
  // TODO(cgruber): consider returning a small structure containing strings to be indented later.
  @Override
  public String format(DependencyRequest request) {
    return request
        .requestElement()
        .accept(
            new ElementKindVisitor7<String, DependencyRequest>() {

              /** Returns the description for component methods. */
              @Override
              public String visitExecutableAsMethod(
                  ExecutableElement method, DependencyRequest request) {
                StringBuilder builder = new StringBuilder(INDENT);
                appendRequestedKeyAndVerb(
                    builder,
                    request.key().qualifier(),
                    request.key().type(),
                    componentMethodRequestVerb(request));
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
               * method parameters and for {@link dagger.Provides @Provides} and
               * {@link dagger.producers.Produces @Produces} method parameters.
               */
              @Override
              public String visitVariableAsParameter(
                  final VariableElement variable, DependencyRequest request) {
                StringBuilder builder = new StringBuilder(INDENT);
                appendRequestedKeyAndVerb(request, builder);

                ExecutableElement methodOrConstructor =
                    asExecutable(variable.getEnclosingElement());
                appendEnclosingTypeAndMemberName(methodOrConstructor, builder).append('(');
                int parameterIndex = methodOrConstructor.getParameters().indexOf(variable);
                if (parameterIndex > 0) {
                  builder.append("…, ");
                }
                builder.append(variable.getSimpleName());
                if (parameterIndex < methodOrConstructor.getParameters().size() - 1) {
                  builder.append(", …");
                }
                builder.append(')');
                return builder.toString();
              }

              /** Returns the description for {@link javax.inject.Inject @Inject} fields. */
              @Override
              public String visitVariableAsField(
                  VariableElement variable, DependencyRequest request) {
                StringBuilder builder = new StringBuilder(INDENT);
                appendRequestedKeyAndVerb(request, builder);
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

  private void appendRequestedKeyAndVerb(DependencyRequest request, StringBuilder builder) {
    appendRequestedKeyAndVerb(
        builder, request.key().qualifier(), requestedTypeWithFrameworkClass(request), "injected");
  }

  private void appendRequestedKeyAndVerb(
      StringBuilder builder,
      Optional<AnnotationMirror> qualifier,
      TypeMirror requestedType,
      String verb) {
    appendQualifiedType(builder, qualifier, requestedType);
    builder.append(" is ").append(verb).append(" at\n    ").append(INDENT);
  }

  private TypeMirror requestedTypeWithFrameworkClass(DependencyRequest request) {
    Optional<Class<?>> requestFrameworkClass = request.kind().frameworkClass;
    if (requestFrameworkClass.isPresent()) {
      return types.getDeclaredType(
          elements.getTypeElement(requestFrameworkClass.get().getCanonicalName()),
          request.key().type());
    }
    return request.key().type();
  }

  private void appendQualifiedType(
      StringBuilder builder, Optional<AnnotationMirror> qualifier, TypeMirror type) {
    if (qualifier.isPresent()) {
      builder.append(qualifier.get()).append(' ');
    }
    builder.append(type);
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
}

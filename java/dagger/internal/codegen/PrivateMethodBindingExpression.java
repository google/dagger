/*
 * Copyright (C) 2017 The Dagger Authors.
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
import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static javax.lang.model.element.Modifier.PRIVATE;

import com.google.auto.common.MoreTypes;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import java.util.EnumMap;
import java.util.Map;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * A binding expression that wraps the dependency expressions in a private, no-arg method.
 *
 * <p>Dependents of this binding expression will just called the no-arg method.
 */
final class PrivateMethodBindingExpression extends BindingExpression {
  private final ClassName componentName;
  private final GeneratedComponentModel generatedComponentModel;
  private final BindingExpression delegate;
  private final Map<DependencyRequest.Kind, MethodSpec> methods =
      new EnumMap<>(DependencyRequest.Kind.class);
  private final ContributionBinding binding;
  private final Types types;
  private final Elements elements;

  PrivateMethodBindingExpression(
      ResolvedBindings resolvedBindings,
      ClassName componentName,
      GeneratedComponentModel generatedComponentModel,
      BindingExpression delegate,
      Types types,
      Elements elements) {
    super(resolvedBindings);
    this.componentName = componentName;
    this.generatedComponentModel = generatedComponentModel;
    this.delegate = delegate;
    binding = resolvedBindings.contributionBinding();
    this.types = types;
    this.elements = elements;
  }

  @Override
  Expression getDependencyExpression(
      DependencyRequest.Kind requestKind, ClassName requestingClass) {
    // TODO(user): we should just use the component method if one matches instead of creating one.
    switch (requestKind) {
      case INSTANCE:
        MethodSpec method = methods.computeIfAbsent(requestKind, this::createMethod);
        CodeBlock invocation =
            componentName.equals(requestingClass)
                ? CodeBlock.of("$N()", method)
                : CodeBlock.of("$T.this.$N()", componentName, method);
        return Expression.create(returnType(), invocation);
      default:
        return delegate.getDependencyExpression(requestKind, requestingClass);
    }
  }

  /** Creates the no-arg method used for dependency expressions and returns the method's name. */
  private MethodSpec createMethod(DependencyRequest.Kind requestKind) {
    MethodSpec method =
        methodBuilder(generatedComponentModel.getUniqueMethodName(methodName(requestKind)))
            .addModifiers(PRIVATE)
            .returns(TypeName.get(returnType()))
            .addStatement(
                "return $L",
                delegate.getDependencyExpression(requestKind, componentName).codeBlock())
            .build();

    generatedComponentModel.addMethod(method);
    return method;
  }

  private TypeMirror returnType() {
    return binding.contributesPrimitiveType()
        ? asExecutable(binding.bindingElement().get()).getReturnType()
        : accessibleType(binding.contributedType(), componentName);
  }

  /** Returns the canonical name for a no-arg dependency expression method. */
  private String methodName(DependencyRequest.Kind dependencyKind) {
    // TODO(user): Use a better name for @MapKey binding instances.
    return String.format("get%s%s", bindingName(), dependencyKindName(dependencyKind));
  }

  /** Returns the canonical name for the {@link Binding}. */
  private String bindingName() {
    return LOWER_CAMEL.to(UPPER_CAMEL, BindingVariableNamer.name(binding));
  }

  /** Returns a canonical name for the {@link DependencyRequest.Kind}. */
  private static String dependencyKindName(DependencyRequest.Kind kind) {
    return UPPER_UNDERSCORE.to(UPPER_CAMEL, kind.name());
  }

  /** Returns a {@link TypeName} for the binding that is accessible to the component. */
  private TypeMirror accessibleType(TypeMirror typeMirror, ClassName componentName) {
    if (Accessibility.isTypeAccessibleFrom(typeMirror, componentName.packageName())) {
      return typeMirror;
    } else if (Accessibility.isRawTypeAccessible(typeMirror, componentName.packageName())
        && typeMirror.getKind().equals(TypeKind.DECLARED)) {
      return types.getDeclaredType(MoreTypes.asTypeElement(typeMirror));
    } else {
      return elements.getTypeElement(Object.class.getCanonicalName()).asType();
    }
  }
}

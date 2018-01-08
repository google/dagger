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

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static dagger.internal.codegen.GeneratedComponentModel.MethodSpecKind.PRIVATE_METHOD;
import static javax.lang.model.element.Modifier.PRIVATE;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;
import dagger.model.RequestKind;

/**
 * A binding expression that wraps the dependency expressions in a private, no-arg method.
 *
 * <p>Dependents of this binding expression will just call the no-arg private method.
 */
final class PrivateMethodBindingExpression extends BindingExpression {
  private final BindingMethodImplementation methodImplementation;
  private final GeneratedComponentModel generatedComponentModel;
  private String methodName;

  PrivateMethodBindingExpression(
      ResolvedBindings resolvedBindings,
      RequestKind requestKind,
      BindingMethodImplementation methodImplementation,
      GeneratedComponentModel generatedComponentModel) {
    super(resolvedBindings, requestKind);
    this.methodImplementation = checkNotNull(methodImplementation);
    this.generatedComponentModel = checkNotNull(generatedComponentModel);
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    if (methodName == null) {
      // Have to set methodName field before implementing the method in order to handle recursion.
      methodName = generatedComponentModel.getUniqueMethodName(methodName());
      createMethod(methodName, requestingClass);
    }

    // TODO(user): This logic is repeated in multiple places. Can we extract it somewhere?
    ClassName componentName = generatedComponentModel.name();
    CodeBlock invocation =
        componentName.equals(requestingClass)
            ? CodeBlock.of("$N()", methodName)
            : CodeBlock.of("$T.this.$N()", componentName, methodName);
    return Expression.create(methodImplementation.returnType(), invocation);
  }

  /** Creates the no-arg method used for dependency expressions. */
  private void createMethod(String name, ClassName requestingClass) {
    // TODO(user): Consider when we can make this method static.
    // TODO(user): Fix the order that these generated methods are written to the component.
    generatedComponentModel.addMethod(
        PRIVATE_METHOD,
        methodBuilder(name)
            .addModifiers(PRIVATE)
            .returns(TypeName.get(methodImplementation.returnType()))
            .addCode(methodImplementation.body(requestingClass))
            .build());
  }

  /** Returns the canonical name for a no-arg dependency expression method. */
  private String methodName() {
    // TODO(user): Use a better name for @MapKey binding instances.
    // TODO(user): Include the binding method as part of the method name.
    if (requestKind().equals(RequestKind.INSTANCE)) {
      return "get" + bindingName();
    }
    return "get" + bindingName() + dependencyKindName(requestKind());
  }

  /** Returns the canonical name for the {@link Binding}. */
  private String bindingName() {
    ContributionBinding binding = resolvedBindings().contributionBinding();
    return LOWER_CAMEL.to(UPPER_CAMEL, BindingVariableNamer.name(binding));
  }

  /** Returns a canonical name for the {@link RequestKind}. */
  private static String dependencyKindName(RequestKind kind) {
    return UPPER_UNDERSCORE.to(UPPER_CAMEL, kind.name());
  }
}

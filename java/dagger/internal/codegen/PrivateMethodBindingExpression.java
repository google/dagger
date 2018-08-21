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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static dagger.internal.codegen.GeneratedComponentModel.MethodSpecKind.PRIVATE_METHOD;
import static javax.lang.model.element.Modifier.PRIVATE;

import com.squareup.javapoet.TypeName;
import dagger.model.RequestKind;

/**
 * A binding expression that wraps the dependency expressions in a private, no-arg method.
 *
 * <p>Dependents of this binding expression will just call the no-arg private method.
 */
final class PrivateMethodBindingExpression extends MethodBindingExpression {
  private final ContributionBinding binding;
  private final RequestKind requestKind;
  private final BindingMethodImplementation methodImplementation;
  private final GeneratedComponentModel generatedComponentModel;
  private String methodName;

  PrivateMethodBindingExpression(
      ResolvedBindings resolvedBindings,
      RequestKind requestKind,
      BindingMethodImplementation methodImplementation,
      GeneratedComponentModel generatedComponentModel) {
    super(methodImplementation, generatedComponentModel);
    this.binding = resolvedBindings.contributionBinding();
    this.requestKind = checkNotNull(requestKind);
    this.methodImplementation = checkNotNull(methodImplementation);
    this.generatedComponentModel = checkNotNull(generatedComponentModel);
  }

  @Override
  protected void addMethod() {
    if (methodName == null) {
      // Have to set methodName field before implementing the method in order to handle recursion.
      methodName = generatedComponentModel.getUniqueGetterMethodName(binding, requestKind);
      // TODO(user): Fix the order that these generated methods are written to the component.
      generatedComponentModel.addMethod(
          PRIVATE_METHOD,
          methodBuilder(methodName)
              .addModifiers(PRIVATE)
              .returns(TypeName.get(methodImplementation.returnType()))
              .addCode(methodImplementation.body())
              .build());
    }
  }

  @Override
  protected String methodName() {
    checkState(methodName != null, "addMethod() must be called before methodName()");
    return methodName;
  }
}

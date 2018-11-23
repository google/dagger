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
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;

import com.squareup.javapoet.TypeName;
import dagger.internal.codegen.ModifiableBindingMethods.ModifiableBindingMethod;
import java.util.Optional;

/**
 * A binding expression that wraps a modifiable binding expression in a public, no-arg method.
 *
 * <p>Dependents of this binding expression will just call the modifiable binding method.
 */
final class ModifiableConcreteMethodBindingExpression extends MethodBindingExpression {
  private final ContributionBinding binding;
  private final BindingRequest request;
  private final ModifiableBindingType modifiableBindingType;
  private final BindingMethodImplementation methodImplementation;
  private final ComponentImplementation componentImplementation;
  private final boolean bindingCannotBeModified;
  private Optional<String> methodName;

  ModifiableConcreteMethodBindingExpression(
      ResolvedBindings resolvedBindings,
      BindingRequest request,
      ModifiableBindingType modifiableBindingType,
      BindingMethodImplementation methodImplementation,
      ComponentImplementation componentImplementation,
      Optional<ModifiableBindingMethod> matchingModifiableBindingMethod,
      boolean bindingCannotBeModified,
      DaggerTypes types) {
    super(methodImplementation, componentImplementation, matchingModifiableBindingMethod, types);
    this.binding = resolvedBindings.contributionBinding();
    this.request = checkNotNull(request);
    this.modifiableBindingType = checkNotNull(modifiableBindingType);
    this.methodImplementation = checkNotNull(methodImplementation);
    this.componentImplementation = checkNotNull(componentImplementation);
    this.bindingCannotBeModified = bindingCannotBeModified;
    this.methodName =
        matchingModifiableBindingMethod.map(modifiableMethod -> modifiableMethod.methodSpec().name);
  }

  @Override
  protected void addMethod() {
    // Add the modifiable binding method to the component if we haven't already.
    if (!methodName.isPresent()) {
      methodName = Optional.of(componentImplementation.getUniqueMethodName(request, binding));
      componentImplementation.addModifiableBindingMethod(
          modifiableBindingType,
          request,
          methodBuilder(methodName.get())
              .addModifiers(bindingCannotBeModified ? PRIVATE : PROTECTED)
              .returns(TypeName.get(methodImplementation.returnType()))
              .addCode(methodImplementation.body())
              .build(),
          bindingCannotBeModified);
    }
  }

  @Override
  protected String methodName() {
    checkState(methodName.isPresent(), "addMethod() must be called before methodName().");
    return methodName.get();
  }
}

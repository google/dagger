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
import static javax.lang.model.element.Modifier.PUBLIC;

import com.squareup.javapoet.CodeBlock;
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
  private final GeneratedComponentModel generatedComponentModel;
  private final boolean bindingFinalized;
  private final Optional<ModifiableBindingMethod> matchingModifiableBindingMethod;
  private Optional<String> methodName;

  ModifiableConcreteMethodBindingExpression(
      ResolvedBindings resolvedBindings,
      BindingRequest request,
      ModifiableBindingType modifiableBindingType,
      BindingMethodImplementation methodImplementation,
      GeneratedComponentModel generatedComponentModel,
      Optional<ModifiableBindingMethod> matchingModifiableBindingMethod,
      boolean bindingFinalized) {
    super(methodImplementation, generatedComponentModel);
    this.binding = resolvedBindings.contributionBinding();
    this.request = checkNotNull(request);
    this.modifiableBindingType = checkNotNull(modifiableBindingType);
    this.methodImplementation = checkNotNull(methodImplementation);
    this.generatedComponentModel = checkNotNull(generatedComponentModel);
    this.bindingFinalized = bindingFinalized;
    this.matchingModifiableBindingMethod = matchingModifiableBindingMethod;
    this.methodName =
        matchingModifiableBindingMethod.map(modifiableMethod -> modifiableMethod.methodSpec().name);
  }

  @Override
  CodeBlock getModifiableBindingMethodImplementation(
      ModifiableBindingMethod modifiableBindingMethod, GeneratedComponentModel component) {
    // Only emit the method implementation if the binding was known when the expression was created
    // (and not registered when calling 'getDependencyExpression'), and we're generating a
    // modifiable binding method for the original component (and not an ancestor component).
    if (matchingModifiableBindingMethod.isPresent() && generatedComponentModel.equals(component)) {
      checkState(
          matchingModifiableBindingMethod.get().fulfillsSameRequestAs(modifiableBindingMethod));
      return methodImplementation.body();
    }
    return super.getModifiableBindingMethodImplementation(modifiableBindingMethod, component);
  }

  @Override
  protected void addMethod() {
    // Add the modifiable binding method to the component model if we haven't already.
    if (!methodName.isPresent()) {
      methodName = Optional.of(generatedComponentModel.getUniqueMethodName(request, binding));
      generatedComponentModel.addModifiableBindingMethod(
          modifiableBindingType,
          request,
          methodBuilder(methodName.get())
              .addModifiers(bindingFinalized ? PRIVATE : PUBLIC)
              .returns(TypeName.get(methodImplementation.returnType()))
              .addCode(methodImplementation.body())
              .build(),
          bindingFinalized);
    }
  }

  @Override
  protected String methodName() {
    checkState(methodName.isPresent(), "addMethod() must be called before methodName().");
    return methodName.get();
  }
}

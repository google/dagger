/*
 * Copyright (C) 2018 The Dagger Authors.
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

import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.ModifiableBindingMethods.ModifiableBindingMethod;
import dagger.internal.codegen.langmodel.DaggerTypes;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;

/**
 * A {@link ModifiableAbstractMethodBindingExpression} for a binding that exists but is not ready to
 * be expressed in this compilation unit and should be deferred until a future compilation.
 * Generates a method that will be implemented in the future compilation.
 *
 * <p>A deferred modifiable binding expression is used when:
 *
 * <ul>
 *   <li>The generated code for a binding requires an instance of a type that is generated in the
 *       root component compilation unit.
 *   <li>A {@linkplain ModifiableBindingType#BINDS_METHOD_WITH_MISSING_DEPENDENCY {@code @Binds}
 *       method's dependency is missing} in a subcomponent.
 * </ul>
 */
final class DeferredModifiableBindingExpression extends ModifiableAbstractMethodBindingExpression {
  private final ComponentImplementation componentImplementation;
  private final ContributionBinding binding;
  private final BindingRequest request;

  DeferredModifiableBindingExpression(
      ComponentImplementation componentImplementation,
      ModifiableBindingType modifiableBindingType,
      ContributionBinding binding,
      BindingRequest request,
      Optional<ModifiableBindingMethod> matchingModifiableBindingMethod,
      Optional<ComponentMethodDescriptor> matchingComponentMethod,
      DaggerTypes types) {
    super(
        componentImplementation,
        modifiableBindingType,
        request,
        matchingModifiableBindingMethod,
        matchingComponentMethod,
        types);
    this.componentImplementation = checkNotNull(componentImplementation);
    this.binding = checkNotNull(binding);
    this.request = checkNotNull(request);
  }

  @Override
  String chooseMethodName() {
    return componentImplementation.getUniqueMethodName(request);
  }

  @Override
  protected TypeMirror contributedType() {
    return binding.contributedType();
  }
}

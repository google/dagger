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

import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.ModifiableBindingMethods.ModifiableBindingMethod;
import dagger.internal.codegen.langmodel.DaggerTypes;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;

/**
 * A {@link ModifiableAbstractMethodBindingExpression} for a binding that is missing when generating
 * the abstract base class implementation of a subcomponent. The (unimplemented) method is added to
 * the {@link ComponentImplementation} when the dependency expression is requested. The method is
 * overridden when generating the implementation of an ancestor component.
 */
final class MissingBindingExpression extends ModifiableAbstractMethodBindingExpression {
  private final ComponentImplementation componentImplementation;
  private final BindingRequest request;

  MissingBindingExpression(
      ComponentImplementation componentImplementation,
      BindingRequest request,
      Optional<ModifiableBindingMethod> matchingModifiableBindingMethod,
      Optional<ComponentMethodDescriptor> matchingComponentMethod,
      DaggerTypes types) {
    super(
        componentImplementation,
        ModifiableBindingType.MISSING,
        request,
        matchingModifiableBindingMethod,
        matchingComponentMethod,
        types);
    this.componentImplementation = componentImplementation;
    this.request = request;
  }

  @Override
  String chooseMethodName() {
    return componentImplementation.getUniqueMethodName(request);
  }

  @Override
  protected TypeMirror contributedType() {
    return request.key().type();
  }
}

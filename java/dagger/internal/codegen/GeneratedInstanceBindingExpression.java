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

import dagger.model.RequestKind;

/**
 * An {@link AbstractMethodModifiableBindingExpression} for a binding that requires an instance of a
 * generated type. This expression is used in abstract implementations of a subcomponent when there
 * are no concrete definitions of generated types available. The (unimplemented) method is added to
 * the {@code GeneratedComponentModel} when this dependency expression is requested. The method is
 * overridden when generating the concrete implementation of an ancestor component.
 */
final class GeneratedInstanceBindingExpression extends AbstractMethodModifiableBindingExpression {
  private final GeneratedComponentModel generatedComponentModel;
  private final ContributionBinding binding;
  private final RequestKind requestKind;

  GeneratedInstanceBindingExpression(
      GeneratedComponentModel generatedComponentModel,
      ResolvedBindings resolvedBindings,
      RequestKind requestKind) {
    super(
        generatedComponentModel,
        ModifiableBindingType.GENERATED_INSTANCE,
        resolvedBindings.key(),
        requestKind);
    this.generatedComponentModel = generatedComponentModel;
    this.binding = resolvedBindings.contributionBinding();
    this.requestKind = requestKind;
  }

  @Override
  String chooseMethodName() {
    return generatedComponentModel.getUniqueGetterMethodName(binding, requestKind);
  }
}

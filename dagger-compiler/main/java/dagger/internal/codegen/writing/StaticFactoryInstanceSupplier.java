/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.internal.codegen.writing;

import static dagger.internal.codegen.model.BindingKind.MULTIBOUND_MAP;
import static dagger.internal.codegen.model.BindingKind.MULTIBOUND_SET;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.binding.MultiboundMapBinding;
import dagger.internal.codegen.binding.MultiboundSetBinding;

/** An object that returns static factory to satisfy framework instance request. */
final class StaticFactoryInstanceSupplier implements FrameworkInstanceSupplier {
  private final FrameworkInstanceSupplier frameworkInstanceSupplier;

  @AssistedInject
  StaticFactoryInstanceSupplier(
      @Assisted ContributionBinding binding,
      FrameworkInstanceBindingRepresentation.Factory
          frameworkInstanceBindingRepresentationFactory) {
    this.frameworkInstanceSupplier = () -> staticFactoryCreation(binding);
  }

  @Override
  public MemberSelect memberSelect() {
    return frameworkInstanceSupplier.memberSelect();
  }

  // TODO(bcorso): no-op members injector is currently handled in
  // `MembersInjectorProviderCreationExpression`, we should inline the logic here so we won't create
  // an extra field for it.
  private MemberSelect staticFactoryCreation(ContributionBinding binding) {
    switch (binding.kind()) {
      case MULTIBOUND_MAP:
        return StaticMemberSelects.emptyMapFactory((MultiboundMapBinding) binding);
      case MULTIBOUND_SET:
        return StaticMemberSelects.emptySetFactory((MultiboundSetBinding) binding);
      case PROVISION:
      case INJECTION:
        return StaticMemberSelects.factoryCreateNoArgumentMethod(binding);
      default:
        throw new AssertionError(String.format("Invalid binding kind: %s", binding.kind()));
    }
  }

  @AssistedFactory
  static interface Factory {
    StaticFactoryInstanceSupplier create(ContributionBinding binding);
  }
}

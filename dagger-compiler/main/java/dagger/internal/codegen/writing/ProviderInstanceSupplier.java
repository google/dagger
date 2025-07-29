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

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.writing.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;

/** An object that initializes a framework-type component field for a binding. */
final class ProviderInstanceSupplier implements FrameworkInstanceSupplier {
  private final FrameworkInstanceSupplier frameworkInstanceSupplier;

  @AssistedInject
  ProviderInstanceSupplier(
      @Assisted ContributionBinding binding,
      CompilerOptions compilerOptions,
      ComponentImplementation componentImplementation,
      UnscopedFrameworkInstanceCreationExpressionFactory
          unscopedFrameworkInstanceCreationExpressionFactory,
      BindingRepresentations bindingRepresentations) {
    FrameworkInstanceCreationExpression frameworkInstanceCreationExpression =
        unscopedFrameworkInstanceCreationExpressionFactory.create(binding);
    this.frameworkInstanceSupplier =
        new FrameworkFieldInitializer(
            compilerOptions,
            componentImplementation,
            binding,
            binding.scope().isPresent()
                ? bindingRepresentations.scope(binding, frameworkInstanceCreationExpression)
                : frameworkInstanceCreationExpression);
  }

  @Override
  public MemberSelect memberSelect() {
    return frameworkInstanceSupplier.memberSelect();
  }

  @AssistedFactory
  static interface Factory {
    ProviderInstanceSupplier create(ContributionBinding binding);
  }
}

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

package dagger.internal.codegen.writing;

import androidx.room.compiler.processing.XProcessingEnv;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.FrameworkType;
import dagger.internal.codegen.binding.ProvisionBinding;

/** Binding expression for provider instances. */
final class ProviderInstanceRequestRepresentation extends FrameworkInstanceRequestRepresentation {

  @AssistedInject
  ProviderInstanceRequestRepresentation(
      @Assisted ProvisionBinding binding,
      SwitchingProviderInstanceSupplier.Factory switchingProviderInstanceSupplierFactory,
      StaticFactoryInstanceSupplier.Factory staticFactoryInstanceSupplierFactory,
      ProviderInstanceSupplier.Factory providerInstanceSupplierFactory,
      ComponentImplementation componentImplementation,
      XProcessingEnv processingEnv) {
    super(
        binding,
        frameworkInstanceSupplier(
            binding,
            switchingProviderInstanceSupplierFactory,
            staticFactoryInstanceSupplierFactory,
            providerInstanceSupplierFactory,
            componentImplementation),
        processingEnv);
  }

  @Override
  protected FrameworkType frameworkType() {
    return FrameworkType.PROVIDER;
  }

  private static FrameworkInstanceSupplier frameworkInstanceSupplier(
      ProvisionBinding binding,
      SwitchingProviderInstanceSupplier.Factory switchingProviderInstanceSupplierFactory,
      StaticFactoryInstanceSupplier.Factory staticFactoryInstanceSupplierFactory,
      ProviderInstanceSupplier.Factory providerInstanceSupplierFactory,
      ComponentImplementation componentImplementation) {
    FrameworkInstanceKind frameworkInstanceKind =
        FrameworkInstanceKind.from(binding, componentImplementation.compilerMode());
    switch (frameworkInstanceKind) {
      case SWITCHING_PROVIDER:
        return switchingProviderInstanceSupplierFactory.create(binding);
      case STATIC_FACTORY:
        return staticFactoryInstanceSupplierFactory.create(binding);
      case PROVIDER_FIELD:
        return providerInstanceSupplierFactory.create(binding);
    }
    throw new AssertionError("Unexpected FrameworkInstanceKind: " + frameworkInstanceKind);
  }

  @AssistedFactory
  static interface Factory {
    ProviderInstanceRequestRepresentation create(ProvisionBinding binding);
  }
}

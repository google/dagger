/*
 * Copyright (C) 2022 The Dagger Authors.
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

import static dagger.internal.codegen.model.BindingKind.DELEGATE;

import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.writing.ComponentImplementation.CompilerMode;

/** Generation mode for satisfying framework request to Provision Binding. */
enum FrameworkInstanceKind {
  SWITCHING_PROVIDER,
  STATIC_FACTORY,
  PROVIDER_FIELD;

  public static FrameworkInstanceKind from(ContributionBinding binding, CompilerMode compilerMode) {
    if (usesSwitchingProvider(binding, compilerMode)) {
      if (compilerMode.isFastInit()) {
        return SWITCHING_PROVIDER;
      } else {
        throw new IllegalStateException(
            "Compiler mode " + compilerMode + " cannot use Switching Provider.");
      }
    } else if (usesStaticFactoryCreation(binding, compilerMode)) {
      return STATIC_FACTORY;
    } else {
      return PROVIDER_FIELD;
    }
  }

  private static boolean usesSwitchingProvider(
      ContributionBinding binding, CompilerMode compilerMode) {
    if (!compilerMode.isFastInit()) {
      return false;
    }
    switch (binding.kind()) {
      case ASSISTED_INJECTION:
      case BOUND_INSTANCE:
      case COMPONENT:
      case COMPONENT_DEPENDENCY:
      case DELEGATE:
      case MEMBERS_INJECTOR: // TODO(b/199889259): Consider optimizing this for fastInit mode.
        // These binding kinds avoid SwitchingProvider when the backing instance already exists,
        // e.g. a component provider can use FactoryInstance.create(this).
        return false;
      case MULTIBOUND_SET:
      case MULTIBOUND_MAP:
      case OPTIONAL:
        // These binding kinds avoid SwitchingProvider when their are no dependencies,
        // e.g. a multibound set with no dependency can use a singleton, SetFactory.empty().
        return !binding.dependencies().isEmpty();
      case INJECTION:
      case PROVISION:
      case ASSISTED_FACTORY:
      case COMPONENT_PROVISION:
      case SUBCOMPONENT_CREATOR:
      case PRODUCTION:
      case COMPONENT_PRODUCTION:
      case MEMBERS_INJECTION:
        return true;
    }
    throw new AssertionError(String.format("No such binding kind: %s", binding.kind()));
  }

  private static boolean usesStaticFactoryCreation(
      ContributionBinding binding, CompilerMode compilerMode) {
    // If {@code binding} is an unscoped provision binding with no factory arguments, then
    // we don't need a field to hold its factory. In that case, this method returns the static
    // select that returns the factory.
    // member
    if (!binding.dependencies().isEmpty() || binding.scope().isPresent()) {
      return false;
    }
    switch (binding.kind()) {
      case MULTIBOUND_MAP:
      case MULTIBOUND_SET:
        return true;
      case PROVISION:
        return !compilerMode.isFastInit()
            && !binding.requiresModuleInstance();
      case INJECTION:
        return !compilerMode.isFastInit();
      default:
        return false;
    }
  }
}

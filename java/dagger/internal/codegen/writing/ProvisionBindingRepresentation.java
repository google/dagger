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

import static dagger.internal.codegen.writing.DelegateRequestRepresentation.isBindsScopeStrongerThanDependencyScope;
import static dagger.internal.codegen.writing.StaticFactoryInstanceSupplier.usesStaticFactoryCreation;
import static dagger.spi.model.BindingKind.DELEGATE;

import androidx.room.compiler.processing.XTypeElement;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.BindingRequest;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.spi.model.RequestKind;

/**
 * A binding representation that wraps code generation methods that satisfy all kinds of request for
 * that binding.
 */
final class ProvisionBindingRepresentation implements BindingRepresentation {
  private final BindingGraph graph;
  private final boolean isFastInit;
  private final ProvisionBinding binding;
  private final DirectInstanceBindingRepresentation directInstanceBindingRepresentation;
  private final FrameworkInstanceBindingRepresentation frameworkInstanceBindingRepresentation;

  @AssistedInject
  ProvisionBindingRepresentation(
      @Assisted ProvisionBinding binding,
      BindingGraph graph,
      ComponentImplementation componentImplementation,
      DirectInstanceBindingRepresentation.Factory directInstanceBindingRepresentationFactory,
      FrameworkInstanceBindingRepresentation.Factory frameworkInstanceBindingRepresentationFactory,
      SwitchingProviderInstanceSupplier.Factory switchingProviderInstanceSupplierFactory,
      ProviderInstanceSupplier.Factory providerInstanceSupplierFactory,
      StaticFactoryInstanceSupplier.Factory staticFactoryInstanceSupplierFactory,
      CompilerOptions compilerOptions,
      DaggerTypes types) {
    this.binding = binding;
    this.graph = graph;
    XTypeElement rootComponent =
        componentImplementation.rootComponentImplementation().componentDescriptor().typeElement();
    this.isFastInit = compilerOptions.fastInit(rootComponent);
    this.directInstanceBindingRepresentation =
        directInstanceBindingRepresentationFactory.create(binding);
    FrameworkInstanceSupplier frameworkInstanceSupplier = null;
    if (usesSwitchingProvider()) {
      frameworkInstanceSupplier =
          switchingProviderInstanceSupplierFactory.create(
              binding, directInstanceBindingRepresentation);
    } else if (usesStaticFactoryCreation(binding, isFastInit)) {
      frameworkInstanceSupplier = staticFactoryInstanceSupplierFactory.create(binding);
    } else {
      frameworkInstanceSupplier = providerInstanceSupplierFactory.create(binding);
    }
    this.frameworkInstanceBindingRepresentation =
        frameworkInstanceBindingRepresentationFactory.create(binding, frameworkInstanceSupplier);
  }

  @Override
  public RequestRepresentation getRequestRepresentation(BindingRequest request) {
    return usesDirectInstanceExpression(request.requestKind(), binding, graph, isFastInit)
        ? directInstanceBindingRepresentation.getRequestRepresentation(request)
        : frameworkInstanceBindingRepresentation.getRequestRepresentation(request);
  }

  static boolean usesDirectInstanceExpression(
      RequestKind requestKind, ProvisionBinding binding, BindingGraph graph, boolean isFastInit) {
    if (requestKind != RequestKind.INSTANCE && requestKind != RequestKind.FUTURE) {
      return false;
    }
    switch (binding.kind()) {
      case MEMBERS_INJECTOR:
        // Currently, we always use a framework instance for MembersInjectors, e.g.
        // InstanceFactory.create(Foo_MembersInjector.create(...)).
        // TODO(b/199889259): Consider optimizing this for fastInit mode.
        return false;
      case ASSISTED_INJECTION:
      case ASSISTED_FACTORY:
        // We choose not to use a direct expression for assisted injection/factory in default mode
        // because they technically act more similar to a Provider than an instance, so we cache
        // them using a field in the component similar to Provider requests. This should also be the
        // case in FastInit, but it hasn't been implemented yet. We also don't need to check for
        // caching since assisted bindings can't be scoped.
        return isFastInit;
      default:
        // We don't need to use Provider#get() if there's no caching, so use a direct instance.
        // TODO(bcorso): This can be optimized in cases where we know a Provider field already
        // exists, in which case even if it's not scoped we might as well call Provider#get().
        return !needsCaching(binding, graph);
    }
  }

  private boolean usesSwitchingProvider() {
    if (!isFastInit) {
      return false;
    }
    switch (binding.kind()) {
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
      case ASSISTED_INJECTION:
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

  /**
   * Returns {@code true} if the component needs to make sure the provided value is cached.
   *
   * <p>The component needs to cache the value for scoped bindings except for {@code @Binds}
   * bindings whose scope is no stronger than their delegate's.
   */
  static boolean needsCaching(ProvisionBinding binding, BindingGraph graph) {
    if (!binding.scope().isPresent()) {
      return false;
    }
    if (binding.kind().equals(DELEGATE)) {
      return isBindsScopeStrongerThanDependencyScope(binding, graph);
    }
    return true;
  }

  @AssistedFactory
  static interface Factory {
    ProvisionBindingRepresentation create(ProvisionBinding binding);
  }
}

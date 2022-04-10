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

import static dagger.internal.codegen.base.Util.reentrantComputeIfAbsent;
import static dagger.internal.codegen.binding.BindingRequest.bindingRequest;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.BindingRequest;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.spi.model.RequestKind;
import java.util.HashMap;
import java.util.Map;

/** Returns request representation based on a direct instance expression. */
final class DirectInstanceBindingRepresentation {
  private final ProvisionBinding binding;
  private final ImmediateFutureRequestRepresentation.Factory
      immediateFutureRequestRepresentationFactory;
  private final PrivateMethodRequestRepresentation.Factory
      privateMethodRequestRepresentationFactory;
  private final UnscopedDirectInstanceRequestRepresentationFactory
      unscopedDirectInstanceRequestRepresentationFactory;
  private final Map<BindingRequest, RequestRepresentation> requestRepresentations = new HashMap<>();

  @AssistedInject
  DirectInstanceBindingRepresentation(
      @Assisted ProvisionBinding binding,
      ImmediateFutureRequestRepresentation.Factory immediateFutureRequestRepresentationFactory,
      PrivateMethodRequestRepresentation.Factory privateMethodRequestRepresentationFactory,
      UnscopedDirectInstanceRequestRepresentationFactory
          unscopedDirectInstanceRequestRepresentationFactory) {
    this.binding = binding;
    this.immediateFutureRequestRepresentationFactory = immediateFutureRequestRepresentationFactory;
    this.privateMethodRequestRepresentationFactory = privateMethodRequestRepresentationFactory;
    this.unscopedDirectInstanceRequestRepresentationFactory =
        unscopedDirectInstanceRequestRepresentationFactory;
  }

  public RequestRepresentation getRequestRepresentation(BindingRequest request) {
    return reentrantComputeIfAbsent(
        requestRepresentations, request, this::getRequestRepresentationUncached);
  }

  private RequestRepresentation getRequestRepresentationUncached(BindingRequest request) {
    switch (request.requestKind()) {
      case INSTANCE:
        return requiresMethodEncapsulation(binding)
            ? wrapInPrivateMethod(
                unscopedDirectInstanceRequestRepresentationFactory.create(binding))
            : unscopedDirectInstanceRequestRepresentationFactory.create(binding);

      case FUTURE:
        return immediateFutureRequestRepresentationFactory.create(
            getRequestRepresentation(bindingRequest(binding.key(), RequestKind.INSTANCE)),
            binding.key().type().xprocessing());

      default:
        throw new AssertionError(
            String.format("Invalid binding request kind: %s", request.requestKind()));
    }
  }

  /** Wraps the expression for a given binding in a no-arg private method. */
  RequestRepresentation wrapInPrivateMethod(RequestRepresentation bindingExpression) {
    return bindingExpression instanceof PrivateMethodRequestRepresentation
        // If we've already wrapped the expression then just return it as is.
        ? bindingExpression
        : privateMethodRequestRepresentationFactory.create(
            bindingRequest(binding.key(), RequestKind.INSTANCE), binding, bindingExpression);
  }

  private static boolean requiresMethodEncapsulation(ProvisionBinding binding) {
    switch (binding.kind()) {
      case COMPONENT:
      case COMPONENT_PROVISION:
      case SUBCOMPONENT_CREATOR:
      case COMPONENT_DEPENDENCY:
      case MULTIBOUND_SET:
      case MULTIBOUND_MAP:
      case BOUND_INSTANCE:
      case ASSISTED_FACTORY:
      case ASSISTED_INJECTION:
      case INJECTION:
      case PROVISION:
        // These binding kinds satify a binding request with a component method or a private
        // method when the requested binding has dependencies. The method will wrap the logic of
        // creating the binding instance. Without the encapsulation, we might see many levels of
        // nested instance creation code in a single statement to satisfy all dependencies of a
        // binding request.
        return !binding.dependencies().isEmpty();
      case MEMBERS_INJECTOR:
      case PRODUCTION:
      case COMPONENT_PRODUCTION:
      case OPTIONAL:
      case DELEGATE:
      case MEMBERS_INJECTION:
        return false;
    }
    throw new AssertionError(String.format("No such binding kind: %s", binding.kind()));
  }

  @AssistedFactory
  static interface Factory {
    DirectInstanceBindingRepresentation create(ProvisionBinding binding);
  }
}

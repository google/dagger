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
import static dagger.internal.codegen.model.BindingKind.DELEGATE;
import static dagger.internal.codegen.writing.ProvisionBindingRepresentation.needsCaching;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.BindingRequest;
import dagger.internal.codegen.binding.FrameworkType;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.model.RequestKind;
import java.util.HashMap;
import java.util.Map;

/** Returns request representation that wraps a framework instance expression */
final class FrameworkInstanceBindingRepresentation {
  private final ProvisionBinding binding;
  private final DerivedFromFrameworkInstanceRequestRepresentation.Factory
      derivedFromFrameworkInstanceRequestRepresentationFactory;
  private final ImmediateFutureRequestRepresentation.Factory
      immediateFutureRequestRepresentationFactory;
  private final Map<BindingRequest, RequestRepresentation> requestRepresentations = new HashMap<>();
  private final RequestRepresentation providerRequestRepresentation;
  private final RequestRepresentation producerFromProviderRepresentation;

  @AssistedInject
  FrameworkInstanceBindingRepresentation(
      @Assisted ProvisionBinding binding,
      BindingGraph graph,
      ComponentImplementation componentImplementation,
      DelegateRequestRepresentation.Factory delegateRequestRepresentationFactory,
      DerivedFromFrameworkInstanceRequestRepresentation.Factory
          derivedFromFrameworkInstanceRequestRepresentationFactory,
      ImmediateFutureRequestRepresentation.Factory immediateFutureRequestRepresentationFactory,
      ProducerNodeInstanceRequestRepresentation.Factory
          producerNodeInstanceRequestRepresentationFactory,
      ProviderInstanceRequestRepresentation.Factory providerInstanceRequestRepresentationFactory,
      ProducerFromProviderCreationExpression.Factory
          producerFromProviderCreationExpressionFactory) {
    this.binding = binding;
    this.derivedFromFrameworkInstanceRequestRepresentationFactory =
        derivedFromFrameworkInstanceRequestRepresentationFactory;
    this.immediateFutureRequestRepresentationFactory = immediateFutureRequestRepresentationFactory;
    this.providerRequestRepresentation =
        binding.kind().equals(DELEGATE) && !needsCaching(binding, graph)
            ? delegateRequestRepresentationFactory.create(binding, RequestKind.PROVIDER)
            : providerInstanceRequestRepresentationFactory.create(binding);
    this.producerFromProviderRepresentation =
        producerNodeInstanceRequestRepresentationFactory.create(
            binding,
            new FrameworkFieldInitializer(
                componentImplementation,
                binding,
                producerFromProviderCreationExpressionFactory.create(
                    providerRequestRepresentation,
                    componentImplementation.shardImplementation(binding).name())));
  }

  public RequestRepresentation getRequestRepresentation(BindingRequest request) {
    return reentrantComputeIfAbsent(
        requestRepresentations, request, this::getRequestRepresentationUncached);
  }

  private RequestRepresentation getRequestRepresentationUncached(BindingRequest request) {
    switch (request.requestKind()) {
      case INSTANCE:
      case LAZY:
      case PRODUCED:
      case PROVIDER_OF_LAZY:
        return derivedFromFrameworkInstanceRequestRepresentationFactory.create(
            binding, providerRequestRepresentation, request.requestKind(), FrameworkType.PROVIDER);
      case PROVIDER:
        return providerRequestRepresentation;
      case PRODUCER:
        return producerFromProviderRepresentation;

      case FUTURE:
        return immediateFutureRequestRepresentationFactory.create(
            getRequestRepresentation(bindingRequest(binding.key(), RequestKind.INSTANCE)),
            binding.key().type().xprocessing());

      default:
        throw new AssertionError(
            String.format("Invalid binding request kind: %s", request.requestKind()));
    }
  }

  @AssistedFactory
  static interface Factory {
    FrameworkInstanceBindingRepresentation create(ProvisionBinding binding);
  }
}

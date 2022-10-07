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
import static dagger.spi.model.BindingKind.MULTIBOUND_MAP;
import static dagger.spi.model.BindingKind.MULTIBOUND_SET;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.BindingRequest;
import dagger.internal.codegen.binding.FrameworkType;
import dagger.internal.codegen.binding.ProductionBinding;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A binding representation that wraps code generation methods that satisfy all kinds of request for
 * that binding.
 */
final class ProductionBindingRepresentation implements BindingRepresentation {
  private final ProductionBinding binding;
  private final DerivedFromFrameworkInstanceRequestRepresentation.Factory
      derivedFromFrameworkInstanceRequestRepresentationFactory;
  private final RequestRepresentation frameworkInstanceRequestRepresentation;
  private final Map<BindingRequest, RequestRepresentation> requestRepresentations = new HashMap<>();

  @AssistedInject
  ProductionBindingRepresentation(
      @Assisted ProductionBinding binding,
      ComponentImplementation componentImplementation,
      DerivedFromFrameworkInstanceRequestRepresentation.Factory
          derivedFromFrameworkInstanceRequestRepresentationFactory,
      ProducerNodeInstanceRequestRepresentation.Factory
          producerNodeInstanceRequestRepresentationFactory,
      UnscopedFrameworkInstanceCreationExpressionFactory
          unscopedFrameworkInstanceCreationExpressionFactory,
      BindingRepresentations bindingRepresentations) {
    this.binding = binding;
    this.derivedFromFrameworkInstanceRequestRepresentationFactory =
        derivedFromFrameworkInstanceRequestRepresentationFactory;
    Optional<MemberSelect> staticMethod = staticFactoryCreation();
    FrameworkInstanceSupplier frameworkInstanceSupplier =
        staticMethod.isPresent()
            ? staticMethod::get
            : new FrameworkFieldInitializer(
                componentImplementation,
                binding,
                binding.scope().isPresent()
                    ? bindingRepresentations.scope(
                        binding, unscopedFrameworkInstanceCreationExpressionFactory.create(binding))
                    : unscopedFrameworkInstanceCreationExpressionFactory.create(binding));
    this.frameworkInstanceRequestRepresentation =
        producerNodeInstanceRequestRepresentationFactory.create(binding, frameworkInstanceSupplier);
  }

  @Override
  public RequestRepresentation getRequestRepresentation(BindingRequest request) {
    return reentrantComputeIfAbsent(
        requestRepresentations, request, this::getRequestRepresentationUncached);
  }

  private RequestRepresentation getRequestRepresentationUncached(BindingRequest request) {
    return request.frameworkType().isPresent()
        ? frameworkInstanceRequestRepresentation
        : derivedFromFrameworkInstanceRequestRepresentationFactory.create(
            binding,
            frameworkInstanceRequestRepresentation,
            request.requestKind(),
            FrameworkType.PRODUCER_NODE);
  }
  /**
   * If {@code resolvedBindings} is an unscoped provision binding with no factory arguments, then we
   * don't need a field to hold its factory. In that case, this method returns the static member
   * select that returns the factory.
   */
  private Optional<MemberSelect> staticFactoryCreation() {
    if (binding.dependencies().isEmpty()) {
      if (binding.kind().equals(MULTIBOUND_MAP)) {
        return Optional.of(StaticMemberSelects.emptyMapFactory(binding));
      }
      if (binding.kind().equals(MULTIBOUND_SET)) {
        return Optional.of(StaticMemberSelects.emptySetFactory(binding));
      }
    }
    return Optional.empty();
  }

  @AssistedFactory
  static interface Factory {
    ProductionBindingRepresentation create(ProductionBinding binding);
  }
}

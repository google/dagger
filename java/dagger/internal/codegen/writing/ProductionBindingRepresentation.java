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
import static dagger.internal.codegen.javapoet.TypeNames.DOUBLE_CHECK;
import static dagger.internal.codegen.javapoet.TypeNames.SINGLE_CHECK;
import static dagger.internal.codegen.writing.MemberSelect.staticFactoryCreation;
import static dagger.spi.model.BindingKind.MULTIBOUND_MAP;
import static dagger.spi.model.BindingKind.MULTIBOUND_SET;

import com.squareup.javapoet.CodeBlock;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.BindingRequest;
import dagger.internal.codegen.binding.FrameworkType;
import dagger.internal.codegen.binding.ProductionBinding;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.internal.codegen.writing.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A binding representation that wraps code generation methods that satisfy all kinds of request for
 * that binding.
 */
final class ProductionBindingRepresentation implements BindingRepresentation {
  private final ProductionBinding binding;
  private final ComponentImplementation componentImplementation;
  private final DerivedFromFrameworkInstanceRequestRepresentation.Factory
      derivedFromFrameworkInstanceRequestRepresentationFactory;
  private final ProducerNodeInstanceRequestRepresentation.Factory
      producerNodeInstanceRequestRepresentationFactory;
  private final UnscopedFrameworkInstanceCreationExpressionFactory
      unscopedFrameworkInstanceCreationExpressionFactory;
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
      DaggerTypes types) {
    this.binding = binding;
    this.componentImplementation = componentImplementation;
    this.derivedFromFrameworkInstanceRequestRepresentationFactory =
        derivedFromFrameworkInstanceRequestRepresentationFactory;
    this.producerNodeInstanceRequestRepresentationFactory =
        producerNodeInstanceRequestRepresentationFactory;
    this.unscopedFrameworkInstanceCreationExpressionFactory =
        unscopedFrameworkInstanceCreationExpressionFactory;
  }

  @Override
  public RequestRepresentation getRequestRepresentation(BindingRequest request) {
    return reentrantComputeIfAbsent(
        requestRepresentations, request, this::getRequestRepresentationUncached);
  }

  private RequestRepresentation getRequestRepresentationUncached(BindingRequest request) {
    return request.frameworkType().isPresent()
        ? frameworkInstanceRequestRepresentation()
        : derivedFromFrameworkInstanceRequestRepresentationFactory.create(
            request, FrameworkType.PRODUCER_NODE);
  }

  /**
   * Returns a binding expression that uses a {@link dagger.producers.Producer} for production
   * bindings.
   */
  private RequestRepresentation frameworkInstanceRequestRepresentation() {
    FrameworkInstanceCreationExpression frameworkInstanceCreationExpression =
        unscopedFrameworkInstanceCreationExpressionFactory.create(binding);

    // TODO(bcorso): Consider merging the static factory creation logic into CreationExpressions?
    Optional<MemberSelect> staticMethod =
        useStaticFactoryCreation() ? staticFactoryCreation(binding) : Optional.empty();
    FrameworkInstanceSupplier frameworkInstanceSupplier =
        staticMethod.isPresent()
            ? staticMethod::get
            : new FrameworkFieldInitializer(
                componentImplementation,
                binding,
                binding.scope().isPresent()
                    ? scope(frameworkInstanceCreationExpression)
                    : frameworkInstanceCreationExpression);

    return producerNodeInstanceRequestRepresentationFactory.create(
        binding, frameworkInstanceSupplier);
  }

  private FrameworkInstanceCreationExpression scope(FrameworkInstanceCreationExpression unscoped) {
    return () ->
        CodeBlock.of(
            "$T.provider($L)",
            binding.scope().get().isReusable() ? SINGLE_CHECK : DOUBLE_CHECK,
            unscoped.creationExpression());
  }

  /**
   * Returns {@code true} if the binding should use the static factory creation strategy.
   *
   * <p>We allow static factories that can reused across multiple bindings, e.g. {@code MapFactory}
   * or {@code SetFactory}.
   */
  private boolean useStaticFactoryCreation() {
    return binding.kind().equals(MULTIBOUND_MAP) || binding.kind().equals(MULTIBOUND_SET);
  }

  @AssistedFactory
  static interface Factory {
    ProductionBindingRepresentation create(ProductionBinding binding);
  }
}

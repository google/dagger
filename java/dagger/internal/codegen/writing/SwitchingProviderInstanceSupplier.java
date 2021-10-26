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

import static dagger.internal.codegen.binding.BindingRequest.bindingRequest;
import static dagger.internal.codegen.javapoet.TypeNames.DOUBLE_CHECK;
import static dagger.internal.codegen.javapoet.TypeNames.SINGLE_CHECK;
import static dagger.internal.codegen.writing.ProvisionBindingRepresentation.usesDirectInstanceExpression;

import com.squareup.javapoet.CodeBlock;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.Binding;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.BindingRequest;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.writing.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;
import dagger.spi.model.BindingKind;
import dagger.spi.model.RequestKind;

/**
 * An object that initializes a framework-type component field for a binding using instances created
 * by switching providers.
 */
final class SwitchingProviderInstanceSupplier implements FrameworkInstanceSupplier {
  private final FrameworkInstanceSupplier frameworkInstanceSupplier;

  @AssistedInject
  SwitchingProviderInstanceSupplier(
      @Assisted ProvisionBinding binding,
      @Assisted DirectInstanceBindingRepresentation directInstanceBindingRepresentation,
      SwitchingProviders switchingProviders,
      BindingGraph graph,
      ComponentImplementation componentImplementation,
      UnscopedDirectInstanceRequestRepresentationFactory
          unscopedDirectInstanceRequestRepresentationFactory) {
    BindingRequest instanceRequest = bindingRequest(binding.key(), RequestKind.INSTANCE);
    FrameworkInstanceCreationExpression frameworkInstanceCreationExpression =
        switchingProviders.newFrameworkInstanceCreationExpression(
            binding,
            // Use the directInstanceBindingRepresentation if possible, that way we share a private
            // method implementation if one already exists. Otherwise, we use the
            // unscopedDirectInstanceRequestRepresentation and, since we're guaranteed this is the
            // only place that will be using the expression in this case, there is no need to wrap
            // the expression in a private method.
            // Note: we can't use ComponentBindingRepresentation.getRequestRepresentation(
            // instanceRequest) here, since that would return fooProvider.get() and cause a cycle.
            usesDirectInstanceExpression(RequestKind.INSTANCE, binding, graph, true)
                ? directInstanceBindingRepresentation.getRequestRepresentation(instanceRequest)
                : unscopedDirectInstanceRequestRepresentationFactory.create(binding));
    this.frameworkInstanceSupplier =
        new FrameworkFieldInitializer(
            componentImplementation, binding, scope(binding, frameworkInstanceCreationExpression));
  }

  @Override
  public MemberSelect memberSelect() {
    return frameworkInstanceSupplier.memberSelect();
  }

  private FrameworkInstanceCreationExpression scope(
      Binding binding, FrameworkInstanceCreationExpression unscoped) {
    // Caching assisted factory provider, so that there won't be new factory created for each
    // provider.get() call.
    if (!binding.scope().isPresent() && !binding.kind().equals(BindingKind.ASSISTED_FACTORY)) {
      return unscoped;
    }
    return () ->
        CodeBlock.of(
            "$T.provider($L)",
            binding.scope().isPresent()
                ? (binding.scope().get().isReusable() ? SINGLE_CHECK : DOUBLE_CHECK)
                : SINGLE_CHECK,
            unscoped.creationExpression());
  }

  @AssistedFactory
  static interface Factory {
    SwitchingProviderInstanceSupplier create(
        ProvisionBinding binding,
        DirectInstanceBindingRepresentation directInstanceBindingRepresentation);
  }
}

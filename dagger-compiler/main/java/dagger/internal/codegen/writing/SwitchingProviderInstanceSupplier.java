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

import static androidx.room.compiler.codegen.compat.XConverters.toJavaPoet;

import com.squareup.javapoet.CodeBlock;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.Binding;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.model.BindingKind;
import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;
import dagger.internal.codegen.writing.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;
import dagger.internal.codegen.xprocessing.XTypeNames;

/**
 * An object that initializes a framework-type component field for a binding using instances created
 * by switching providers.
 */
final class SwitchingProviderInstanceSupplier implements FrameworkInstanceSupplier {
  private final FrameworkInstanceSupplier frameworkInstanceSupplier;

  @AssistedInject
  SwitchingProviderInstanceSupplier(
      @Assisted ContributionBinding binding,
      BindingGraph graph,
      ComponentImplementation componentImplementation,
      UnscopedDirectInstanceRequestRepresentationFactory
          unscopedDirectInstanceRequestRepresentationFactory) {
    ShardImplementation shardImplementation = componentImplementation.shardImplementation(binding);
    FrameworkInstanceCreationExpression frameworkInstanceCreationExpression =
        shardImplementation
            .getSwitchingProviders()
            .newFrameworkInstanceCreationExpression(
                binding, unscopedDirectInstanceRequestRepresentationFactory.create(binding));
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
            toJavaPoet(
                binding.scope().isPresent()
                    ? (binding.scope().get().isReusable()
                        ? XTypeNames.SINGLE_CHECK
                        : XTypeNames.DOUBLE_CHECK)
                    : XTypeNames.SINGLE_CHECK),
            unscoped.creationExpression());
  }

  @AssistedFactory
  static interface Factory {
    SwitchingProviderInstanceSupplier create(ContributionBinding binding);
  }
}

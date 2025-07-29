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

import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.binding.BindingRequest.bindingRequest;

import androidx.room.compiler.codegen.XCodeBlock;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.OptionalBinding;
import dagger.internal.codegen.writing.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;

/**
 * A {@link FrameworkInstanceCreationExpression} for {@link
 * dagger.internal.codegen.model.BindingKind#OPTIONAL optional bindings}.
 */
final class OptionalFactoryInstanceCreationExpression
    implements FrameworkInstanceCreationExpression {
  private final OptionalFactories optionalFactories;
  private final OptionalBinding binding;
  private final ComponentImplementation componentImplementation;
  private final ComponentRequestRepresentations componentRequestRepresentations;

  @AssistedInject
  OptionalFactoryInstanceCreationExpression(
      @Assisted OptionalBinding binding,
      OptionalFactories optionalFactories,
      ComponentImplementation componentImplementation,
      ComponentRequestRepresentations componentRequestRepresentations) {
    this.optionalFactories = optionalFactories;
    this.binding = binding;
    this.componentImplementation = componentImplementation;
    this.componentRequestRepresentations = componentRequestRepresentations;
  }

  @Override
  public XCodeBlock creationExpression() {
    return binding.dependencies().isEmpty()
        ? optionalFactories.absentOptionalProvider(binding)
        : optionalFactories.presentOptionalFactory(
            binding,
            componentRequestRepresentations
                .getDependencyExpression(
                    bindingRequest(
                        getOnlyElement(binding.dependencies()).key(), binding.frameworkType()),
                    componentImplementation.shardImplementation(binding).name())
                .codeBlock());
  }

  @AssistedFactory
  static interface Factory {
    OptionalFactoryInstanceCreationExpression create(OptionalBinding binding);
  }
}

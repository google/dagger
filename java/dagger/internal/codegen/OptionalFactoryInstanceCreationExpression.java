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

package dagger.internal.codegen;

import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.BindingRequest.bindingRequest;

import com.squareup.javapoet.CodeBlock;
import dagger.internal.codegen.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;

/**
 * A {@link FrameworkInstanceCreationExpression} for {@link dagger.model.BindingKind#OPTIONAL
 * optional bindings}.
 */
final class OptionalFactoryInstanceCreationExpression
    implements FrameworkInstanceCreationExpression {
  private final OptionalFactories optionalFactories;
  private final ContributionBinding binding;
  private final ComponentImplementation componentImplementation;
  private final ComponentBindingExpressions componentBindingExpressions;

  OptionalFactoryInstanceCreationExpression(
      OptionalFactories optionalFactories,
      ContributionBinding binding,
      ComponentImplementation componentImplementation,
      ComponentBindingExpressions componentBindingExpressions) {
    this.optionalFactories = optionalFactories;
    this.binding = binding;
    this.componentImplementation = componentImplementation;
    this.componentBindingExpressions = componentBindingExpressions;
  }

  @Override
  public CodeBlock creationExpression() {
    return binding.dependencies().isEmpty()
        ? optionalFactories.absentOptionalProvider(binding)
        : optionalFactories.presentOptionalFactory(
            binding,
            componentBindingExpressions
                .getDependencyExpression(
                    bindingRequest(
                        getOnlyElement(binding.dependencies()).key(), binding.frameworkType()),
                    componentImplementation.name())
                .codeBlock());
  }

  @Override
  public boolean useInnerSwitchingProvider() {
    // Share providers for empty optionals from OptionalFactories so we don't have numerous
    // switch cases that all return Optional.empty().
    return !binding.dependencies().isEmpty();
  }
}

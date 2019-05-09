/*
 * Copyright (C) 2015 The Dagger Authors.
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.BindingRequest.bindingRequest;

import com.squareup.javapoet.CodeBlock;
import dagger.internal.codegen.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;
import dagger.internal.codegen.javapoet.CodeBlocks;
import dagger.model.DependencyRequest;

/** A framework instance creation expression for a {@link dagger.Binds @Binds} binding. */
final class DelegatingFrameworkInstanceCreationExpression
    implements FrameworkInstanceCreationExpression {

  private final ContributionBinding binding;
  private final ComponentImplementation componentImplementation;
  private final ComponentBindingExpressions componentBindingExpressions;

  DelegatingFrameworkInstanceCreationExpression(
      ContributionBinding binding,
      ComponentImplementation componentImplementation,
      ComponentBindingExpressions componentBindingExpressions) {
    this.binding = checkNotNull(binding);
    this.componentImplementation = checkNotNull(componentImplementation);
    this.componentBindingExpressions = checkNotNull(componentBindingExpressions);
  }

  @Override
  public CodeBlock creationExpression() {
    DependencyRequest dependency = getOnlyElement(binding.dependencies());
    return CodeBlocks.cast(
        componentBindingExpressions
            .getDependencyExpression(
                bindingRequest(dependency.key(), binding.frameworkType()),
                componentImplementation.name())
            .codeBlock(),
        binding.frameworkType().frameworkClass());
  }
}

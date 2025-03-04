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

package dagger.internal.codegen.writing;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.binding.BindingRequest.bindingRequest;

import com.squareup.javapoet.CodeBlock;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.DelegateBinding;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.javapoet.CodeBlocks;
import dagger.internal.codegen.model.DependencyRequest;
import dagger.internal.codegen.writing.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;

/** A framework instance creation expression for a {@link dagger.Binds @Binds} binding. */
final class DelegatingFrameworkInstanceCreationExpression
    implements FrameworkInstanceCreationExpression {

  private final DelegateBinding binding;
  private final ComponentImplementation componentImplementation;
  private final ComponentRequestRepresentations componentRequestRepresentations;

  @AssistedInject
  DelegatingFrameworkInstanceCreationExpression(
      @Assisted DelegateBinding binding,
      ComponentImplementation componentImplementation,
      ComponentRequestRepresentations componentRequestRepresentations,
      CompilerOptions compilerOptions) {
    this.binding = checkNotNull(binding);
    this.componentImplementation = componentImplementation;
    this.componentRequestRepresentations = componentRequestRepresentations;
  }

  @Override
  public CodeBlock creationExpression() {
    DependencyRequest dependency = getOnlyElement(binding.dependencies());
    return CodeBlocks.cast(
        componentRequestRepresentations
            .getDependencyExpression(
                bindingRequest(dependency.key(), binding.frameworkType()),
                componentImplementation.shardImplementation(binding).name())
            .codeBlock(),
        binding.frameworkType().frameworkClassName());
  }

  @AssistedFactory
  static interface Factory {
    DelegatingFrameworkInstanceCreationExpression create(DelegateBinding binding);
  }
}

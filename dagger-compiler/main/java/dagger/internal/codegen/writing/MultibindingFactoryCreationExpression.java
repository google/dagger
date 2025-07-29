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

import static com.google.common.base.Preconditions.checkNotNull;

import androidx.room.compiler.codegen.XCodeBlock;
import dagger.internal.codegen.binding.BindingRequest;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.model.DependencyRequest;
import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;
import dagger.internal.codegen.writing.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;
import dagger.internal.codegen.xprocessing.XCodeBlocks;

/** An abstract factory creation expression for multibindings. */
abstract class MultibindingFactoryCreationExpression
    implements FrameworkInstanceCreationExpression {
  private final ShardImplementation shardImplementation;
  private final ComponentRequestRepresentations componentRequestRepresentations;
  private final ContributionBinding binding;
  private final CompilerOptions compilerOptions;

  MultibindingFactoryCreationExpression(
      ContributionBinding binding,
      ComponentImplementation componentImplementation,
      ComponentRequestRepresentations componentRequestRepresentations,
      CompilerOptions compilerOptions) {
    this.binding = checkNotNull(binding);
    this.shardImplementation = checkNotNull(componentImplementation).shardImplementation(binding);
    this.componentRequestRepresentations = checkNotNull(componentRequestRepresentations);
    this.compilerOptions = checkNotNull(compilerOptions);
  }

  /** Returns the expression for a dependency of this multibinding. */
  protected final XCodeBlock multibindingDependencyExpression(DependencyRequest dependency) {
    XCodeBlock expression =
        componentRequestRepresentations
            .getDependencyExpression(
                BindingRequest.bindingRequest(dependency.key(), binding.frameworkType()),
                shardImplementation.name())
            .codeBlock();

    return useRawType()
        ? XCodeBlocks.cast(expression, binding.frameworkType().frameworkClassName())
        : expression;
  }

  /** The binding request for this framework instance. */
  protected final BindingRequest bindingRequest() {
    return BindingRequest.bindingRequest(binding.key(), binding.frameworkType());
  }

  /**
   * Returns true if the {@linkplain ContributionBinding#key() key type} is inaccessible from the
   * component, and therefore a raw type must be used.
   */
  protected final boolean useRawType() {
    return !shardImplementation.isTypeAccessible(binding.key().type().xprocessing());
  }
}

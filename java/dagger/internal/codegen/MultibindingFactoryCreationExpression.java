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

import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.CodeBlock;
import dagger.internal.codegen.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;
import dagger.internal.codegen.ModifiableBindingMethods.ModifiableBindingMethod;
import dagger.internal.codegen.javapoet.CodeBlocks;
import dagger.model.DependencyRequest;
import dagger.model.Key;
import java.util.Optional;

/** An abstract factory creation expression for multibindings. */
abstract class MultibindingFactoryCreationExpression
    implements FrameworkInstanceCreationExpression {
  private final ComponentImplementation componentImplementation;
  private final ComponentBindingExpressions componentBindingExpressions;
  private final ContributionBinding binding;

  MultibindingFactoryCreationExpression(
      ContributionBinding binding,
      ComponentImplementation componentImplementation,
      ComponentBindingExpressions componentBindingExpressions) {
    this.binding = checkNotNull(binding);
    this.componentImplementation = checkNotNull(componentImplementation);
    this.componentBindingExpressions = checkNotNull(componentBindingExpressions);
  }

  /** Returns the expression for a dependency of this multibinding. */
  protected final CodeBlock multibindingDependencyExpression(DependencyRequest dependency) {
    CodeBlock expression =
        componentBindingExpressions
            .getDependencyExpression(
                BindingRequest.bindingRequest(dependency.key(), binding.frameworkType()),
                componentImplementation.name())
            .codeBlock();

    return useRawType()
        ? CodeBlocks.cast(expression, binding.frameworkType().frameworkClass())
        : expression;
  }

  protected final ImmutableSet<DependencyRequest> dependenciesToImplement() {
    ImmutableSet<Key> alreadyImplementedKeys =
        componentImplementation.superclassContributionsMade(bindingRequest());
    return binding.dependencies().stream()
        .filter(dependency -> !alreadyImplementedKeys.contains(dependency.key()))
        .collect(toImmutableSet());
  }

  protected Optional<CodeBlock> superContributions() {
    if (dependenciesToImplement().size() == binding.dependencies().size()) {
      return Optional.empty();
    }
    ModifiableBindingMethod superMethod =
        componentImplementation.getModifiableBindingMethod(bindingRequest()).get();
    return Optional.of(CodeBlock.of("super.$N()", superMethod.methodSpec().name));
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
    return !componentImplementation.isTypeAccessible(binding.key().type());
  }

  @Override
  public final boolean useInnerSwitchingProvider() {
    return !binding.dependencies().isEmpty();
  }
}

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

import com.squareup.javapoet.CodeBlock;
import dagger.internal.codegen.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;

/** An abstract factory creation expression for multibindings. */
abstract class MultibindingFactoryCreationExpression
    implements FrameworkInstanceCreationExpression {
  private final GeneratedComponentModel generatedComponentModel;
  private final ComponentBindingExpressions componentBindingExpressions;
  private final ContributionBinding binding;

  MultibindingFactoryCreationExpression(
      ContributionBinding binding,
      GeneratedComponentModel generatedComponentModel,
      ComponentBindingExpressions componentBindingExpressions) {
    this.binding = checkNotNull(binding);
    this.generatedComponentModel = checkNotNull(generatedComponentModel);
    this.componentBindingExpressions = checkNotNull(componentBindingExpressions);
  }

  /** Returns the expression for a dependency of this multibinding. */
  protected final CodeBlock multibindingDependencyExpression(
      FrameworkDependency frameworkDependency) {
    CodeBlock expression =
        componentBindingExpressions
            .getDependencyExpression(
                BindingRequest.bindingRequest(frameworkDependency), generatedComponentModel.name())
            .codeBlock();
    return useRawType()
        ? CodeBlocks.cast(expression, frameworkDependency.frameworkClass())
        : expression;
  }

  /** The binding request for this framework instance. */
  protected final BindingRequest bindingRequest() {
    return BindingRequest.bindingRequest(
        binding.key(),
        binding instanceof ProvisionBinding ? FrameworkType.PROVIDER : FrameworkType.PRODUCER_NODE);
  }

  /**
   * Returns true if the {@linkplain ContributionBinding#key() key type} is inaccessible from the
   * component, and therefore a raw type must be used.
   */
  protected final boolean useRawType() {
    return !generatedComponentModel.isTypeAccessible(binding.key().type());
  }

  @Override
  public final boolean useInnerSwitchingProvider() {
    return !binding.dependencies().isEmpty();
  }
}

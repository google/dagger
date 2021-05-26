/*
 * Copyright (C) 2017 The Dagger Authors.
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

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.javapoet.CodeBlocks;
import dagger.internal.codegen.javapoet.Expression;

/** A binding expression for a subcomponent creator that just invokes the constructor. */
final class SubcomponentCreatorBindingExpression extends SimpleInvocationBindingExpression {
  private final ComponentImplementation owningComponent;
  private final ContributionBinding binding;

  @AssistedInject
  SubcomponentCreatorBindingExpression(
      @Assisted ContributionBinding binding, ComponentImplementation owningComponent) {
    super(binding);
    this.binding = binding;
    this.owningComponent = owningComponent;
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    return Expression.create(
        binding.key().type(),
        "new $L($L)",
        owningComponent.getSubcomponentCreatorSimpleName(binding.key()),
        owningComponent.componentFieldsByImplementation().values().stream()
            .map(field -> CodeBlock.of("$N", field))
            .collect(CodeBlocks.toParametersCodeBlock()));
  }

  @AssistedFactory
  static interface Factory {
    SubcomponentCreatorBindingExpression create(ContributionBinding binding);
  }
}

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
import static dagger.internal.codegen.BindingType.PROVISION;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import dagger.model.RequestKind;
import dagger.producers.Producer;
import java.util.Optional;

/** An initializer for {@link Producer} fields that are adaptations of provision bindings. */
final class ProducerFromProviderFieldInitializer extends FrameworkFieldInitializer {

  private final ComponentBindingExpressions componentBindingExpressions;
  // TODO(ronshapiro): add Binding.bindingKey() and use that instead of taking a ResolvedBindings
  private final ResolvedBindings resolvedBindings;

  ProducerFromProviderFieldInitializer(
      ResolvedBindings resolvedBindings,
      GeneratedComponentModel generatedComponentModel,
      ComponentBindingExpressions componentBindingExpressions) {
    super(generatedComponentModel, componentBindingExpressions, resolvedBindings);
    this.componentBindingExpressions = checkNotNull(componentBindingExpressions);
    this.resolvedBindings = checkNotNull(resolvedBindings);
  }

  @Override
  protected CodeBlock getFieldInitialization() {
    return FrameworkType.PROVIDER.to(
        RequestKind.PRODUCER,
        componentBindingExpressions
            .getDependencyExpression(
                FrameworkDependency.create(resolvedBindings.key(), PROVISION),
                generatedComponentModel.name())
            .codeBlock());
  }

  @Override
  protected Optional<ClassName> alternativeFrameworkClass() {
    return Optional.of(ClassName.get(Producer.class));
  }
}

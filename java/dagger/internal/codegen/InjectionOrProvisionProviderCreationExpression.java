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
import static dagger.internal.codegen.SourceFiles.generatedClassNameForBinding;
import static dagger.model.BindingKind.INJECTION;

import com.squareup.javapoet.CodeBlock;
import dagger.internal.codegen.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;
import dagger.internal.codegen.javapoet.CodeBlocks;
import javax.inject.Provider;

/**
 * A {@link Provider} creation expression for an {@link javax.inject.Inject @Inject}-constructed
 * class or a {@link dagger.Provides @Provides}-annotated module method.
 */
// TODO(dpb): Resolve with ProducerCreationExpression.
final class InjectionOrProvisionProviderCreationExpression
    implements FrameworkInstanceCreationExpression {

  private final ContributionBinding binding;
  private final ComponentBindingExpressions componentBindingExpressions;

  InjectionOrProvisionProviderCreationExpression(
      ContributionBinding binding, ComponentBindingExpressions componentBindingExpressions) {
    this.binding = checkNotNull(binding);
    this.componentBindingExpressions = checkNotNull(componentBindingExpressions);
  }

  @Override
  public CodeBlock creationExpression() {
    CodeBlock createFactory =
        CodeBlock.of(
            "$T.create($L)",
            generatedClassNameForBinding(binding),
            componentBindingExpressions.getCreateMethodArgumentsCodeBlock(binding));

    // When scoping a parameterized factory for an @Inject class, Java 7 cannot always infer the
    // type properly, so cast to a raw framework type before scoping.
    if (binding.kind().equals(INJECTION)
        && binding.unresolved().isPresent()
        && binding.scope().isPresent()) {
      return CodeBlocks.cast(createFactory, Provider.class);
    } else {
      return createFactory;
    }
  }
}

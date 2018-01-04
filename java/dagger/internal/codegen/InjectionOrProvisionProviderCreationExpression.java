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
import static dagger.internal.codegen.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.ContributionBinding.Kind.INJECTION;
import static dagger.internal.codegen.SourceFiles.generatedClassNameForBinding;

import com.google.common.collect.Lists;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;
import dagger.internal.codegen.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;
import java.util.List;
import java.util.Optional;

/**
 * A {@link javax.inject.Provider} creation expression for an {@link
 * javax.inject.Inject @Inject}-constructed class or a {@link dagger.Provides @Provides}-annotated
 * module method.
 */
// TODO(dpb): Resolve with ProducerCreationExpression.
final class InjectionOrProvisionProviderCreationExpression
    implements FrameworkInstanceCreationExpression {

  private final ContributionBinding binding;
  private final GeneratedComponentModel generatedComponentModel;
  private final ComponentBindingExpressions componentBindingExpressions;
  private final ComponentRequirementFields componentRequirementFields;

  InjectionOrProvisionProviderCreationExpression(
      ContributionBinding binding,
      GeneratedComponentModel generatedComponentModel,
      ComponentBindingExpressions componentBindingExpressions,
      ComponentRequirementFields componentRequirementFields) {
    this.binding = checkNotNull(binding);
    this.generatedComponentModel = checkNotNull(generatedComponentModel);
    this.componentBindingExpressions = checkNotNull(componentBindingExpressions);
    this.componentRequirementFields = checkNotNull(componentRequirementFields);
  }

  @Override
  public CodeBlock creationExpression() {
    List<CodeBlock> arguments =
        Lists.newArrayListWithCapacity(binding.explicitDependencies().size() + 1);
    if (binding.requiresModuleInstance()) {
      arguments.add(
          componentRequirementFields.getExpressionDuringInitialization(
              ComponentRequirement.forModule(binding.contributingModule().get().asType()),
              generatedComponentModel.name()));
    }
    arguments.addAll(
        componentBindingExpressions.getDependencyExpressions(
            binding.frameworkDependencies(), generatedComponentModel.name()));

    CodeBlock createFactory =
        CodeBlock.of(
            "$T.create($L)",
            generatedClassNameForBinding(binding),
            makeParametersCodeBlock(arguments));

    // When scoping a parameterized factory for an @Inject class, Java 7 cannot always infer the
    // type properly, so cast to a raw framework type before scoping.
    if (binding.bindingKind().equals(INJECTION)
        && binding.unresolved().isPresent()
        && binding.scope().isPresent()) {
      return CodeBlocks.cast(createFactory, binding.bindingType().frameworkClass());
    } else {
      return createFactory;
    }
  }

  @Override
  public Optional<TypeName> specificType() {
    return Optional.of(generatedClassNameForBinding(binding));
  }
}

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

import com.squareup.javapoet.CodeBlock;
import dagger.internal.InstanceFactory;
import dagger.internal.codegen.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;

/**
 * A {@link javax.inject.Provider} creation expression for a {@linkplain
 * dagger.Component#dependencies() component dependency} or an instance passed to a {@link
 * dagger.BindsInstance @BindsInstance} builder method.
 */
final class ComponentRequirementProviderCreationExpression
    implements FrameworkInstanceCreationExpression {

  private final ContributionBinding binding;
  private final GeneratedComponentModel generatedComponentModel;
  private final ComponentRequirementFields componentRequirementFields;

  ComponentRequirementProviderCreationExpression(
      ContributionBinding binding,
      GeneratedComponentModel generatedComponentModel,
      ComponentRequirementFields componentRequirementFields) {
    this.binding = checkNotNull(binding);
    this.generatedComponentModel = checkNotNull(generatedComponentModel);
    this.componentRequirementFields = checkNotNull(componentRequirementFields);
  }

  @Override
  public CodeBlock creationExpression() {
    return CodeBlock.of(
        "$T.$L($L)",
        InstanceFactory.class,
        binding.nullableType().isPresent() ? "createNullable" : "create",
        componentRequirementFields.getExpressionDuringInitialization(
            componentRequirement(), generatedComponentModel.name()));
  }

  private ComponentRequirement componentRequirement() {
    switch (binding.kind()) {
      case COMPONENT_DEPENDENCY:
        return ComponentRequirement.forDependency(binding.key().type());

      case BOUND_INSTANCE:
        return ComponentRequirement.forBoundInstance(binding);

      default:
        throw new IllegalArgumentException(
            "binding must be for a bound instance or a dependency: " + binding);
    }
  }
}

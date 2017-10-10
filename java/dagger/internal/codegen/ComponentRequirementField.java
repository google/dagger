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

package dagger.internal.codegen;

import static com.google.common.base.Preconditions.checkNotNull;
import static javax.lang.model.element.Modifier.PRIVATE;

import com.google.common.collect.ImmutableMap;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.TypeName;

/**
 * A factory for expressions of {@link ComponentRequirement}s in the generated component. This is
 * <em>not</em> a {@link BindingExpression}, since {@link ComponentRequirement}s do not have a
 * {@link BindingKey}. See {@link BoundInstanceBindingExpression} for binding expressions that are
 * themselves a binding.
 */
abstract class ComponentRequirementField {
  private final ComponentRequirement componentRequirement;

  private ComponentRequirementField(ComponentRequirement componentRequirement) {
    this.componentRequirement = checkNotNull(componentRequirement);
  }

  final ComponentRequirement componentRequirement() {
    return componentRequirement;
  }

  /**
   * Returns an expression for the {@link ComponentRequirement} to be used when implementing a
   * component method. This may add a field to the component in order to reference the component
   * requirement outside of the {@code initialize()} methods.
   */
  abstract CodeBlock getExpression(ClassName requestingClass);

  /**
   * Returns an expression for the {@link ComponentRequirement} to be used only within {@code
   * initialize()} methods, where the component builder is available.
   *
   * <p>When accessing this field from a subcomponent, this may cause a field to be initialized in
   * the component that owns this {@link ComponentRequirement}.
   */
  abstract CodeBlock getExpressionDuringInitialization(ClassName requestingClass);

  /**
   * A {@link ComponentRequirementField} for {@link ComponentRequirement}s that have a corresponding
   * field on the component builder.
   */
  private static final class BuilderField extends ComponentRequirementField {
    private final GeneratedComponentModel generatedComponentModel;
    private final UniqueNameSet componentFieldNames;
    private final ClassName owningComponent;
    private final FieldSpec builderField;
    private MemberSelect field;

    private BuilderField(
        ComponentRequirement componentRequirement,
        GeneratedComponentModel generatedComponentModel,
        UniqueNameSet componentFieldNames,
        ClassName owningComponent,
        FieldSpec builderField) {
      super(componentRequirement);
      this.generatedComponentModel = checkNotNull(generatedComponentModel);
      this.componentFieldNames = checkNotNull(componentFieldNames);
      this.owningComponent = checkNotNull(owningComponent);
      this.builderField = checkNotNull(builderField);
    }

    @Override
    CodeBlock getExpression(ClassName requestingClass) {
      return getField().getExpressionFor(requestingClass);
    }

    @Override
    CodeBlock getExpressionDuringInitialization(ClassName requestingClass) {
      if (owningComponent.equals(requestingClass)) {
        return CodeBlock.of("builder.$N", builderField);
      } else {
        // requesting this component requirement during initialization of a child component requires
        // the it to be access from a field and not the builder (since it is no longer available)
        return getExpression(requestingClass);
      }
    }

    private MemberSelect getField() {
      if (field == null) {
        // TODO(dpb,ronshapiro): think about whether GeneratedComponentModel.addField should make a
        // unique name for the field.
        String fieldName = componentFieldNames.getUniqueName(componentRequirement().variableName());
        FieldSpec componentField =
            FieldSpec.builder(TypeName.get(componentRequirement().type()), fieldName, PRIVATE)
                .build();
        generatedComponentModel.addField(componentField);
        generatedComponentModel.addInitialization(
            CodeBlock.of("this.$N = builder.$N;", componentField, builderField));
        field = MemberSelect.localField(owningComponent, fieldName);
      }
      return field;
    }
  }

  /**
   * A {@link ComponentRequirementField} for {@link ComponentRequirement}s that have a corresponding
   * field already added on the component.
   */
  private static final class ComponentField extends ComponentRequirementField {
    private final MemberSelect memberSelect;

    private ComponentField(
        ComponentRequirement componentRequirement,
        FieldSpec componentField,
        ClassName owningComponent) {
      super(componentRequirement);
      this.memberSelect = MemberSelect.localField(owningComponent, componentField.name);
    }

    @Override
    CodeBlock getExpression(ClassName requestingClass) {
      return memberSelect.getExpressionFor(requestingClass);
    }

    @Override
    CodeBlock getExpressionDuringInitialization(ClassName requestingClass) {
      return getExpression(requestingClass);
    }
  }

  static final class Factory {
    private final GeneratedComponentModel generatedComponentModel;
    private final UniqueNameSet componentFieldNames;
    private final ClassName owningComponent;
    private final ImmutableMap<ComponentRequirement, FieldSpec> builderFields;

    Factory(
        GeneratedComponentModel generatedComponentModel,
        UniqueNameSet componentFieldNames,
        ClassName owningComponent,
        ImmutableMap<ComponentRequirement, FieldSpec> builderFields) {
      this.generatedComponentModel = checkNotNull(generatedComponentModel);
      this.componentFieldNames = checkNotNull(componentFieldNames);
      this.owningComponent = checkNotNull(owningComponent);
      this.builderFields = checkNotNull(builderFields);
    }

    /**
     * Returns a {@link ComponentRequirementField} for {@link ComponentRequirement}s that have a
     * corresponding field on the component builder.
     */
    ComponentRequirementField forBuilderField(ComponentRequirement componentRequirement) {
      return new BuilderField(
          componentRequirement,
          generatedComponentModel,
          componentFieldNames,
          owningComponent,
          builderFields.get(componentRequirement));
    }
  }

  /**
   * Returns a {@link ComponentRequirementField} for {@link ComponentRequirement}s that have a
   * corresponding field already added on the component.
   */
  static ComponentRequirementField componentField(
      ComponentRequirement componentRequirement,
      FieldSpec componentField,
      ClassName owningComponent) {
    return new ComponentField(componentRequirement, componentField, owningComponent);
  }
}

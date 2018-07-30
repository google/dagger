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
import static com.google.common.base.Suppliers.memoize;
import static dagger.internal.codegen.GeneratedComponentModel.FieldSpecKind.COMPONENT_REQUIREMENT_FIELD;
import static javax.lang.model.element.Modifier.PRIVATE;

import com.google.common.base.Supplier;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import dagger.internal.Preconditions;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A central repository of fields used to access any {@link ComponentRequirement} available to a
 * component.
 */
final class ComponentRequirementFields {

  // TODO(dpb,ronshapiro): refactor this and ComponentBindingExpressions into a
  // HierarchicalComponentMap<K, V>, or perhaps this use a flattened ImmutableMap, built from its
  // parents? If so, maybe make ComponentRequirementField.Factory create it.

  private final Optional<ComponentRequirementFields> parent;
  private final Map<ComponentRequirement, ComponentRequirementField> componentRequirementFields =
      new HashMap<>();
  private final BindingGraph graph;
  private final GeneratedComponentModel generatedComponentModel;
  private final Optional<ComponentBuilder> componentBuilder;

  private ComponentRequirementFields(
      Optional<ComponentRequirementFields> parent,
      BindingGraph graph,
      GeneratedComponentModel generatedComponentModel,
      Optional<ComponentBuilder> componentBuilder) {
    this.parent = parent;
    this.graph = graph;
    this.generatedComponentModel = generatedComponentModel;
    this.componentBuilder = componentBuilder;
  }

  ComponentRequirementFields(
      BindingGraph graph,
      GeneratedComponentModel generatedComponentModel,
      Optional<ComponentBuilder> componentBuilder) {
    this(Optional.empty(), graph, generatedComponentModel, componentBuilder);
  }

  /** Returns a new object representing the fields available from a child component of this one. */
  ComponentRequirementFields forChildComponent(
      BindingGraph graph,
      GeneratedComponentModel generatedComponentModel,
      Optional<ComponentBuilder> componentBuilder) {
    return new ComponentRequirementFields(
        Optional.of(this), graph, generatedComponentModel, componentBuilder);
  }

  /**
   * Returns an expression for the {@code componentRequirement} to be used when implementing a
   * component method. This may add a field to the component in order to reference the component
   * requirement outside of the {@code initialize()} methods.
   */
  CodeBlock getExpression(ComponentRequirement componentRequirement, ClassName requestingClass) {
    return getField(componentRequirement).getExpression(requestingClass);
  }

  /**
   * Returns an expression for the {@code componentRequirement} to be used only within {@code
   * initialize()} methods, where the component builder is available.
   *
   * <p>When accessing this field from a subcomponent, this may cause a field to be initialized in
   * the component that owns this {@link ComponentRequirement}.
   */
  CodeBlock getExpressionDuringInitialization(
      ComponentRequirement componentRequirement, ClassName requestingClass) {
    return getField(componentRequirement).getExpressionDuringInitialization(requestingClass);
  }

  ComponentRequirementField getField(ComponentRequirement componentRequirement) {
    if (graph.componentRequirements().contains(componentRequirement)) {
      return componentRequirementFields.computeIfAbsent(componentRequirement, this::create);
    }
    if (parent.isPresent()) {
      return parent.get().getField(componentRequirement);
    }
    throw new IllegalStateException(
        "no component requirement field found for " + componentRequirement);
  }

  /** Returns a {@link ComponentRequirementField} for a {@link ComponentRequirement}. */
  private ComponentRequirementField create(ComponentRequirement requirement) {
    if (componentBuilder.isPresent()) {
      FieldSpec builderField = componentBuilder.get().builderFields().get(requirement);
      return new BuilderField(requirement, generatedComponentModel, builderField);
    } else if (graph.factoryMethod().isPresent()
        && graph.factoryMethodParameters().containsKey(requirement)) {
      ParameterSpec factoryParameter =
          ParameterSpec.get(graph.factoryMethodParameters().get(requirement));
      return new ComponentParameterField(requirement, generatedComponentModel, factoryParameter);
    } else if (graph.componentRequirements().contains(requirement)) {
      return new ComponentInstantiableField(requirement, generatedComponentModel);
    } else {
      throw new AssertionError();
    }
  }

  private abstract static class AbstractField implements ComponentRequirementField {
    private final ComponentRequirement componentRequirement;
    private final GeneratedComponentModel generatedComponentModel;
    private final Supplier<MemberSelect> field = memoize(this::createField);

    private AbstractField(
        ComponentRequirement componentRequirement,
        GeneratedComponentModel generatedComponentModel) {
      this.componentRequirement = checkNotNull(componentRequirement);
      this.generatedComponentModel = checkNotNull(generatedComponentModel);
    }

    @Override
    public CodeBlock getExpression(ClassName requestingClass) {
      return field.get().getExpressionFor(requestingClass);
    }

    @Override
    public CodeBlock getExpressionDuringInitialization(ClassName requestingClass) {
      return getExpression(requestingClass);
    }

    private MemberSelect createField() {
      // TODO(dpb,ronshapiro): think about whether GeneratedComponentModel.addField
      // should make a unique name for the field.
      String fieldName =
          generatedComponentModel.getUniqueFieldName(componentRequirement.variableName());
      FieldSpec field =
          FieldSpec.builder(TypeName.get(componentRequirement.type()), fieldName, PRIVATE).build();
      generatedComponentModel.addField(COMPONENT_REQUIREMENT_FIELD, field);
      generatedComponentModel.addInitialization(fieldInitialization(field));
      return MemberSelect.localField(generatedComponentModel.name(), fieldName);
    }

    /** Returns the {@link CodeBlock} that initializes the component field during construction. */
    abstract CodeBlock fieldInitialization(FieldSpec componentField);
  }

  /**
   * A {@link ComponentRequirementField} for {@link ComponentRequirement}s that have a corresponding
   * field on the component builder.
   */
  private static final class BuilderField extends AbstractField {
    private final FieldSpec builderField;

    private BuilderField(
        ComponentRequirement componentRequirement,
        GeneratedComponentModel generatedComponentModel,
        FieldSpec builderField) {
      super(componentRequirement, generatedComponentModel);
      this.builderField = checkNotNull(builderField);
    }

    @Override
    public CodeBlock getExpressionDuringInitialization(ClassName requestingClass) {
      if (super.generatedComponentModel.name().equals(requestingClass)) {
        return CodeBlock.of("builder.$N", builderField);
      } else {
        // requesting this component requirement during initialization of a child component requires
        // the it to be access from a field and not the builder (since it is no longer available)
        return getExpression(requestingClass);
      }
    }

    @Override
    CodeBlock fieldInitialization(FieldSpec componentField) {
      return CodeBlock.of("this.$N = builder.$N;", componentField, builderField);
    }
  }

  /**
   * A {@link ComponentRequirementField} for {@link ComponentRequirement}s that can be instantiated
   * by the component (i.e. a static class with a no-arg constructor).
   */
  private static final class ComponentInstantiableField extends AbstractField {
    private ComponentInstantiableField(
        ComponentRequirement componentRequirement,
        GeneratedComponentModel generatedComponentModel) {
      super(componentRequirement, generatedComponentModel);
    }

    @Override
    CodeBlock fieldInitialization(FieldSpec componentField) {
      return CodeBlock.of("this.$N = new $T();", componentField, componentField.type);
    }
  }

  /**
   * A {@link ComponentRequirementField} for {@link ComponentRequirement}s that are passed in
   * as parameters to a component factory method.
   */
  private static final class ComponentParameterField extends AbstractField {
    private final ParameterSpec factoryParameter;

    private ComponentParameterField(
        ComponentRequirement componentRequirement,
        GeneratedComponentModel generatedComponentModel,
        ParameterSpec factoryParameter) {
      super(componentRequirement, generatedComponentModel);
      this.factoryParameter = checkNotNull(factoryParameter);
    }

    @Override
    CodeBlock fieldInitialization(FieldSpec componentField) {
      return CodeBlock.of(
          "this.$N = $T.checkNotNull($N);", componentField, Preconditions.class, factoryParameter);
    }
  }
}

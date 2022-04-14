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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Suppliers.memoize;
import static dagger.internal.codegen.writing.ComponentImplementation.FieldSpecKind.COMPONENT_REQUIREMENT_FIELD;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;

import androidx.room.compiler.processing.XTypeElement;
import com.google.common.base.Supplier;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.TypeName;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.ComponentRequirement;
import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;

/**
 * A central repository of expressions used to access any {@link ComponentRequirement} available to
 * a component.
 */
@PerComponentImplementation
public final class ComponentRequirementExpressions {

  // TODO(dpb,ronshapiro): refactor this and ComponentRequestRepresentations into a
  // HierarchicalComponentMap<K, V>, or perhaps this use a flattened ImmutableMap, built from its
  // parents? If so, maybe make ComponentRequirementExpression.Factory create it.

  private final Optional<ComponentRequirementExpressions> parent;
  private final Map<ComponentRequirement, ComponentRequirementExpression>
      componentRequirementExpressions = new HashMap<>();
  private final BindingGraph graph;
  private final ShardImplementation componentShard;

  @Inject
  ComponentRequirementExpressions(
      @ParentComponent Optional<ComponentRequirementExpressions> parent,
      BindingGraph graph,
      ComponentImplementation componentImplementation) {
    this.parent = parent;
    this.graph = graph;
    // All component requirements go in the componentShard.
    this.componentShard = componentImplementation.getComponentShard();
  }

  /**
   * Returns an expression for the {@code componentRequirement} to be used when implementing a
   * component method. This may add a field or method to the component in order to reference the
   * component requirement outside of the {@code initialize()} methods.
   */
  CodeBlock getExpression(ComponentRequirement componentRequirement, ClassName requestingClass) {
    return getExpression(componentRequirement).getExpression(requestingClass);
  }

  private ComponentRequirementExpression getExpression(ComponentRequirement componentRequirement) {
    if (graph.componentRequirements().contains(componentRequirement)) {
      return componentRequirementExpressions.computeIfAbsent(
          componentRequirement, this::createExpression);
    }
    if (parent.isPresent()) {
      return parent.get().getExpression(componentRequirement);
    }

    throw new IllegalStateException(
        "no component requirement expression found for " + componentRequirement);
  }

  /**
   * Returns an expression for the {@code componentRequirement} to be used only within {@code
   * initialize()} methods, where the component constructor parameters are available.
   *
   * <p>When accessing this expression from a subcomponent, this may cause a field to be initialized
   * or a method to be added in the component that owns this {@link ComponentRequirement}.
   */
  CodeBlock getExpressionDuringInitialization(
      ComponentRequirement componentRequirement, ClassName requestingClass) {
    return getExpression(componentRequirement).getExpressionDuringInitialization(requestingClass);
  }

  /** Returns a field for a {@link ComponentRequirement}. */
  private ComponentRequirementExpression createExpression(ComponentRequirement requirement) {
    if (componentShard.componentDescriptor().hasCreator()
        || (graph.factoryMethod().isPresent()
            && graph.factoryMethodParameters().containsKey(requirement))) {
      return new ComponentParameterField(requirement);
    } else if (requirement.kind().isModule()) {
      return new InstantiableModuleField(requirement);
    } else {
      throw new AssertionError(
          String.format("Can't create %s in %s", requirement, componentShard.name()));
    }
  }

  private abstract class AbstractField implements ComponentRequirementExpression {
    final ComponentRequirement componentRequirement;
    private final Supplier<MemberSelect> field = memoize(this::createField);

    private AbstractField(ComponentRequirement componentRequirement) {
      this.componentRequirement = checkNotNull(componentRequirement);
    }

    @Override
    public CodeBlock getExpression(ClassName requestingClass) {
      return field.get().getExpressionFor(requestingClass);
    }

    private MemberSelect createField() {
      String fieldName = componentShard.getUniqueFieldName(componentRequirement.variableName());
      TypeName fieldType = componentRequirement.type().getTypeName();
      FieldSpec field = FieldSpec.builder(fieldType, fieldName, PRIVATE, FINAL).build();
      componentShard.addField(COMPONENT_REQUIREMENT_FIELD, field);
      componentShard.addComponentRequirementInitialization(fieldInitialization(field));
      return MemberSelect.localField(componentShard, fieldName);
    }

    /** Returns the {@link CodeBlock} that initializes the component field during construction. */
    abstract CodeBlock fieldInitialization(FieldSpec componentField);
  }

  /**
   * A {@link ComponentRequirementExpression} for {@link ComponentRequirement}s that can be
   * instantiated by the component (i.e. a static class with a no-arg constructor).
   */
  private final class InstantiableModuleField extends AbstractField {
    private final XTypeElement moduleElement;

    InstantiableModuleField(ComponentRequirement module) {
      super(module);
      checkArgument(module.kind().isModule());
      this.moduleElement = module.typeElement();
    }

    @Override
    CodeBlock fieldInitialization(FieldSpec componentField) {
      return CodeBlock.of(
          "this.$N = $L;",
          componentField,
          ModuleProxies.newModuleInstance(moduleElement, componentShard.name()));
    }
  }

  /**
   * A {@link ComponentRequirementExpression} for {@link ComponentRequirement}s that are passed in
   * as parameters to the component's constructor.
   */
  private final class ComponentParameterField extends AbstractField {
    private final String parameterName;

    ComponentParameterField(ComponentRequirement module) {
      super(module);
      this.parameterName = componentShard.getParameterName(componentRequirement);
    }

    @Override
    public CodeBlock getExpressionDuringInitialization(ClassName requestingClass) {
      if (componentShard.name().equals(requestingClass)) {
        return CodeBlock.of("$L", parameterName);
      } else {
        // requesting this component requirement during initialization of a child component requires
        // it to be accessed from a field and not the parameter (since it is no longer available)
        return getExpression(requestingClass);
      }
    }

    @Override
    CodeBlock fieldInitialization(FieldSpec componentField) {
      // Don't checkNotNull here because the parameter may be nullable; if it isn't, the caller
      // should handle checking that before passing the parameter.
      return CodeBlock.of("this.$N = $L;", componentField, parameterName);
    }
  }
}

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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Suppliers.memoize;
import static dagger.internal.codegen.ComponentImplementation.FieldSpecKind.COMPONENT_REQUIREMENT_FIELD;
import static dagger.internal.codegen.ModuleProxies.newModuleInstance;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;

import com.google.common.base.Supplier;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.TypeName;
import dagger.internal.codegen.langmodel.DaggerElements;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.lang.model.element.TypeElement;

/**
 * A central repository of expressions used to access any {@link ComponentRequirement} available to
 * a component.
 */
@PerComponentImplementation
final class ComponentRequirementExpressions {

  // TODO(dpb,ronshapiro): refactor this and ComponentBindingExpressions into a
  // HierarchicalComponentMap<K, V>, or perhaps this use a flattened ImmutableMap, built from its
  // parents? If so, maybe make ComponentRequirementExpression.Factory create it.

  private final Optional<ComponentRequirementExpressions> parent;
  private final Map<ComponentRequirement, ComponentRequirementExpression>
      componentRequirementExpressions = new HashMap<>();
  private final BindingGraph graph;
  private final ComponentImplementation componentImplementation;
  private final DaggerElements elements;

  // TODO(ronshapiro): give ComponentImplementation a graph() method
  @Inject
  ComponentRequirementExpressions(
      @ParentComponent Optional<ComponentRequirementExpressions> parent,
      BindingGraph graph,
      ComponentImplementation componentImplementation,
      DaggerElements elements) {
    this.parent = parent;
    this.graph = graph;
    this.componentImplementation = componentImplementation;
    this.elements = elements;
  }

  /**
   * Returns an expression for the {@code componentRequirement} to be used when implementing a
   * component method. This may add a field or method to the component in order to reference the
   * component requirement outside of the {@code initialize()} methods.
   */
  CodeBlock getExpression(ComponentRequirement componentRequirement, ClassName requestingClass) {
    return getExpression(componentRequirement).getExpression(requestingClass);
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

  ComponentRequirementExpression getExpression(ComponentRequirement componentRequirement) {
    if (graph.componentRequirements().contains(componentRequirement)) {
      return componentRequirementExpressions.computeIfAbsent(
          componentRequirement, this::createField);
    }
    if (parent.isPresent()) {
      return parent.get().getExpression(componentRequirement);
    }

    throw new IllegalStateException(
        "no component requirement expression found for " + componentRequirement);
  }

  /** Returns a field for a {@link ComponentRequirement}. */
  private ComponentRequirementExpression createField(ComponentRequirement requirement) {
    if (componentImplementation.componentDescriptor().hasCreator()) {
      return new ComponentParameterField(requirement, componentImplementation, Optional.empty());
    } else if (graph.factoryMethod().isPresent()
        && graph.factoryMethodParameters().containsKey(requirement)) {
      String parameterName =
          graph.factoryMethodParameters().get(requirement).getSimpleName().toString();
      return new ComponentParameterField(
          requirement, componentImplementation, Optional.of(parameterName));
    } else if (requirement.kind().isModule()) {
      return new InstantiableModuleField(requirement, componentImplementation);
    } else {
      throw new AssertionError(
          String.format("Can't create %s in %s", requirement, componentImplementation.name()));
    }
  }

  private abstract static class AbstractField implements ComponentRequirementExpression {
    final ComponentRequirement componentRequirement;
    final ComponentImplementation componentImplementation;
    final String fieldName;
    private final Supplier<MemberSelect> field = memoize(this::addField);

    private AbstractField(
        ComponentRequirement componentRequirement,
        ComponentImplementation componentImplementation) {
      this.componentRequirement = checkNotNull(componentRequirement);
      this.componentImplementation = checkNotNull(componentImplementation);
      // Note: The field name is being claimed eagerly here even though we don't know at this point
      // whether or not the requirement will even need a field. This is done because:
      // A) ComponentParameterField wants to ensure that it doesn't give the parameter the same name
      //    as any field in the component, which requires that it claim a "field name" for itself
      //    when naming the parameter.
      // B) The parameter name may be needed before the field name is.
      // C) We want to prefer giving the best name to the field rather than the parameter given its
      //    wider scope.
      this.fieldName =
          componentImplementation.getUniqueFieldName(componentRequirement.variableName());
    }

    @Override
    public CodeBlock getExpression(ClassName requestingClass) {
      return field.get().getExpressionFor(requestingClass);
    }

    private MemberSelect addField() {
      FieldSpec field = createField();
      componentImplementation.addField(COMPONENT_REQUIREMENT_FIELD, field);
      componentImplementation.addComponentRequirementInitialization(fieldInitialization(field));
      return MemberSelect.localField(componentImplementation.name(), fieldName);
    }

    private FieldSpec createField() {
      return FieldSpec.builder(TypeName.get(componentRequirement.type()), fieldName, PRIVATE, FINAL)
          .build();
    }

    /** Returns the {@link CodeBlock} that initializes the component field during construction. */
    abstract CodeBlock fieldInitialization(FieldSpec componentField);
  }

  /**
   * A {@link ComponentRequirementExpression} for {@link ComponentRequirement}s that can be
   * instantiated by the component (i.e. a static class with a no-arg constructor).
   */
  private final class InstantiableModuleField extends AbstractField {
    private final TypeElement moduleElement;

    private InstantiableModuleField(
        ComponentRequirement module, ComponentImplementation componentImplementation) {
      super(module, componentImplementation);
      checkArgument(module.kind().isModule());
      this.moduleElement = module.typeElement();
    }

    @Override
    CodeBlock fieldInitialization(FieldSpec componentField) {
      return CodeBlock.of(
          "this.$N = $L;",
          componentField,
          newModuleInstance(moduleElement, componentImplementation.name(), elements));
    }
  }

  /**
   * A {@link ComponentRequirementExpression} for {@link ComponentRequirement}s that are passed in
   * as parameters to the component's constructor.
   */
  private static final class ComponentParameterField extends AbstractField {
    private final String parameterName;

    private ComponentParameterField(
        ComponentRequirement componentRequirement,
        ComponentImplementation componentImplementation,
        Optional<String> name) {
      super(componentRequirement, componentImplementation);
      // Get the name that the component implementation will use for its parameter for the
      // requirement. If the given name is different than the name of the field created for the
      // requirement (as may be the case when the parameter name is derived from a user-written
      // factory method parameter), just use that as the base name for the parameter. Otherwise,
      // append "Param" to the end of the name to differentiate.
      // In either case, componentImplementation.getParameterName() will ensure that the final name
      // that is used is not the same name as any field in the component even if there's something
      // weird where the component actually has fields named, say, "foo" and "fooParam".
      String baseName = name.filter(n -> !n.equals(fieldName)).orElse(fieldName + "Param");
      this.parameterName = componentImplementation.getParameterName(componentRequirement, baseName);
    }

    @Override
    public CodeBlock getExpressionDuringInitialization(ClassName requestingClass) {
      if (componentImplementation.name().equals(requestingClass)) {
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

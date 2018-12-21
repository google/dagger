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

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Suppliers.memoize;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static dagger.internal.codegen.ComponentImplementation.FieldSpecKind.COMPONENT_REQUIREMENT_FIELD;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import dagger.internal.Preconditions;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;

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
  private final CompilerOptions compilerOptions;

  // TODO(ronshapiro): give ComponentImplementation a graph() method
  @Inject
  ComponentRequirementExpressions(
      @ParentComponent Optional<ComponentRequirementExpressions> parent,
      BindingGraph graph,
      ComponentImplementation componentImplementation,
      CompilerOptions compilerOptions) {
    this.parent = parent;
    this.graph = graph;
    this.componentImplementation = componentImplementation;
    this.compilerOptions = compilerOptions;
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
   * initialize()} methods, where the component builder is available.
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
          componentRequirement, this::createMethodOrField);
    }
    if (parent.isPresent()) {
      return parent.get().getExpression(componentRequirement);
    }

    if (componentRequirement.kind().isModule() && compilerOptions.aheadOfTimeSubcomponents()) {
      return new PrunedModifiableModule(componentRequirement);
    }

    throw new IllegalStateException(
        "no component requirement expression found for " + componentRequirement);
  }

  /**
   * If {@code requirement} is a module that may be owned by a future ancestor component, returns a
   * modifiable module method. Otherwise, returns a field for {@code requirement}.
   */
  private ComponentRequirementExpression createMethodOrField(ComponentRequirement requirement) {
    if (componentImplementation.isAbstract() && requirement.kind().isModule()) {
      return new ModifiableModule(requirement);
    }
    return createField(requirement);
  }

  /** Returns a field for a {@link ComponentRequirement}. */
  private ComponentRequirementExpression createField(ComponentRequirement requirement) {
    Optional<ComponentCreatorImplementation> creatorImplementation =
        Optionals.firstPresent(
            componentImplementation.baseImplementation().flatMap(c -> c.creatorImplementation()),
            componentImplementation.creatorImplementation());
    if (creatorImplementation.isPresent()) {
      FieldSpec builderField = creatorImplementation.get().builderFields().get(requirement);
      return new BuilderField(requirement, componentImplementation, builderField);
    } else if (graph.factoryMethod().isPresent()
        && graph.factoryMethodParameters().containsKey(requirement)) {
      ParameterSpec factoryParameter =
          ParameterSpec.get(graph.factoryMethodParameters().get(requirement));
      return new ComponentParameterField(requirement, componentImplementation, factoryParameter);
    } else if (requirement.kind().isModule()) {
      return new ComponentInstantiableField(requirement, componentImplementation);
    } else {
      throw new AssertionError(
          String.format("Can't create %s in %s", requirement, componentImplementation.name()));
    }
  }

  private abstract static class AbstractField implements ComponentRequirementExpression {
    private final ComponentRequirement componentRequirement;
    private final ComponentImplementation componentImplementation;
    private final Supplier<MemberSelect> field = memoize(this::createField);

    private AbstractField(
        ComponentRequirement componentRequirement,
        ComponentImplementation componentImplementation) {
      this.componentRequirement = checkNotNull(componentRequirement);
      this.componentImplementation = checkNotNull(componentImplementation);
    }

    @Override
    public CodeBlock getExpression(ClassName requestingClass) {
      return field.get().getExpressionFor(requestingClass);
    }

    private MemberSelect createField() {
      // TODO(dpb,ronshapiro): think about whether ComponentImplementation.addField
      // should make a unique name for the field.
      String fieldName =
          componentImplementation.getUniqueFieldName(componentRequirement.variableName());
      FieldSpec field =
          FieldSpec.builder(TypeName.get(componentRequirement.type()), fieldName, PRIVATE).build();
      componentImplementation.addField(COMPONENT_REQUIREMENT_FIELD, field);
      componentImplementation.addComponentRequirementInitialization(fieldInitialization(field));
      return MemberSelect.localField(componentImplementation.name(), fieldName);
    }

    /** Returns the {@link CodeBlock} that initializes the component field during construction. */
    abstract CodeBlock fieldInitialization(FieldSpec componentField);
  }

  /**
   * A {@link ComponentRequirementExpression} for {@link ComponentRequirement}s that have a
   * corresponding field on the component builder.
   */
  private static final class BuilderField extends AbstractField {
    private final FieldSpec builderField;

    private BuilderField(
        ComponentRequirement componentRequirement,
        ComponentImplementation componentImplementation,
        FieldSpec builderField) {
      super(componentRequirement, componentImplementation);
      this.builderField = checkNotNull(builderField);
    }

    @Override
    public CodeBlock getExpressionDuringInitialization(ClassName requestingClass) {
      if (super.componentImplementation.name().equals(requestingClass)) {
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
   * A {@link ComponentRequirementExpression} for {@link ComponentRequirement}s that can be
   * instantiated by the component (i.e. a static class with a no-arg constructor).
   */
  private static final class ComponentInstantiableField extends AbstractField {
    private ComponentInstantiableField(
        ComponentRequirement componentRequirement,
        ComponentImplementation componentImplementation) {
      super(componentRequirement, componentImplementation);
    }

    @Override
    CodeBlock fieldInitialization(FieldSpec componentField) {
      return CodeBlock.of("this.$N = new $T();", componentField, componentField.type);
    }
  }

  /**
   * A {@link ComponentRequirementExpression} for {@link ComponentRequirement}s that are passed in
   * as parameters to a component factory method.
   */
  private static final class ComponentParameterField extends AbstractField {
    private final ParameterSpec factoryParameter;

    private ComponentParameterField(
        ComponentRequirement componentRequirement,
        ComponentImplementation componentImplementation,
        ParameterSpec factoryParameter) {
      super(componentRequirement, componentImplementation);
      this.factoryParameter = checkNotNull(factoryParameter);
    }

    @Override
    CodeBlock fieldInitialization(FieldSpec componentField) {
      return CodeBlock.of(
          "this.$N = $T.checkNotNull($N);", componentField, Preconditions.class, factoryParameter);
    }
  }

  private final class ModifiableModule implements ComponentRequirementExpression {
    private final ComponentRequirement module;
    private final Supplier<MemberSelect> method = Suppliers.memoize(this::methodSelect);

    private ModifiableModule(ComponentRequirement module) {
      checkArgument(module.kind().isModule());
      this.module = module;
    }

    @Override
    public CodeBlock getExpression(ClassName requestingClass) {
      return method.get().getExpressionFor(requestingClass);
    }

    private MemberSelect methodSelect() {
      String methodName =
          componentImplementation
              .supertypeModifiableModuleMethodName(module)
              .orElseGet(this::createMethod);
      return MemberSelect.localMethod(componentImplementation.name(), methodName);
    }

    private String createMethod() {
      String methodName =
          UPPER_CAMEL.to(
              LOWER_CAMEL,
              componentImplementation.getUniqueMethodName(
                  module.typeElement().getSimpleName().toString()));
      MethodSpec.Builder methodBuilder =
          methodBuilder(methodName)
              .addModifiers(PROTECTED)
              .returns(TypeName.get(module.type()));
      // TODO(b/117833324): if the module is instantiable, we could provide an implementation here
      // too. Then, if no ancestor ever repeats the module, there's nothing to do in subclasses.
      if (graph.componentDescriptor().creatorDescriptor().isPresent()) {
        methodBuilder.addStatement(
            "return $L",
            createField(module).getExpression(componentImplementation.name()));
      } else {
        methodBuilder.addModifiers(ABSTRACT);
      }
      componentImplementation.addModifiableModuleMethod(module, methodBuilder.build());
      return methodName;
    }
  }

  private static final class PrunedModifiableModule implements ComponentRequirementExpression {
    private final ComponentRequirement module;

    private PrunedModifiableModule(ComponentRequirement module) {
      checkArgument(module.kind().isModule());
      this.module = module;
    }

    @Override
    public CodeBlock getExpression(ClassName requestingClass) {
      throw new UnsupportedOperationException(module + " is pruned - it cannot be requested");
    }

    @Override
    public CodeBlock getModifiableModuleMethodExpression(ClassName requestingClass) {
      return CodeBlock.of(
          "throw new UnsupportedOperationException($T.class + $S)",
          module.typeElement(),
          " has been pruned from the final resolved binding graph. If this exception is thrown, "
              + "it is a cause of a Dagger bug - please report it!");
    }
  }
}

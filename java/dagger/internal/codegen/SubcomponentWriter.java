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

import static com.google.auto.common.MoreTypes.asDeclared;
import static com.google.auto.common.MoreTypes.asExecutable;
import static com.google.auto.common.MoreTypes.asTypeElement;
import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static dagger.internal.codegen.CodeBlocks.toParametersCodeBlock;
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.GeneratedComponentModel.FieldSpecKind.COMPONENT_REQUIREMENT_FIELD;
import static dagger.internal.codegen.GeneratedComponentModel.MethodSpecKind.COMPONENT_METHOD;
import static dagger.internal.codegen.GeneratedComponentModel.TypeSpecKind.SUBCOMPONENT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;

import com.google.common.base.CaseFormat;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.Preconditions;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

/**
 * Creates the nested implementation class for a subcomponent.
 */
final class SubcomponentWriter extends AbstractComponentWriter {

  private final AbstractComponentWriter parent;

  /**
   * The parent's factory method to create this subcomponent, or {@link Optional#empty()} if the
   * subcomponent was added via {@link dagger.Module#subcomponents()}.
   */
  private final Optional<ExecutableElement> subcomponentFactoryMethod;

  SubcomponentWriter(AbstractComponentWriter parent, BindingGraph graph) {
    super(
        parent,
        subcomponentModel(parent, graph),
        graph,
        parent.componentRequirementFields.forChildComponent());
    this.parent = parent;
    this.subcomponentFactoryMethod =
        Optional.ofNullable(
                parent
                    .graph
                    .componentDescriptor()
                    .subcomponentsByFactoryMethod()
                    .inverse()
                    .get(graph.componentDescriptor()))
            .map(method -> method.methodElement());
  }

  private static GeneratedComponentModel subcomponentModel(
      AbstractComponentWriter parent, BindingGraph graph) {
    ClassName parentName = parent.generatedComponentModel.name();
    ClassName name =
        parentName.nestedClass(parent.subcomponentNames.get(graph.componentDescriptor()) + "Impl");
    return GeneratedComponentModel.forSubcomponent(name);
  }

  @Override
  protected void addBuilderClass(TypeSpec builder) {
    parent.generatedComponentModel.addType(SUBCOMPONENT, builder);
  }

  @Override
  protected void addFactoryMethods() {
    subcomponentFactoryMethod.ifPresent(this::createSubcomponentFactoryMethod);
  }

  private void createSubcomponentFactoryMethod(ExecutableElement factoryMethod) {
    parent.generatedComponentModel.addMethod(
        COMPONENT_METHOD,
        MethodSpec.overriding(factoryMethod, parentType(), types)
            .addStatement(
                "return new $T($L)",
                generatedComponentModel.name(),
                factoryMethod
                    .getParameters()
                    .stream()
                    .map(param -> CodeBlock.of("$L", param.getSimpleName()))
                    .collect(toParametersCodeBlock()))
            .build());

    writeSubcomponentConstructorFor(factoryMethod);
  }

  private void writeSubcomponentConstructorFor(ExecutableElement factoryMethod) {
    Set<ComponentRequirement> modules =
        asExecutable(types.asMemberOf(parentType(), factoryMethod))
            .getParameterTypes()
            .stream()
            .map(param -> ComponentRequirement.forModule(asTypeElement(param).asType()))
            .collect(toImmutableSet());

    for (ComponentRequirement module : modules) {
      FieldSpec field = createSubcomponentModuleField(module);
      constructor
          .addParameter(field.type, field.name)
          .addStatement("this.$1N = $2T.checkNotNull($1N)", field, Preconditions.class);
    }

    Set<ComponentRequirement> remainingModules =
        graph
            .componentRequirements()
            .stream()
            .filter(requirement -> requirement.kind().equals(ComponentRequirement.Kind.MODULE))
            .filter(requirement -> !modules.contains(requirement))
            .collect(toImmutableSet());

    for (ComponentRequirement module : remainingModules) {
      FieldSpec field = createSubcomponentModuleField(module);
      constructor.addStatement("this.$N = new $T()", field, field.type);
    }
  }

  // TODO(user): We shouldn't have to create these manually. They should be created lazily
  // by ComponentRequirementFields, similar to how it's done for ComponentBindingExpressions.
  private FieldSpec createSubcomponentModuleField(ComponentRequirement module) {
    TypeElement moduleElement = module.typeElement();
    String fieldName =
        generatedComponentModel.getUniqueFieldName(
            CaseFormat.UPPER_CAMEL.to(LOWER_CAMEL, moduleElement.getSimpleName().toString()));
    FieldSpec contributionField =
        FieldSpec.builder(ClassName.get(moduleElement), fieldName)
            .addModifiers(PRIVATE, FINAL).build();

    generatedComponentModel.addField(COMPONENT_REQUIREMENT_FIELD, contributionField);
    componentRequirementFields.add(
        ComponentRequirementField.componentField(
            module, contributionField, generatedComponentModel.name()));

    return contributionField;
  }

  private DeclaredType parentType() {
    return asDeclared(parent.graph.componentType().asType());
  }
}

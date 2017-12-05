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

import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static dagger.internal.codegen.GeneratedComponentModel.MethodSpecKind.BUILDER_METHOD;
import static dagger.internal.codegen.GeneratedComponentModel.TypeSpecKind.COMPONENT_BUILDER;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.common.collect.Iterables;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import javax.lang.model.util.Elements;

/**
 * Creates the implementation class for a component.
 */
final class ComponentWriter extends AbstractComponentWriter {
  static TypeSpec.Builder write(
      DaggerTypes types,
      Elements elements,
      KeyFactory keyFactory,
      CompilerOptions compilerOptions,
      ClassName name,
      BindingGraph graph) {
    GeneratedComponentModel generatedComponentModel = GeneratedComponentModel.forComponent(name);
    SubcomponentNames subcomponentNames = new SubcomponentNames(graph, keyFactory);
    ComponentRequirementFields componentRequirementFields = new ComponentRequirementFields();
    OptionalFactories optionalFactories = new OptionalFactories();
    ComponentBindingExpressions bindingExpressions =
        new ComponentBindingExpressions(
            graph,
            generatedComponentModel,
            subcomponentNames,
            componentRequirementFields,
            optionalFactories,
            types,
            elements,
            compilerOptions);
    return new ComponentWriter(
            types,
            elements,
            compilerOptions,
            graph,
            generatedComponentModel,
            subcomponentNames,
            bindingExpressions,
            componentRequirementFields,
            optionalFactories)
        .write();
  }

  private ComponentWriter(
      DaggerTypes types,
      Elements elements,
      CompilerOptions compilerOptions,
      BindingGraph graph,
      GeneratedComponentModel generatedComponentModel,
      SubcomponentNames subcomponentNames,
      ComponentBindingExpressions bindingExpressions,
      ComponentRequirementFields componentRequirementFields,
      OptionalFactories optionalFactories) {
    super(
        types,
        elements,
        compilerOptions,
        graph,
        generatedComponentModel,
        subcomponentNames,
        optionalFactories,
        bindingExpressions,
        componentRequirementFields);
  }

  private void addBuilderFactoryMethod() {
    // Only top-level components have the factory builder() method.
    // Mirror the user's builder API type if they had one.
    MethodSpec builderFactoryMethod =
        methodBuilder("builder")
            .addModifiers(PUBLIC, STATIC)
            .returns(
                graph.componentDescriptor().builderSpec().isPresent()
                    ? ClassName.get(
                    graph.componentDescriptor().builderSpec().get().builderDefinitionType())
                    : builderName())
            .addStatement("return new $T()", builderName())
            .build();
    generatedComponentModel.addMethod(BUILDER_METHOD, builderFactoryMethod);
  }

  @Override
  protected void addBuilderClass(TypeSpec builder) {
    generatedComponentModel.addType(COMPONENT_BUILDER, builder);
  }

  @Override
  protected void addFactoryMethods() {
    addBuilderFactoryMethod();
    if (canInstantiateAllRequirements()) {
      CharSequence buildMethodName =
          graph.componentDescriptor().builderSpec().isPresent()
              ? graph.componentDescriptor().builderSpec().get().buildMethod().getSimpleName()
              : "build";
      generatedComponentModel.addMethod(
          BUILDER_METHOD,
          methodBuilder("create")
              .returns(ClassName.get(graph.componentType()))
              .addModifiers(PUBLIC, STATIC)
              .addStatement("return new Builder().$L()", buildMethodName)
              .build());
    }
  }

  /** {@code true} if all of the graph's required dependencies can be automatically constructed. */
  private boolean canInstantiateAllRequirements() {
    return !Iterables.any(
        graph.componentRequirements(),
        dependency -> dependency.requiresAPassedInstance(elements, types));
  }
}

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
import static javax.lang.model.element.Modifier.FINAL;
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

  ComponentWriter(
      DaggerTypes types,
      Elements elements,
      KeyFactory keyFactory,
      CompilerOptions compilerOptions,
      ClassName name,
      BindingGraph graph) {
    super(
        types,
        elements,
        compilerOptions,
        name,
        graph,
        new SubcomponentNames(graph, keyFactory),
        new OptionalFactories(),
        new ComponentBindingExpressions(types),
        new ComponentRequirementFields());
  }

  @Override
  protected void decorateComponent() {
    component.addModifiers(PUBLIC, FINAL);
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
    component.addMethod(builderFactoryMethod);
  }

  @Override
  protected void addBuilderClass(TypeSpec builder) {
    component.addType(builder);
  }

  @Override
  protected void addFactoryMethods() {
    addBuilderFactoryMethod();
    if (canInstantiateAllRequirements()) {
      CharSequence buildMethodName =
          graph.componentDescriptor().builderSpec().isPresent()
              ? graph.componentDescriptor().builderSpec().get().buildMethod().getSimpleName()
              : "build";
      component.addMethod(
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

  @Override
  public boolean requiresReleasableReferences(Scope scope) {
    return graph.scopesRequiringReleasableReferenceManagers().contains(scope);
  }
}

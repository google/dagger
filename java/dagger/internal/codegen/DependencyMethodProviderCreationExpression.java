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
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static dagger.internal.codegen.ComponentImplementation.TypeSpecKind.COMPONENT_PROVISION_FACTORY;
import static dagger.internal.codegen.javapoet.TypeNames.providerOf;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.auto.common.MoreTypes;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import dagger.internal.codegen.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;
import javax.lang.model.element.Element;

/**
 * A {@link javax.inject.Provider} creation expression for a provision method on a component's
 * {@linkplain dagger.Component#dependencies()} dependency}.
 */
// TODO(dpb): Resolve with DependencyMethodProducerCreationExpression.
final class DependencyMethodProviderCreationExpression
    implements FrameworkInstanceCreationExpression {

  private final ComponentImplementation componentImplementation;
  private final ComponentRequirementExpressions componentRequirementExpressions;
  private final CompilerOptions compilerOptions;
  private final BindingGraph graph;
  private final ContributionBinding binding;

  DependencyMethodProviderCreationExpression(
      ContributionBinding binding,
      ComponentImplementation componentImplementation,
      ComponentRequirementExpressions componentRequirementExpressions,
      CompilerOptions compilerOptions,
      BindingGraph graph) {
    this.binding = checkNotNull(binding);
    this.componentImplementation = checkNotNull(componentImplementation);
    this.componentRequirementExpressions = checkNotNull(componentRequirementExpressions);
    this.compilerOptions = checkNotNull(compilerOptions);
    this.graph = checkNotNull(graph);
  }

  @Override
  public CodeBlock creationExpression() {
    // TODO(sameb): The Provider.get() throws a very vague NPE.  The stack trace doesn't
    // help to figure out what the method or return type is.  If we include a string
    // of the return type or method name in the error message, that can defeat obfuscation.
    // We can easily include the raw type (no generics) + annotation type (no values),
    // using .class & String.format -- but that wouldn't be the whole story.
    // What should we do?
    CodeBlock invocation =
        ComponentProvisionBindingExpression.maybeCheckForNull(
            (ProvisionBinding) binding,
            compilerOptions,
            CodeBlock.of(
                "$N.$N()", dependency().variableName(), provisionMethod().getSimpleName()));
    ClassName dependencyClassName = ClassName.get(dependency().typeElement());
    TypeName keyType = TypeName.get(binding.key().type());
    MethodSpec.Builder getMethod =
        methodBuilder("get")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .returns(keyType)
            .addStatement("return $L", invocation);
    if (binding.nullableType().isPresent()) {
      getMethod.addAnnotation(ClassName.get(MoreTypes.asTypeElement(binding.nullableType().get())));
    }
    componentImplementation.addType(
        COMPONENT_PROVISION_FACTORY,
        classBuilder(factoryClassName())
            .addSuperinterface(providerOf(keyType))
            .addModifiers(PRIVATE, STATIC)
            .addField(dependencyClassName, dependency().variableName(), PRIVATE, FINAL)
            .addMethod(
                constructorBuilder()
                    .addParameter(dependencyClassName, dependency().variableName())
                    .addStatement("this.$1L = $1L", dependency().variableName())
                    .build())
            .addMethod(getMethod.build())
            .build());
    return CodeBlock.of(
        "new $T($L)",
        factoryClassName(),
        componentRequirementExpressions.getExpressionDuringInitialization(
            dependency(), componentImplementation.name()));
  }

  private ClassName factoryClassName() {
    String factoryName =
        ClassName.get(dependency().typeElement()).toString().replace('.', '_')
            + "_"
            + binding.bindingElement().get().getSimpleName();
    return componentImplementation.name().nestedClass(factoryName);
  }

  private ComponentRequirement dependency() {
    return graph.componentDescriptor().getDependencyThatDefinesMethod(provisionMethod());
  }

  private Element provisionMethod() {
    return binding.bindingElement().get();
  }
}

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

package dagger.internal.codegen.writing;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static dagger.internal.codegen.javapoet.TypeNames.providerOf;
import static dagger.internal.codegen.writing.ComponentImplementation.TypeSpecKind.COMPONENT_PROVISION_FACTORY;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.auto.common.MoreTypes;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.ComponentRequirement;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;
import dagger.internal.codegen.writing.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;
import javax.lang.model.element.Element;

/**
 * A {@link javax.inject.Provider} creation expression for a provision method on a component's
 * {@linkplain dagger.Component#dependencies()} dependency}.
 */
// TODO(dpb): Resolve with DependencyMethodProducerCreationExpression.
final class DependencyMethodProviderCreationExpression
    implements FrameworkInstanceCreationExpression {

  private final ShardImplementation shardImplementation;
  private final ComponentRequirementExpressions componentRequirementExpressions;
  private final CompilerOptions compilerOptions;
  private final BindingGraph graph;
  private final ContributionBinding binding;

  @AssistedInject
  DependencyMethodProviderCreationExpression(
      @Assisted ContributionBinding binding,
      ComponentImplementation componentImplementation,
      ComponentRequirementExpressions componentRequirementExpressions,
      CompilerOptions compilerOptions,
      BindingGraph graph) {
    this.binding = checkNotNull(binding);
    this.shardImplementation = componentImplementation.shardImplementation(binding);
    this.componentRequirementExpressions = componentRequirementExpressions;
    this.compilerOptions = compilerOptions;
    this.graph = graph;
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
        ComponentProvisionRequestRepresentation.maybeCheckForNull(
            (ProvisionBinding) binding,
            compilerOptions,
            CodeBlock.of(
                "$N.$N()", dependency().variableName(), provisionMethod().getSimpleName()));
    ClassName dependencyClassName = ClassName.get(dependency().typeElement());
    TypeName keyType = TypeName.get(binding.key().type().java());
    MethodSpec.Builder getMethod =
        methodBuilder("get")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .returns(keyType)
            .addStatement("return $L", invocation);
    if (binding.nullableType().isPresent()) {
      getMethod.addAnnotation(ClassName.get(MoreTypes.asTypeElement(binding.nullableType().get())));
    }

    // We need to use the componentShard here since the generated type is static and shards are
    // not static classes so it can't be nested inside the shard.
    ShardImplementation componentShard =
        shardImplementation.getComponentImplementation().getComponentShard();
    ClassName factoryClassName =
        componentShard
            .name()
            .nestedClass(
                ClassName.get(dependency().typeElement()).toString().replace('.', '_')
                    + "_"
                    + binding.bindingElement().get().getSimpleName());
    componentShard.addType(
        COMPONENT_PROVISION_FACTORY,
        classBuilder(factoryClassName)
            .addSuperinterface(providerOf(keyType))
            .addModifiers(PRIVATE, STATIC, FINAL)
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
        factoryClassName,
        componentRequirementExpressions.getExpressionDuringInitialization(
            dependency(), shardImplementation.name()));
  }

  private ComponentRequirement dependency() {
    return graph.componentDescriptor().getDependencyThatDefinesMethod(provisionMethod());
  }

  private Element provisionMethod() {
    return binding.bindingElement().get();
  }

  @AssistedFactory
  static interface Factory {
    DependencyMethodProviderCreationExpression create(ContributionBinding binding);
  }
}

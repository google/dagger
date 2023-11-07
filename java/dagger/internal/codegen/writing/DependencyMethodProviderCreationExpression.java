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

import static androidx.room.compiler.processing.XElementKt.isMethod;
import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static dagger.internal.codegen.javapoet.TypeNames.providerOf;
import static dagger.internal.codegen.writing.ComponentImplementation.TypeSpecKind.COMPONENT_PROVISION_FACTORY;
import static dagger.internal.codegen.xprocessing.XElements.asMethod;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import androidx.room.compiler.processing.XMethodElement;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.ComponentRequirement;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;
import dagger.internal.codegen.writing.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;

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
  private final ProvisionBinding binding;
  private final XMethodElement provisionMethod;

  @AssistedInject
  DependencyMethodProviderCreationExpression(
      @Assisted ProvisionBinding binding,
      ComponentImplementation componentImplementation,
      ComponentRequirementExpressions componentRequirementExpressions,
      CompilerOptions compilerOptions,
      BindingGraph graph) {
    this.binding = checkNotNull(binding);
    this.shardImplementation = componentImplementation.shardImplementation(binding);
    this.componentRequirementExpressions = componentRequirementExpressions;
    this.compilerOptions = compilerOptions;
    this.graph = graph;

    checkArgument(binding.bindingElement().isPresent());
    checkArgument(isMethod(binding.bindingElement().get()));
    provisionMethod = asMethod(binding.bindingElement().get());
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
            binding,
            compilerOptions,
            CodeBlock.of("$N.$N()", dependency().variableName(), provisionMethod.getJvmName()));
    ClassName dependencyClassName = dependency().typeElement().getClassName();
    TypeName keyType = binding.key().type().xprocessing().getTypeName();
    MethodSpec.Builder getMethod =
        methodBuilder("get")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .returns(keyType)
            .addStatement("return $L", invocation);

    binding
        .nullability()
        .nullableAnnotations()
        .forEach(getMethod::addAnnotation);

    // We need to use the componentShard here since the generated type is static and shards are
    // not static classes so it can't be nested inside the shard.
    ShardImplementation componentShard =
        shardImplementation.getComponentImplementation().getComponentShard();
    ClassName factoryClassName =
        componentShard
            .name()
            .nestedClass(
                componentShard.getUniqueClassName(
                    LOWER_CAMEL.to(UPPER_CAMEL, getSimpleName(provisionMethod) + "Provider")));
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
    return graph.componentDescriptor().getDependencyThatDefinesMethod(provisionMethod);
  }

  @AssistedFactory
  static interface Factory {
    DependencyMethodProviderCreationExpression create(ProvisionBinding binding);
  }
}

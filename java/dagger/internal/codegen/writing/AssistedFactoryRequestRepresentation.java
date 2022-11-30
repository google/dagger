/*
 * Copyright (C) 2020 The Dagger Authors.
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
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.binding.AssistedInjectionAnnotations.assistedFactoryMethod;
import static dagger.internal.codegen.writing.AssistedInjectionParameters.assistedFactoryParameterSpecs;
import static dagger.internal.codegen.xprocessing.MethodSpecs.overriding;
import static dagger.internal.codegen.xprocessing.XElements.asTypeElement;

import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.Binding;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.javapoet.Expression;
import dagger.spi.model.DependencyRequest;
import java.util.Optional;

/**
 * A {@link dagger.internal.codegen.writing.RequestRepresentation} for {@link
 * dagger.assisted.AssistedFactory} methods.
 */
final class AssistedFactoryRequestRepresentation extends RequestRepresentation {
  private final ProvisionBinding binding;
  private final BindingGraph graph;
  private final SimpleMethodRequestRepresentation.Factory simpleMethodRequestRepresentationFactory;
  private final ComponentImplementation componentImplementation;

  @AssistedInject
  AssistedFactoryRequestRepresentation(
      @Assisted ProvisionBinding binding,
      BindingGraph graph,
      ComponentImplementation componentImplementation,
      SimpleMethodRequestRepresentation.Factory simpleMethodRequestRepresentationFactory) {
    this.binding = checkNotNull(binding);
    this.graph = graph;
    this.componentImplementation = componentImplementation;
    this.simpleMethodRequestRepresentationFactory = simpleMethodRequestRepresentationFactory;
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    // An assisted factory binding should have a single request for an assisted injection type.
    DependencyRequest assistedInjectionRequest = getOnlyElement(binding.provisionDependencies());
    // Get corresponding assisted injection binding.
    Optional<Binding> localBinding = graph.localContributionBinding(assistedInjectionRequest.key());
    checkArgument(
        localBinding.isPresent(),
        "assisted factory should have a dependency on an assisted injection binding");
    Expression assistedInjectionExpression =
        simpleMethodRequestRepresentationFactory
            .create((ProvisionBinding) localBinding.get())
            .getDependencyExpression(requestingClass.peerClass(""));
    return Expression.create(
        assistedInjectionExpression.type(),
        CodeBlock.of("$L", anonymousfactoryImpl(localBinding.get(), assistedInjectionExpression)));
  }

  private TypeSpec anonymousfactoryImpl(
      Binding assistedBinding, Expression assistedInjectionExpression) {
    XTypeElement factory = asTypeElement(binding.bindingElement().get());
    XType factoryType = binding.key().type().xprocessing();
    XMethodElement factoryMethod = assistedFactoryMethod(factory);

    // We can't use MethodSpec.overriding directly because we need to control the parameter names.
    MethodSpec factoryOverride = overriding(factoryMethod, factoryType).build();
    TypeSpec.Builder builder =
        TypeSpec.anonymousClassBuilder("")
            .addMethod(
                // We're overriding the method so we have to use the jvm name here.
                MethodSpec.methodBuilder(factoryMethod.getJvmName())
                    .addModifiers(factoryOverride.modifiers)
                    .addTypeVariables(factoryOverride.typeVariables)
                    .returns(factoryOverride.returnType)
                    .addAnnotations(factoryOverride.annotations)
                    .addExceptions(factoryOverride.exceptions)
                    .addParameters(
                        assistedFactoryParameterSpecs(
                            binding, componentImplementation.shardImplementation(assistedBinding)))
                    .addStatement("return $L", assistedInjectionExpression.codeBlock())
                    .build());

    if (factory.isInterface()) {
      builder.addSuperinterface(factoryType.getTypeName());
    } else {
      builder.superclass(factoryType.getTypeName());
    }

    return builder.build();
  }

  @AssistedFactory
  static interface Factory {
    AssistedFactoryRequestRepresentation create(ProvisionBinding binding);
  }
}

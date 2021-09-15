/*
 * Copyright (C) 2021 The Dagger Authors.
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
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static dagger.internal.codegen.binding.AssistedInjectionAnnotations.assistedParameterSpecs;
import static dagger.internal.codegen.javapoet.CodeBlocks.parameterNames;
import static dagger.internal.codegen.writing.AssistedInjectionParameters.assistedParameterSpecs;
import static dagger.internal.codegen.writing.ComponentImplementation.MethodSpecKind.PRIVATE_METHOD;
import static javax.lang.model.element.Modifier.PRIVATE;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.BindingRequest;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;
import dagger.spi.model.BindingKind;
import dagger.spi.model.RequestKind;
import javax.lang.model.type.TypeMirror;

/** A binding expression that wraps private method call for assisted fatory creation. */
final class AssistedPrivateMethodRequestRepresentation extends MethodRequestRepresentation {
  private final ShardImplementation shardImplementation;
  private final ContributionBinding binding;
  private final BindingRequest request;
  private final RequestRepresentation wrappedRequestRepresentation;
  private final CompilerOptions compilerOptions;
  private final DaggerTypes types;
  private String methodName;

  @AssistedInject
  AssistedPrivateMethodRequestRepresentation(
      @Assisted BindingRequest request,
      @Assisted ContributionBinding binding,
      @Assisted RequestRepresentation wrappedRequestRepresentation,
      ComponentImplementation componentImplementation,
      DaggerTypes types,
      CompilerOptions compilerOptions) {
    super(componentImplementation.shardImplementation(binding), types);
    checkArgument(binding.kind() == BindingKind.ASSISTED_INJECTION);
    checkArgument(request.requestKind() == RequestKind.INSTANCE);
    this.binding = checkNotNull(binding);
    this.request = checkNotNull(request);
    this.wrappedRequestRepresentation = checkNotNull(wrappedRequestRepresentation);
    this.shardImplementation = componentImplementation.shardImplementation(binding);
    this.compilerOptions = compilerOptions;
    this.types = types;
  }

  Expression getAssistedDependencyExpression(ClassName requestingClass) {
    return Expression.create(
        returnType(),
        requestingClass.equals(shardImplementation.name())
            ? CodeBlock.of(
                "$N($L)", methodName(), parameterNames(assistedParameterSpecs(binding, types)))
            : CodeBlock.of(
                "$L.$N($L)",
                shardImplementation.shardFieldReference(),
                methodName(),
                parameterNames(assistedParameterSpecs(binding, types))));
  }

  @Override
  protected CodeBlock methodCall() {
    throw new IllegalStateException("This should not be accessed");
  }

  @Override
  protected TypeMirror returnType() {
    return types.accessibleType(binding.contributedType(), shardImplementation.name());
  }

  private String methodName() {
    if (methodName == null) {
      // Have to set methodName field before implementing the method in order to handle recursion.
      methodName = shardImplementation.getUniqueMethodName(request);

      // TODO(bcorso): Fix the order that these generated methods are written to the component.
      shardImplementation.addMethod(
          PRIVATE_METHOD,
          methodBuilder(methodName)
              .addModifiers(PRIVATE)
              .addParameters(assistedParameterSpecs(binding, types, shardImplementation))
              .returns(TypeName.get(returnType()))
              .addStatement(
                  "return $L",
                  wrappedRequestRepresentation
                      .getDependencyExpression(shardImplementation.name())
                      .codeBlock())
              .build());
    }
    return methodName;
  }

  @AssistedFactory
  static interface Factory {
    AssistedPrivateMethodRequestRepresentation create(
        BindingRequest request,
        ContributionBinding binding,
        RequestRepresentation wrappedRequestRepresentation);
  }
}

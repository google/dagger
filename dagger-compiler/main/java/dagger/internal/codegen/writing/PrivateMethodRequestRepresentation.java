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

package dagger.internal.codegen.writing;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static dagger.internal.codegen.langmodel.Accessibility.isRawTypeAccessible;
import static dagger.internal.codegen.langmodel.Accessibility.isTypeAccessibleFrom;
import static dagger.internal.codegen.writing.ComponentImplementation.MethodSpecKind.PRIVATE_METHOD;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static javax.lang.model.element.Modifier.PRIVATE;

import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.BindingRequest;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.javapoet.ExpressionType;
import dagger.internal.codegen.model.RequestKind;
import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;

/**
 * A binding expression that wraps the dependency expressions in a private, no-arg method.
 *
 * <p>Dependents of this binding expression will just call the no-arg private method.
 */
final class PrivateMethodRequestRepresentation extends MethodRequestRepresentation {
  private final ShardImplementation shardImplementation;
  private final ContributionBinding binding;
  private final BindingRequest request;
  private final RequestRepresentation wrappedRequestRepresentation;
  private final XProcessingEnv processingEnv;
  private String methodName;

  @AssistedInject
  PrivateMethodRequestRepresentation(
      @Assisted BindingRequest request,
      @Assisted ContributionBinding binding,
      @Assisted RequestRepresentation wrappedRequestRepresentation,
      ComponentImplementation componentImplementation,
      XProcessingEnv processingEnv) {
    super(componentImplementation.shardImplementation(binding), processingEnv);
    this.binding = checkNotNull(binding);
    this.request = checkNotNull(request);
    this.wrappedRequestRepresentation = checkNotNull(wrappedRequestRepresentation);
    this.shardImplementation = componentImplementation.shardImplementation(binding);
    this.processingEnv = processingEnv;
  }

  @Override
  protected CodeBlock methodCall() {
    return CodeBlock.of("$N()", methodName());
  }

  @Override
  protected ExpressionType returnType() {
    XType type = request.isRequestKind(RequestKind.INSTANCE)
                && binding.contributedPrimitiveType().isPresent()
        ? binding.contributedPrimitiveType().get()
        : request.requestedType(binding.contributedType(), processingEnv);
    String requestingPackage = shardImplementation.name().getPackageName();
    if (isTypeAccessibleFrom(type, requestingPackage)) {
      return ExpressionType.create(type);
    } else if (isDeclared(type) && isRawTypeAccessible(type, requestingPackage)) {
      return ExpressionType.createRawType(type);
    } else {
      return ExpressionType.create(processingEnv.requireType(TypeName.OBJECT));
    }
  }

  private String methodName() {
    if (methodName == null) {
      // Have to set methodName field before implementing the method in order to handle recursion.
      methodName = shardImplementation.getUniqueMethodName(request);

      // TODO(bcorso): Fix the order that these generated methods are written to the component.
      shardImplementation.addMethod(
          PRIVATE_METHOD,
          methodBuilder(methodName)
              // TODO(bcorso): remove once dagger.generatedClassExtendsComponent flag is removed.
              .addModifiers(
                  !shardImplementation.isShardClassPrivate()
                      ? ImmutableSet.of(PRIVATE)
                      : ImmutableSet.of())
              .returns(returnType().getTypeName())
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
    PrivateMethodRequestRepresentation create(
        BindingRequest request,
        ContributionBinding binding,
        RequestRepresentation wrappedRequestRepresentation);
  }
}

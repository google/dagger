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
import static dagger.internal.codegen.binding.SourceFiles.bindingTypeElementTypeVariableNames;
import static dagger.internal.codegen.binding.SourceFiles.generatedClassNameForBinding;
import static dagger.internal.codegen.binding.SourceFiles.setFactoryClassName;
import static dagger.internal.codegen.javapoet.CodeBlocks.toParametersCodeBlock;
import static dagger.internal.codegen.javapoet.TypeNames.FACTORY;
import static dagger.internal.codegen.javapoet.TypeNames.MAP_FACTORY;
import static dagger.internal.codegen.javapoet.TypeNames.PRODUCER;
import static dagger.internal.codegen.javapoet.TypeNames.PRODUCERS;
import static dagger.internal.codegen.javapoet.TypeNames.PROVIDER;
import static dagger.internal.codegen.langmodel.Accessibility.isTypeAccessibleFrom;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;

import androidx.room.compiler.processing.XType;
import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeVariableName;
import dagger.internal.codegen.base.SetType;
import dagger.internal.codegen.binding.Binding;
import dagger.internal.codegen.binding.BindingType;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.javapoet.CodeBlocks;

/** Helper class for static member select creation. */
final class StaticMemberSelects {
  /** A {@link MemberSelect} for a factory of an empty map. */
  static MemberSelect emptyMapFactory(Binding binding) {
    BindingType bindingType = binding.bindingType();
    ImmutableList<XType> typeParameters =
        ImmutableList.copyOf(binding.key().type().xprocessing().getTypeArguments());
    return bindingType.equals(BindingType.PRODUCTION)
        ? new ParameterizedStaticMethod(
            PRODUCERS, typeParameters, CodeBlock.of("emptyMapProducer()"), PRODUCER)
        : new ParameterizedStaticMethod(
            MAP_FACTORY, typeParameters, CodeBlock.of("emptyMapProvider()"), PROVIDER);
  }

  /**
   * A static member select for an empty set factory. Calls {@link
   * dagger.internal.SetFactory#empty()}, {@link dagger.producers.internal.SetProducer#empty()}, or
   * {@link dagger.producers.internal.SetOfProducedProducer#empty()}, depending on the set bindings.
   */
  static MemberSelect emptySetFactory(ContributionBinding binding) {
    return new ParameterizedStaticMethod(
        setFactoryClassName(binding),
        ImmutableList.of(SetType.from(binding.key()).elementType()),
        CodeBlock.of("empty()"),
        FACTORY);
  }

  /**
   * Returns a {@link MemberSelect} for the instance of a {@code create()} method on a factory with
   * no arguments.
   */
  static MemberSelect factoryCreateNoArgumentMethod(Binding binding) {
    checkArgument(
        binding.bindingType().equals(BindingType.PROVISION),
        "Invalid binding type: %s",
        binding.bindingType());
    checkArgument(
        binding.dependencies().isEmpty() && !binding.scope().isPresent(),
        "%s should have no dependencies and be unscoped to create a no argument factory.",
        binding);

    ClassName factoryName = generatedClassNameForBinding(binding);
    XType keyType = binding.key().type().xprocessing();
    if (isDeclared(keyType)) {
      ImmutableList<TypeVariableName> typeVariables = bindingTypeElementTypeVariableNames(binding);
      if (!typeVariables.isEmpty()) {
        ImmutableList<XType> typeArguments = ImmutableList.copyOf(keyType.getTypeArguments());
        return new ParameterizedStaticMethod(
            factoryName, typeArguments, CodeBlock.of("create()"), FACTORY);
      }
    }
    return new StaticMethod(factoryName, CodeBlock.of("create()"));
  }

  private static final class StaticMethod extends MemberSelect {
    private final CodeBlock methodCodeBlock;

    StaticMethod(ClassName owningClass, CodeBlock methodCodeBlock) {
      super(owningClass, true);
      this.methodCodeBlock = checkNotNull(methodCodeBlock);
    }

    @Override
    CodeBlock getExpressionFor(ClassName usingClass) {
      return owningClass().equals(usingClass)
          ? methodCodeBlock
          : CodeBlock.of("$T.$L", owningClass(), methodCodeBlock);
    }
  }

  private static final class ParameterizedStaticMethod extends MemberSelect {
    private final ImmutableList<XType> typeParameters;
    private final CodeBlock methodCodeBlock;
    private final ClassName rawReturnType;

    ParameterizedStaticMethod(
        ClassName owningClass,
        ImmutableList<XType> typeParameters,
        CodeBlock methodCodeBlock,
        ClassName rawReturnType) {
      super(owningClass, true);
      this.typeParameters = typeParameters;
      this.methodCodeBlock = methodCodeBlock;
      this.rawReturnType = rawReturnType;
    }

    @Override
    CodeBlock getExpressionFor(ClassName usingClass) {
      boolean isAccessible =
          typeParameters.stream().allMatch(t -> isTypeAccessibleFrom(t, usingClass.packageName()));

      if (isAccessible) {
        return CodeBlock.of(
            "$T.<$L>$L",
            owningClass(),
            typeParameters.stream().map(CodeBlocks::type).collect(toParametersCodeBlock()),
            methodCodeBlock);
      } else {
        return CodeBlock.of("(($T) $T.$L)", rawReturnType, owningClass(), methodCodeBlock);
      }
    }
  }

  private StaticMemberSelects() {}
}

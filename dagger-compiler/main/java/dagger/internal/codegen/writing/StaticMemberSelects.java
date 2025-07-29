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
import static dagger.internal.codegen.xprocessing.Accessibility.isTypeAccessibleFrom;
import static dagger.internal.codegen.xprocessing.XCodeBlocks.toParametersCodeBlock;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XCodeBlock;
import androidx.room.compiler.codegen.XTypeName;
import androidx.room.compiler.processing.XType;
import com.google.common.collect.ImmutableList;
import dagger.internal.codegen.base.SetType;
import dagger.internal.codegen.binding.Binding;
import dagger.internal.codegen.binding.BindingType;
import dagger.internal.codegen.binding.MultiboundMapBinding;
import dagger.internal.codegen.binding.MultiboundSetBinding;
import dagger.internal.codegen.xprocessing.XCodeBlocks;
import dagger.internal.codegen.xprocessing.XTypeNames;

/** Helper class for static member select creation. */
final class StaticMemberSelects {
  /** A {@link MemberSelect} for a factory of an empty map. */
  static MemberSelect emptyMapFactory(MultiboundMapBinding binding) {
    BindingType bindingType = binding.bindingType();
    ImmutableList<XType> typeParameters =
        ImmutableList.copyOf(binding.key().type().xprocessing().getTypeArguments());
    return bindingType.equals(BindingType.PRODUCTION)
        ? new ParameterizedStaticMethod(
            XTypeNames.PRODUCERS,
            typeParameters,
            XCodeBlock.of("emptyMapProducer()"),
            XTypeNames.PRODUCER)
        : new ParameterizedStaticMethod(
            XTypeNames.MAP_FACTORY,
            typeParameters,
            XCodeBlock.of("emptyMapProvider()"),
            XTypeNames.DAGGER_PROVIDER);
  }

  /**
   * A static member select for an empty set factory. Calls {@link
   * dagger.internal.SetFactory#empty()}, {@link dagger.producers.internal.SetProducer#empty()}, or
   * {@link dagger.producers.internal.SetOfProducedProducer#empty()}, depending on the set bindings.
   */
  static MemberSelect emptySetFactory(MultiboundSetBinding binding) {
    return new ParameterizedStaticMethod(
        setFactoryClassName(binding),
        ImmutableList.of(SetType.from(binding.key()).elementType()),
        XCodeBlock.of("empty()"),
        XTypeNames.FACTORY);
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

    XClassName factoryName = generatedClassNameForBinding(binding);
    XType keyType = binding.key().type().xprocessing();
    if (isDeclared(keyType)) {
      ImmutableList<XTypeName> typeVariables = bindingTypeElementTypeVariableNames(binding);
      if (!typeVariables.isEmpty()) {
        ImmutableList<XType> typeArguments = ImmutableList.copyOf(keyType.getTypeArguments());
        return new ParameterizedStaticMethod(
            factoryName, typeArguments, XCodeBlock.of("create()"), XTypeNames.FACTORY);
      }
    }
    return new StaticMethod(factoryName, XCodeBlock.of("create()"));
  }

  private static final class StaticMethod extends MemberSelect {
    private final XCodeBlock methodCodeBlock;

    StaticMethod(XClassName owningClass, XCodeBlock methodCodeBlock) {
      super(owningClass, true);
      this.methodCodeBlock = checkNotNull(methodCodeBlock);
    }

    @Override
    XCodeBlock getExpressionFor(XClassName usingClass) {
      return owningClass().equals(usingClass)
          ? methodCodeBlock
          : XCodeBlock.of("%T.%L", owningClass(), methodCodeBlock);
    }
  }

  private static final class ParameterizedStaticMethod extends MemberSelect {
    private final ImmutableList<XType> typeParameters;
    private final XCodeBlock methodCodeBlock;
    private final XClassName rawReturnType;

    ParameterizedStaticMethod(
        XClassName owningClass,
        ImmutableList<XType> typeParameters,
        XCodeBlock methodCodeBlock,
        XClassName rawReturnType) {
      super(owningClass, true);
      this.typeParameters = typeParameters;
      this.methodCodeBlock = methodCodeBlock;
      this.rawReturnType = rawReturnType;
    }

    @Override
    XCodeBlock getExpressionFor(XClassName usingClass) {
      boolean isAccessible =
          typeParameters.stream()
              .allMatch(t -> isTypeAccessibleFrom(t, usingClass.getPackageName()));

      if (isAccessible) {
        return XCodeBlock.of(
            "%T.<%L>%L",
            owningClass(),
            typeParameters.stream().map(XCodeBlocks::type).collect(toParametersCodeBlock()),
            methodCodeBlock);
      } else {
        XCodeBlock expression = XCodeBlock.of("%T.%L", owningClass(), methodCodeBlock);
        return XCodeBlock.of("(%L)", XCodeBlock.ofCast(rawReturnType, expression));
      }
    }
  }

  private StaticMemberSelects() {}
}

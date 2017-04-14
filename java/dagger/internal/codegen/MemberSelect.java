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
import static dagger.internal.codegen.Accessibility.isTypeAccessibleFrom;
import static dagger.internal.codegen.CodeBlocks.toTypeNamesCodeBlock;
import static dagger.internal.codegen.TypeNames.FACTORY;
import static dagger.internal.codegen.TypeNames.MAP_OF_PRODUCER_PRODUCER;
import static dagger.internal.codegen.TypeNames.MAP_PROVIDER_FACTORY;
import static dagger.internal.codegen.TypeNames.MEMBERS_INJECTOR;
import static dagger.internal.codegen.TypeNames.MEMBERS_INJECTORS;

import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;
import dagger.MembersInjector;
import java.util.List;
import java.util.Set;
import javax.lang.model.type.TypeMirror;

/**
 * Represents a {@link com.sun.source.tree.MemberSelectTree} as a {@link CodeBlock}.
 */
abstract class MemberSelect {
  /**
   * Returns a {@link MemberSelect} that accesses the field given by {@code fieldName} owned by
   * {@code owningClass}.  In this context "local" refers to the fact that the field is owned by the
   * type (or an enclosing type) from which the code block will be used.  The returned
   * {@link MemberSelect} will not be valid for accessing the field from a different class
   * (regardless of accessibility).
   */
  static MemberSelect localField(ClassName owningClass, String fieldName) {
    return new LocalField(owningClass, fieldName);
  }

  private static final class LocalField extends MemberSelect {
    final String fieldName;

    LocalField(ClassName owningClass, String fieldName) {
      super(owningClass, false);
      this.fieldName = checkNotNull(fieldName);
    }

    @Override
    CodeBlock getExpressionFor(ClassName usingClass) {
      return owningClass().equals(usingClass)
          ? CodeBlock.of("$L", fieldName)
          : CodeBlock.of("$T.this.$L", owningClass(), fieldName);
    }
  }

  /**
   * Returns a {@link MemberSelect} for the invocation of a static method (given by
   * {@code methodInvocationCodeBlock}) on the {@code owningClass}.
   */
  static MemberSelect staticMethod(ClassName owningClass, CodeBlock methodInvocationCodeBlock) {
    return new StaticMethod(owningClass, methodInvocationCodeBlock);
  }

  /**
   * Returns a {@link MemberSelect} for the instance of a {@code create()} method on a factory.
   * This only applies for factories that do not have any dependencies.
   */
  static MemberSelect parameterizedFactoryCreateMethod(
      ClassName owningClass, List<? extends TypeMirror> parameters) {
    return new ParameterizedStaticMethod(
        owningClass, ImmutableList.copyOf(parameters), CodeBlock.of("create()"), FACTORY);
  }

  private static final class StaticMethod extends MemberSelect {
    final CodeBlock methodCodeBlock;

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

  /**
   * Returns the {@link MemberSelect} for a no-op {@link MembersInjector} for the given type.
   */
  static MemberSelect noOpMembersInjector(TypeMirror type) {
    return new ParameterizedStaticMethod(
        MEMBERS_INJECTORS,
        ImmutableList.of(type),
        CodeBlock.of("noOp()"),
        MEMBERS_INJECTOR);
  }

   /**
   * A {@link MemberSelect} for an empty map of framework types.
   *
   * @param bindingType the type of the binding of the empty map
   */
  static MemberSelect emptyFrameworkMapFactory(
      BindingType bindingType, TypeMirror keyType, TypeMirror unwrappedValueType) {
    final ClassName frameworkMapFactoryClass;
    switch (bindingType) {
      case PROVISION:
        frameworkMapFactoryClass = MAP_PROVIDER_FACTORY;
        break;
      case PRODUCTION:
        frameworkMapFactoryClass = MAP_OF_PRODUCER_PRODUCER;
        break;
      case MEMBERS_INJECTION:
        throw new IllegalArgumentException();
      default:
        throw new AssertionError();
    }
    return new ParameterizedStaticMethod(
        frameworkMapFactoryClass,
        ImmutableList.of(keyType, unwrappedValueType),
        CodeBlock.of("empty()"),
        ClassName.get(bindingType.frameworkClass()));
  }

  /**
   * Returns the {@link MemberSelect} for an empty set provider.  Since there are several different
   * implementations for a multibound {@link Set}, the caller is responsible for passing the
   * correct factory.
   */
  static MemberSelect emptySetProvider(ClassName setFactoryType, SetType setType) {
    return new ParameterizedStaticMethod(
        setFactoryType, ImmutableList.of(setType.elementType()), CodeBlock.of("empty()"), FACTORY);
  }

  private static final class ParameterizedStaticMethod extends MemberSelect {
    final ImmutableList<TypeMirror> typeParameters;
    final CodeBlock methodCodeBlock;
    final ClassName rawReturnType;

    ParameterizedStaticMethod(
        ClassName owningClass,
        ImmutableList<TypeMirror> typeParameters,
        CodeBlock methodCodeBlock,
        ClassName rawReturnType) {
      super(owningClass, true);
      this.typeParameters = typeParameters;
      this.methodCodeBlock = methodCodeBlock;
      this.rawReturnType = rawReturnType;
    }

    @Override
    CodeBlock getExpressionFor(ClassName usingClass) {
      boolean accessible = true;
      for (TypeMirror typeParameter : typeParameters) {
        accessible &= isTypeAccessibleFrom(typeParameter, usingClass.packageName());
      }

      if (accessible) {
        return CodeBlock.of(
            "$T.<$L>$L",
            owningClass(),
            typeParameters.stream().map(TypeName::get).collect(toTypeNamesCodeBlock()),
            methodCodeBlock);
      } else {
        return CodeBlock.of("(($T) $T.$L)", rawReturnType, owningClass(), methodCodeBlock);
      }
    }
  }

  private final ClassName owningClass;
  private final boolean staticMember;

  MemberSelect(ClassName owningClass, boolean staticMemeber) {
    this.owningClass = owningClass;
    this.staticMember = staticMemeber;
  }

  /** Returns the class that owns the member being selected. */
  ClassName owningClass() {
    return owningClass;
  }

  /**
   * Returns true if the member being selected is static and does not require an instance of
   * {@link #owningClass()}.
   */
  boolean staticMember() {
    return staticMember;
  }

  /**
   * Returns a {@link CodeBlock} suitable for accessing the member from the given {@code
   * usingClass}.
   */
  abstract CodeBlock getExpressionFor(ClassName usingClass);
}

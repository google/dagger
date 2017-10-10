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
import static dagger.internal.codegen.ContributionBinding.FactoryCreationStrategy.SINGLETON_INSTANCE;
import static dagger.internal.codegen.SourceFiles.bindingTypeElementTypeVariableNames;
import static dagger.internal.codegen.SourceFiles.generatedClassNameForBinding;
import static dagger.internal.codegen.SourceFiles.setFactoryClassName;
import static dagger.internal.codegen.TypeNames.FACTORY;
import static dagger.internal.codegen.TypeNames.MAP_FACTORY;
import static dagger.internal.codegen.TypeNames.MEMBERS_INJECTOR;
import static dagger.internal.codegen.TypeNames.MEMBERS_INJECTORS;
import static dagger.internal.codegen.TypeNames.PRODUCER;
import static dagger.internal.codegen.TypeNames.PRODUCERS;
import static dagger.internal.codegen.TypeNames.PROVIDER;
import static javax.lang.model.type.TypeKind.DECLARED;

import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;
import dagger.MembersInjector;
import java.util.List;
import java.util.Optional;
import javax.lang.model.type.DeclaredType;
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
   * If {@code resolvedBindings} is an unscoped provision binding with no factory arguments or a
   * no-op members injection binding, then we don't need a field to hold its factory. In that case,
   * this method returns the static member select that returns the factory or no-op members
   * injector.
   */
  static Optional<MemberSelect> staticMemberSelect(ResolvedBindings resolvedBindings) {
    BindingKey bindingKey = resolvedBindings.bindingKey();
    switch (bindingKey.kind()) {
      case CONTRIBUTION:
        ContributionBinding contributionBinding = resolvedBindings.contributionBinding();
        if (contributionBinding.factoryCreationStrategy().equals(SINGLETON_INSTANCE)
            && !contributionBinding.scope().isPresent()) {
          switch (contributionBinding.bindingKind()) {
            case SYNTHETIC_MULTIBOUND_MAP:
              return Optional.of(emptyMapFactory(contributionBinding));

            case SYNTHETIC_MULTIBOUND_SET:
              return Optional.of(emptySetFactory(contributionBinding));

            case INJECTION:
            case PROVISION:
              if (bindingKey.key().type().getKind().equals(DECLARED)) {
                ImmutableList<TypeVariableName> typeVariables =
                    bindingTypeElementTypeVariableNames(contributionBinding);
                if (!typeVariables.isEmpty()) {
                  List<? extends TypeMirror> typeArguments =
                      ((DeclaredType) bindingKey.key().type()).getTypeArguments();
                  return Optional.of(
                      MemberSelect.parameterizedFactoryCreateMethod(
                          generatedClassNameForBinding(contributionBinding), typeArguments));
                }
              }
              // fall through

            default:
              return Optional.of(
                  new StaticMethod(
                      generatedClassNameForBinding(contributionBinding), CodeBlock.of("create()")));
          }
        }
        break;

      case MEMBERS_INJECTION:
        Optional<MembersInjectionBinding> membersInjectionBinding =
            resolvedBindings.membersInjectionBinding();
        if (membersInjectionBinding.isPresent()
            && membersInjectionBinding.get().injectionSites().isEmpty()) {
          return Optional.of(noOpMembersInjector(membersInjectionBinding.get().key().type()));
        }
        break;

      default:
        throw new AssertionError();
    }
    return Optional.empty();
  }

  /**
   * Returns a {@link MemberSelect} for the instance of a {@code create()} method on a factory. This
   * only applies for factories that do not have any dependencies.
   */
  private static MemberSelect parameterizedFactoryCreateMethod(
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

  /** Returns the {@link MemberSelect} for a no-op {@link MembersInjector} for the given type. */
  private static MemberSelect noOpMembersInjector(TypeMirror type) {
    return new ParameterizedStaticMethod(
        MEMBERS_INJECTORS,
        ImmutableList.of(type),
        CodeBlock.of("noOp()"),
        MEMBERS_INJECTOR);
  }

  /** A {@link MemberSelect} for a factory of an empty map. */
  private static MemberSelect emptyMapFactory(ContributionBinding contributionBinding) {
    BindingType bindingType = contributionBinding.bindingType();
    ImmutableList<TypeMirror> typeParameters =
        ImmutableList.copyOf(
            MoreTypes.asDeclared(contributionBinding.key().type()).getTypeArguments());
    if (bindingType.equals(BindingType.PRODUCTION)) {
      return new ParameterizedStaticMethod(
          PRODUCERS, typeParameters, CodeBlock.of("emptyMapProducer()"), PRODUCER);
    } else {
      return new ParameterizedStaticMethod(
          MAP_FACTORY, typeParameters, CodeBlock.of("emptyMapProvider()"), PROVIDER);
    }
  }

  /**
   * A static member select for an empty set factory. Calls {@link
   * dagger.internal.SetFactory#empty()}, {@link dagger.producers.internal.SetProducer#empty()}, or
   * {@link dagger.producers.internal.SetOfProducedProducer#empty()}, depending on the set bindings.
   */
  private static MemberSelect emptySetFactory(ContributionBinding binding) {
    return new ParameterizedStaticMethod(
        setFactoryClassName(binding),
        ImmutableList.of(SetType.from(binding.key()).elementType()),
        CodeBlock.of("empty()"),
        FACTORY);
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

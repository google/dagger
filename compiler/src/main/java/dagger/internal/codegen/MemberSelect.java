/*
 * Copyright (C) 2015 Google, Inc.
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

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import dagger.MembersInjector;
import dagger.internal.MapProviderFactory;
import dagger.internal.MembersInjectors;
import dagger.internal.codegen.writer.ClassName;
import dagger.internal.codegen.writer.Snippet;
import dagger.internal.codegen.writer.TypeNames;
import java.util.Set;
import javax.inject.Provider;
import javax.lang.model.type.TypeMirror;

import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.Accessibility.isTypeAccessibleFrom;

/**
 * Represents a {@link com.sun.source.tree.MemberSelectTree} as a {@link Snippet}.
 */
abstract class MemberSelect {
  /**
   * Returns a {@link MemberSelect} that accesses the field given by {@code fieldName} owned by
   * {@code owningClass}.  In this context "local" refers to the fact that the field is owned by the
   * type (or an enclosing type) from which the snippet will be used.  The returned
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
    Snippet getSnippetFor(ClassName usingClass) {
      return owningClass().equals(usingClass)
          ? Snippet.format("%s", fieldName)
          : Snippet.format("%s.this.%s", owningClass(), fieldName);
    }
  }

  /**
   * Returns a {@link MemberSelect} for the invocation of a static method (given by
   * {@code methodInvocationSnippet}) on the {@code owningClass}.
   */
  static MemberSelect staticMethod(ClassName owningClass, Snippet methodInvocationSnippet) {
    return new StaticMethod(owningClass, methodInvocationSnippet);
  }

  private static final class StaticMethod extends MemberSelect {
    final Snippet methodSnippet;

    StaticMethod(ClassName owningClass, Snippet methodSnippet) {
      super(owningClass, true);
      this.methodSnippet = checkNotNull(methodSnippet);
    }

    @Override
    Snippet getSnippetFor(ClassName usingClass) {
      return owningClass().equals(usingClass)
          ? methodSnippet
          : Snippet.format("%s.%s", owningClass(), methodSnippet);
    }
  }

  /**
   * Returns the {@link MemberSelect} for a no-op {@link MembersInjector} for the given type.
   */
  static MemberSelect noOpMembersInjector(TypeMirror type) {
    return new ParameterizedStaticMethod(
        ClassName.fromClass(MembersInjectors.class),
        ImmutableList.of(type),
        Snippet.format("noOp()"),
        ClassName.fromClass(MembersInjector.class));
  }

  /**
   * Returns the {@link MemberSelect} an empty implementation of {@link MapProviderFactory}.
   */
  static MemberSelect emptyMapProviderFactory(MapType mapType) {
    return new ParameterizedStaticMethod(
        ClassName.fromClass(MapProviderFactory.class),
        ImmutableList.of(mapType.keyType(), mapType.unwrappedValueType(Provider.class)),
        Snippet.format("empty()"),
        ClassName.fromClass(MapProviderFactory.class));
  }

  /**
   * Returns the {@link MemberSelect} for an empty set provider.  Since there are several different
   * implementations for a multibound {@link Set}, the caller is responsible for passing the
   * correct factory.
   */
  static MemberSelect emptySetProvider(ClassName setFactoryType, SetType setType) {
    return new ParameterizedStaticMethod(
        setFactoryType,
        ImmutableList.of(setType.elementType()),
        Snippet.format("create()"),
        ClassName.fromClass(Set.class));
  }

  static final class ParameterizedStaticMethod extends MemberSelect {
    final ImmutableList<TypeMirror> typeParameters;
    final Snippet methodSnippet;
    final ClassName rawReturnType;

    ParameterizedStaticMethod(
        ClassName owningClass,
        ImmutableList<TypeMirror> typeParameters,
        Snippet methodSnippet,
        ClassName rawReturnType) {
      super(owningClass, true);
      this.typeParameters = typeParameters;
      this.methodSnippet = methodSnippet;
      this.rawReturnType = rawReturnType;
    }

    @Override
    Snippet getSnippetFor(ClassName usingClass) {
      boolean accessible = true;
      for (TypeMirror typeParameter : typeParameters) {
        accessible &= isTypeAccessibleFrom(typeParameter, usingClass.packageName());
      }

      if (accessible) {
        Snippet typeParametersSnippet = Snippet.makeParametersSnippet(
            FluentIterable.from(typeParameters)
                .transform(new Function<TypeMirror, Snippet>() {
                  @Override
                  public Snippet apply(TypeMirror input) {
                    return Snippet.format("%s", TypeNames.forTypeMirror(input));
                  }
                }));
        return Snippet.format(
            "%s.<%s>%s",
            owningClass(),
            typeParametersSnippet,
            methodSnippet);
      } else {
        return Snippet.format(
            "((%s) %s.%s)",
            rawReturnType,
            owningClass(),
            methodSnippet);
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
   * Returns a {@link Snippet} suitable for accessing the member from the given {@code usingClass}.
   */
  abstract Snippet getSnippetFor(ClassName usingClass);
}
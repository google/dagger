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

package dagger.internal.codegen;

import static dagger.internal.codegen.Accessibility.isTypeAccessibleFrom;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import dagger.model.Key;
import dagger.model.RequestKind;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

/** A binding expression that uses an instance of a {@link FrameworkType}. */
final class FrameworkInstanceBindingExpression extends BindingExpression {
  private final ComponentBindingExpressions componentBindingExpressions;
  private final FrameworkFieldSupplier frameworkFieldSupplier;
  private final FrameworkType frameworkType;
  private final DaggerTypes types;
  private final Elements elements;

  FrameworkInstanceBindingExpression(
      ResolvedBindings resolvedBindings,
      RequestKind requestKind,
      ComponentBindingExpressions componentBindingExpressions,
      FrameworkType frameworkType,
      FrameworkFieldSupplier frameworkFieldSupplier,
      DaggerTypes types,
      Elements elements) {
    super(resolvedBindings, requestKind);
    this.componentBindingExpressions = componentBindingExpressions;
    this.frameworkType = frameworkType;
    this.frameworkFieldSupplier = frameworkFieldSupplier;
    this.types = types;
    this.elements = elements;
  }

  /**
   * The expression for the framework instance for this binding. If the instance comes from a
   * component field, it will be {@link GeneratedComponentModel#addInitialization(CodeBlock)
   * initialized} and {@link GeneratedComponentModel#addField(GeneratedComponentModel.FieldSpecKind,
   * FieldSpec) added} to the component the first time this method is invoked.
   */
  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    if (requestKind().equals(frameworkRequestKind())) {
      MemberSelect memberSelect = frameworkFieldSupplier.memberSelect();
      TypeMirror expressionType =
          isTypeAccessibleFrom(instanceType(), requestingClass.packageName())
                  || isInlinedFactoryCreation(memberSelect)
                  || frameworkFieldSupplier.fieldTypeReplaced()
              ? types.wrapType(instanceType(), resolvedBindings().frameworkClass())
              : rawFrameworkType();
      return Expression.create(expressionType, memberSelect.getExpressionFor(requestingClass));
    }

    // The following expressions form a composite with the expression for the framework type. For
    // example, the expression for RequestKind.LAZY is a composite of the expression for a
    // RequestKind.PROVIDER (the framework type):
    //    lazyExpression = DoubleCheck.lazy(providerExpression);
    return frameworkType.to(
        requestKind(),
        componentBindingExpressions.getDependencyExpression(
            key(), frameworkRequestKind(), requestingClass),
        types);
  }

  /** Returns the request kind that matches the framework type. */
  private RequestKind frameworkRequestKind() {
    switch (frameworkType) {
      case PROVIDER:
        return RequestKind.PROVIDER;
      case PRODUCER:
        return RequestKind.PRODUCER;
      default:
        throw new AssertionError(frameworkType);
    }
  }

  /**
   * The instance type {@code T} of this {@code FrameworkType<T>}. For {@link
   * MembersInjectionBinding}s, this is the {@linkplain Key#type() key type}; for {@link
   * ContributionBinding}s, this the {@link ContributionBinding#contributedType()}.
   */
  private TypeMirror instanceType() {
    return resolvedBindings()
        .membersInjectionBinding()
        .map(binding -> binding.key().type())
        .orElseGet(() -> resolvedBindings().contributionBinding().contributedType());
  }

  /**
   * Returns {@code true} if a factory is created inline each time it is requested. For example, in
   * the initialization {@code this.fooProvider = Foo_Factory.create(Bar_Factory.create());}, {@code
   * Bar_Factory} is considered to be inline.
   *
   * <p>This is used in {@link #getDependencyExpression(ClassName)} when determining the type of a
   * factory. Normally if the {@link #instanceType()} is not accessible from the component, the type
   * of the expression will be a raw {@link javax.inject.Provider}. However, if the factory is
   * created inline, even if contributed type is not accessible, javac will still be able to
   * determine the type that is returned from the {@code Foo_Factory.create()} method.
   */
  private static boolean isInlinedFactoryCreation(MemberSelect memberSelect) {
    return memberSelect.staticMember();
  }

  private DeclaredType rawFrameworkType() {
    return types.getDeclaredType(
        elements.getTypeElement(resolvedBindings().frameworkClass().getCanonicalName()));
  }
}

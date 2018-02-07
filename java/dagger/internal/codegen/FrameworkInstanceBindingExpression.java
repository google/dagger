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

import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.Accessibility.isTypeAccessibleFrom;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import dagger.model.RequestKind;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

/** A binding expression that uses an instance of a {@link FrameworkType}. */
final class FrameworkInstanceBindingExpression extends BindingExpression {
  private final ResolvedBindings resolvedBindings;
  private final RequestKind requestKind;
  private final ComponentBindingExpressions componentBindingExpressions;
  private final FrameworkInstanceSupplier frameworkInstanceSupplier;
  private final FrameworkType frameworkType;
  private final DaggerTypes types;
  private final Elements elements;

  FrameworkInstanceBindingExpression(
      ResolvedBindings resolvedBindings,
      RequestKind requestKind,
      ComponentBindingExpressions componentBindingExpressions,
      FrameworkType frameworkType,
      FrameworkInstanceSupplier frameworkInstanceSupplier,
      DaggerTypes types,
      Elements elements) {
    this.resolvedBindings = checkNotNull(resolvedBindings);
    this.requestKind = checkNotNull(requestKind);
    this.componentBindingExpressions = checkNotNull(componentBindingExpressions);
    this.frameworkType = checkNotNull(frameworkType);
    this.frameworkInstanceSupplier = checkNotNull(frameworkInstanceSupplier);
    this.types = checkNotNull(types);
    this.elements = checkNotNull(elements);
  }

  /**
   * The expression for the framework instance for this binding. If the instance comes from a
   * component field, it will be {@link GeneratedComponentModel#addInitialization(CodeBlock)
   * initialized} and {@link GeneratedComponentModel#addField(GeneratedComponentModel.FieldSpecKind,
   * FieldSpec) added} to the component the first time this method is invoked.
   */
  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    if (requestKind.equals(frameworkRequestKind())) {
      MemberSelect memberSelect = frameworkInstanceSupplier.memberSelect();
      TypeMirror contributedType = resolvedBindings.contributionBinding().contributedType();
      TypeMirror expressionType =
          frameworkInstanceSupplier.specificType().isPresent()
                  || isTypeAccessibleFrom(contributedType, requestingClass.packageName())
                  || isInlinedFactoryCreation(memberSelect)
              ? types.wrapType(contributedType, resolvedBindings.frameworkClass())
              : rawFrameworkType();
      return Expression.create(expressionType, memberSelect.getExpressionFor(requestingClass));
    }

    // The following expressions form a composite with the expression for the framework type. For
    // example, the expression for RequestKind.LAZY is a composite of the expression for a
    // RequestKind.PROVIDER (the framework type):
    //    lazyExpression = DoubleCheck.lazy(providerExpression);
    return frameworkType.to(
        requestKind,
        componentBindingExpressions.getDependencyExpression(
            resolvedBindings.key(), frameworkRequestKind(), requestingClass),
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
   * Returns {@code true} if a factory is created inline each time it is requested. For example, in
   * the initialization {@code this.fooProvider = Foo_Factory.create(Bar_Factory.create());}, {@code
   * Bar_Factory} is considered to be inline.
   *
   * <p>This is used in {@link #getDependencyExpression(ClassName)} when determining the type of a
   * factory. Normally if the {@link ContributionBinding#contributedType()} is not accessible from
   * the component, the type of the expression will be a raw {@link javax.inject.Provider}. However,
   * if the factory is created inline, even if contributed type is not accessible, javac will still
   * be able to determine the type that is returned from the {@code Foo_Factory.create()} method.
   */
  private static boolean isInlinedFactoryCreation(MemberSelect memberSelect) {
    return memberSelect.staticMember();
  }

  private DeclaredType rawFrameworkType() {
    return types.getDeclaredType(
        elements.getTypeElement(resolvedBindings.frameworkClass().getCanonicalName()));
  }
}

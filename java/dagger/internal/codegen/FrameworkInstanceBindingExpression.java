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
import static dagger.internal.codegen.langmodel.Accessibility.isTypeAccessibleFrom;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/** A binding expression that uses a {@link FrameworkType} field. */
abstract class FrameworkInstanceBindingExpression extends BindingExpression {
  private final ResolvedBindings resolvedBindings;
  private final FrameworkInstanceSupplier frameworkInstanceSupplier;
  private final DaggerTypes types;
  private final DaggerElements elements;

  FrameworkInstanceBindingExpression(
      ResolvedBindings resolvedBindings,
      FrameworkInstanceSupplier frameworkInstanceSupplier,
      DaggerTypes types,
      DaggerElements elements) {
    this.resolvedBindings = checkNotNull(resolvedBindings);
    this.frameworkInstanceSupplier = checkNotNull(frameworkInstanceSupplier);
    this.types = checkNotNull(types);
    this.elements = checkNotNull(elements);
  }

  /**
   * The expression for the framework instance for this binding. The field will be {@link
   * ComponentImplementation#addInitialization(CodeBlock) initialized} and {@link
   * ComponentImplementation#addField(ComponentImplementation.FieldSpecKind, FieldSpec) added} to
   * the component the first time this method is invoked.
   */
  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    MemberSelect memberSelect = frameworkInstanceSupplier.memberSelect();
    TypeMirror contributedType = resolvedBindings.contributionBinding().contributedType();
    TypeMirror expressionType =
        isTypeAccessibleFrom(contributedType, requestingClass.packageName())
                || isInlinedFactoryCreation(memberSelect)
            ? types.wrapType(contributedType, frameworkType().frameworkClass())
            : rawFrameworkType();
    return Expression.create(expressionType, memberSelect.getExpressionFor(requestingClass));
  }

  /** Returns the framework type for the binding. */
  protected abstract FrameworkType frameworkType();

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
    return types.getDeclaredType(elements.getTypeElement(frameworkType().frameworkClass()));
  }
}

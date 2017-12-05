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

import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.Accessibility.isTypeAccessibleFrom;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import java.util.Optional;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

/** A binding expression that uses an instance of a {@link FrameworkType}. */
final class FrameworkInstanceBindingExpression extends BindingExpression {
  private final MemberSelect memberSelect;
  private final FrameworkType frameworkType;
  private final Optional<FrameworkFieldInitializer> fieldInitializer;
  private final DaggerTypes types;
  private final Elements elements;

  /** Returns a binding expression for a binding. */
  static FrameworkInstanceBindingExpression create(
      ResolvedBindings resolvedBindings,
      MemberSelect memberSelect,
      Optional<FrameworkFieldInitializer> frameworkFieldInitializer,
      DaggerTypes types,
      Elements elements) {
    return new FrameworkInstanceBindingExpression(
        resolvedBindings,
        memberSelect,
        resolvedBindings.bindingType().frameworkType(),
        frameworkFieldInitializer,
        types,
        elements);
  }

  private FrameworkInstanceBindingExpression(
      ResolvedBindings resolvedBindings,
      MemberSelect memberSelect,
      FrameworkType frameworkType,
      Optional<FrameworkFieldInitializer> fieldInitializer,
      DaggerTypes types,
      Elements elements) {
    super(resolvedBindings);
    this.memberSelect = memberSelect;
    this.frameworkType = frameworkType;
    this.fieldInitializer = fieldInitializer;
    this.types = types;
    this.elements = elements;
  }

  FrameworkInstanceBindingExpression producerFromProvider(
      MemberSelect memberSelect, FrameworkFieldInitializer producerFieldInitializer) {
    checkState(frameworkType.equals(FrameworkType.PROVIDER));
    return new FrameworkInstanceBindingExpression(
        resolvedBindings(),
        memberSelect,
        FrameworkType.PRODUCER,
        Optional.of(producerFieldInitializer),
        types,
        elements);
  }

  /**
   * The expression for the framework instance for this binding. If the instance comes from a
   * component field, it will be {@link GeneratedComponentModel#addInitialization(CodeBlock)
   * initialized} and {@link GeneratedComponentModel#addField(GeneratedComponentModel.FieldSpecKind,
   * FieldSpec) added} to the component the first time this method is invoked.
   */
  @Override
  Expression getDependencyExpression(
      DependencyRequest.Kind requestKind, ClassName requestingClass) {
    fieldInitializer.ifPresent(FrameworkFieldInitializer::initializeField);
    TypeMirror expressionType =
        isTypeAccessibleFrom(instanceType(), requestingClass.packageName())
                || isInlinedFactoryCreation()
            ? types.wrapType(instanceType(), resolvedBindings().frameworkClass())
            : rawFrameworkType();

    return frameworkType.to(
        requestKind,
        Expression.create(expressionType, memberSelect.getExpressionFor(requestingClass)),
        types);
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
   * <p>This is used in {@link #getDependencyExpression(DependencyRequest.Kind, ClassName)} when
   * determining the type of a factory. Normally if the {@link #instanceType()} is not accessible
   * from the component, the type of the expression will be a raw {@link javax.inject.Provider}.
   * However, if the factory is created inline, even if contributed type is not accessible, javac
   * will still be able to determine the type that is returned from the {@code Foo_Factory.create()}
   * method.
   */
  private boolean isInlinedFactoryCreation() {
    return memberSelect.staticMember();
  }

  private DeclaredType rawFrameworkType() {
    return types.getDeclaredType(
        elements.getTypeElement(resolvedBindings().frameworkClass().getCanonicalName()));
  }
}

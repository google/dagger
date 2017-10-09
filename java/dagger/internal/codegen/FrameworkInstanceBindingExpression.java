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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.Accessibility.isTypeAccessibleFrom;
import static dagger.internal.codegen.TypeNames.DELEGATE_FACTORY;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import dagger.internal.DelegateFactory;
import java.util.Optional;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

/** A binding expression that uses an instance of a {@link FrameworkType}. */
final class FrameworkInstanceBindingExpression extends BindingExpression {
  private final Optional<FieldSpec> fieldSpec;
  private final GeneratedComponentModel generatedComponentModel;
  private final MemberSelect memberSelect;
  private final FrameworkType frameworkType;
  private final FrameworkFieldInitializer fieldInitializer;
  private final DaggerTypes types;
  private final Elements elements;
  private InitializationState fieldInitializationState = InitializationState.UNINITIALIZED;

  /** Returns a binding expression for a binding. */
  static FrameworkInstanceBindingExpression create(
      ResolvedBindings resolvedBindings,
      Optional<FieldSpec> fieldSpec,
      GeneratedComponentModel generatedComponentModel,
      MemberSelect memberSelect,
      FrameworkFieldInitializer frameworkFieldInitializer,
      DaggerTypes types,
      Elements elements) {
    return new FrameworkInstanceBindingExpression(
        resolvedBindings,
        fieldSpec,
        generatedComponentModel,
        memberSelect,
        resolvedBindings.bindingType().frameworkType(),
        frameworkFieldInitializer,
        types,
        elements);
  }

  private FrameworkInstanceBindingExpression(
      ResolvedBindings resolvedBindings,
      Optional<FieldSpec> fieldSpec,
      GeneratedComponentModel generatedComponentModel,
      MemberSelect memberSelect,
      FrameworkType frameworkType,
      FrameworkFieldInitializer fieldInitializer,
      DaggerTypes types,
      Elements elements) {
    super(resolvedBindings);
    this.generatedComponentModel = generatedComponentModel;
    this.memberSelect = memberSelect;
    this.fieldSpec = fieldSpec;
    this.frameworkType = frameworkType;
    this.fieldInitializer = fieldInitializer;
    this.types = types;
    this.elements = elements;
  }

  FrameworkInstanceBindingExpression producerFromProvider(
      FieldSpec fieldSpec, ClassName componentName) {
    checkState(frameworkType.equals(FrameworkType.PROVIDER));
    return new FrameworkInstanceBindingExpression(
        resolvedBindings(),
        Optional.of(fieldSpec),
        generatedComponentModel,
        MemberSelect.localField(componentName, fieldSpec.name),
        FrameworkType.PRODUCER,
        fieldInitializer.forProducerFromProvider(),
        types,
        elements);
  }

  /**
   * The expression for the framework instance for this binding. If the instance comes from a
   * component field, it will be {@link GeneratedComponentModel#addInitialization(CodeBlock)
   * initialized} and {@link GeneratedComponentModel#addField(FieldSpec) added} to the component the
   * first time this method is invoked.
   */
  @Override
  Expression getDependencyExpression(
      DependencyRequest.Kind requestKind, ClassName requestingClass) {
    maybeInitializeField();
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
   * <p>This is used in {@link #getDependencyExpression(Kind, ClassName)} when determining the type
   * of a factory. Normally if the {@link #instanceType()} is not accessible from the component, the
   * type of the expression will be a raw {@link javax.inject.Provider}. However, if the factory is
   * created inline, even if contributed type is not accessible, javac will still be able to
   * determine the type that is returned from the {@code Foo_Factory.create()} method.
   */
  private boolean isInlinedFactoryCreation() {
    return memberSelect.staticMember();
  }

  private DeclaredType rawFrameworkType() {
    return types.getDeclaredType(
        elements.getTypeElement(resolvedBindings().frameworkClass().getCanonicalName()));
  }

  /**
   * Returns the name of the binding's underlying field.
   *
   * @throws UnsupportedOperationException if no field exists
   */
  // TODO(ronshapiro): remove this in favor of $N in a CodeBlock
  private String fieldName() {
    checkHasField();
    return fieldSpec.get().name;
  }

  /**
   * Sets the initialization state for the binding's underlying field. Only valid for field types.
   *
   * @throws UnsupportedOperationException if no field exists
   */
  private void setFieldInitializationState(InitializationState fieldInitializationState) {
    checkHasField();
    checkArgument(this.fieldInitializationState.compareTo(fieldInitializationState) < 0);
    this.fieldInitializationState = fieldInitializationState;
  }

  private void checkHasField() {
    if (!fieldSpec.isPresent()) {
      throw new UnsupportedOperationException();
    }
  }

  // Adds our field and initialization of our field to the component.
  // TODO(user): Move this to the field initializer class
  private void maybeInitializeField() {
    if (!fieldSpec.isPresent()) {
      return;
    }
    switch (fieldInitializationState) {
      case UNINITIALIZED:
        // Change our state in case we are recursively invoked via initializeBindingExpression
        setFieldInitializationState(InitializationState.INITIALIZING);
        CodeBlock.Builder codeBuilder = CodeBlock.builder();
        CodeBlock initCode =
            CodeBlock.of(
                "this.$L = $L;",
                fieldName(),
                checkNotNull(fieldInitializer.getFieldInitialization()));

        if (fieldInitializationState == InitializationState.DELEGATED) {
          // If we were recursively invoked, set the delegate factory as part of our initialization
          String delegateFactoryVariable = fieldName() + "Delegate";
          codeBuilder
              .add("$1T $2L = ($1T) $3L;", DELEGATE_FACTORY, delegateFactoryVariable, fieldName())
              .add(initCode)
              .add("$L.setDelegatedProvider($L);", delegateFactoryVariable, fieldName())
              .build();
        } else {
          codeBuilder.add(initCode);
        }
        generatedComponentModel.addInitialization(codeBuilder.build());
        generatedComponentModel.addField(fieldSpec.get());

        setFieldInitializationState(InitializationState.INITIALIZED);
        break;

      case INITIALIZING:
        // We were recursively invoked, so create a delegate factory instead
        generatedComponentModel.addInitialization(
            CodeBlock.of("this.$L = new $T<>();", fieldName(), DELEGATE_FACTORY));
        setFieldInitializationState(InitializationState.DELEGATED);
        break;

      case DELEGATED:
      case INITIALIZED:
        break;
      default:
        throw new AssertionError("Unhandled initialization state: " + fieldInitializationState);
    }
  }

  /** Initialization state for a factory field. */
  private enum InitializationState {
    /** The field is {@code null}. */
    UNINITIALIZED,

    /**
     * The field's dependencies are being set up. If the field is needed in this state, use a {@link
     * DelegateFactory}.
     */
    INITIALIZING,

    /**
     * The field's dependencies are being set up, but the field can be used because it has already
     * been set to a {@link DelegateFactory}.
     */
    DELEGATED,

    /** The field is set to an undelegated factory. */
    INITIALIZED;
  }
}

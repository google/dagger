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
import static dagger.internal.Preconditions.checkNotNull;
import static dagger.internal.codegen.TypeNames.DELEGATE_FACTORY;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import dagger.internal.DelegateFactory;
import java.util.Optional;

/** A binding expression that uses an instance of a {@link FrameworkType}. */
// TODO(user): consider removing this class and making it a strategy that is created by
// {Provider,Producer,MembersInjector}BindingExpression
abstract class FrameworkInstanceBindingExpression extends BindingExpression {
  private final Optional<FieldSpec> fieldSpec;
  private final GeneratedComponentModel generatedComponentModel;
  private final MemberSelect memberSelect;
  private InitializationState fieldInitializationState = InitializationState.UNINITIALIZED;

  protected FrameworkInstanceBindingExpression(
      BindingKey bindingKey,
      Optional<FieldSpec> fieldSpec,
      GeneratedComponentModel generatedComponentModel,
      MemberSelect memberSelect) {
    super(bindingKey);
    this.generatedComponentModel = generatedComponentModel;
    this.memberSelect = memberSelect;
    this.fieldSpec = fieldSpec;
  }

  /**
   * The expression for the framework instance for this binding. If the instance comes from a
   * component field, it will be {@link GeneratedComponentModel#addInitialization(CodeBlock)
   * initialized} and {@link GeneratedComponentModel#addField(FieldSpec) added} to the component the
   * first time this method is invoked.
   */
  final CodeBlock getFrameworkTypeInstance(ClassName requestingClass) {
    maybeInitializeField();
    return memberSelect.getExpressionFor(requestingClass);
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

  /** Returns true if this binding expression represents a producer from provider. */
  // TODO(user): remove this and represent this via a subtype of BindingExpression
  abstract boolean isProducerFromProvider();

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
                checkNotNull(generatedComponentModel.getFieldInitialization(this)));

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
            CodeBlock.of("this.$L = new $T();", fieldName(), DELEGATE_FACTORY));
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

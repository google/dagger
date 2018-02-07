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
import static dagger.internal.codegen.AnnotationSpecs.Suppression.RAWTYPES;
import static dagger.internal.codegen.GeneratedComponentModel.FieldSpecKind.FRAMEWORK_FIELD;
import static javax.lang.model.element.Modifier.PRIVATE;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.TypeName;
import dagger.internal.DelegateFactory;
import java.util.Optional;

/**
 * An object that can initialize a framework-type component field for a binding. An instance should
 * be created for each field.
 */
class FrameworkFieldInitializer implements FrameworkInstanceSupplier {

  /**
   * An object that can determine the expression to use to assign to the component field for a
   * binding.
   */
  interface FrameworkInstanceCreationExpression {
    /** Returns the expression to use to assign to the component field for the binding. */
    CodeBlock creationExpression();

    /**
     * Returns the type of the creation expression when it is a specific factory type. This
     * implementation returns {@link Optional#empty()}.
     */
    default Optional<TypeName> specificType() {
      return Optional.empty();
    }

    /**
     * Returns the framework class to use for the field, if different from the one implied by the
     * binding. This implementation returns {@link Optional#empty()}.
     */
    default Optional<ClassName> alternativeFrameworkClass() {
      return Optional.empty();
    }

    /**
     * Returns {@code true} if the factory created for a binding is not worth inlining because it's
     * a singleton or an {@link dagger.internal.InstanceFactory} or a nested {@code Provider} for a
     * component dependency provision method.
     */
    default boolean isSimpleFactory() {
      return false;
    }
  }

  private final GeneratedComponentModel generatedComponentModel;
  private final ResolvedBindings resolvedBindings;
  private final FrameworkInstanceCreationExpression frameworkInstanceCreationExpression;
  private FieldSpec fieldSpec;
  private InitializationState fieldInitializationState = InitializationState.UNINITIALIZED;

  FrameworkFieldInitializer(
      GeneratedComponentModel generatedComponentModel,
      ResolvedBindings resolvedBindings,
      FrameworkInstanceCreationExpression frameworkInstanceCreationExpression) {
    this.generatedComponentModel = checkNotNull(generatedComponentModel);
    this.resolvedBindings = checkNotNull(resolvedBindings);
    this.frameworkInstanceCreationExpression = checkNotNull(frameworkInstanceCreationExpression);
  }

  /**
   * Returns the {@link MemberSelect} for the framework field, and adds the field and its
   * initialization code to the component if it's needed and not already added.
   */
  @Override
  public final MemberSelect memberSelect() {
    initializeField();
    return MemberSelect.localField(generatedComponentModel.name(), checkNotNull(fieldSpec).name);
  }

  /** Adds the field and its initialization code to the component. */
  private void initializeField() {
    switch (fieldInitializationState) {
      case UNINITIALIZED:
        // Change our state in case we are recursively invoked via initializeBindingExpression
        fieldInitializationState = InitializationState.INITIALIZING;
        CodeBlock.Builder codeBuilder = CodeBlock.builder();
        CodeBlock fieldInitialization = frameworkInstanceCreationExpression.creationExpression();
        CodeBlock initCode = CodeBlock.of("this.$N = $L;", getOrCreateField(), fieldInitialization);

        if (fieldInitializationState == InitializationState.DELEGATED) {
          // If we were recursively invoked, set the delegate factory as part of our initialization
          CodeBlock delegateFactoryVariable = CodeBlock.of("$NDelegate", fieldSpec);
          codeBuilder
              .add(
                  "$1T $2L = ($1T) $3N;", DelegateFactory.class, delegateFactoryVariable, fieldSpec)
              .add(initCode)
              .add("$L.setDelegatedProvider($N);", delegateFactoryVariable, fieldSpec);
        } else {
          codeBuilder.add(initCode);
        }
        generatedComponentModel.addInitialization(codeBuilder.build());

        fieldInitializationState = InitializationState.INITIALIZED;
        break;

      case INITIALIZING:
        // We were recursively invoked, so create a delegate factory instead
        fieldInitializationState = InitializationState.DELEGATED;
        generatedComponentModel.addInitialization(
            CodeBlock.of("this.$N = new $T<>();", getOrCreateField(), DelegateFactory.class));
        break;

      case DELEGATED:
      case INITIALIZED:
        break;

      default:
        throw new AssertionError("Unhandled initialization state: " + fieldInitializationState);
    }
  }

  /**
   * Adds a field representing the resolved bindings, optionally forcing it to use a particular
   * binding type (instead of the type the resolved bindings would typically use).
   */
  private FieldSpec getOrCreateField() {
    if (fieldSpec != null) {
      return fieldSpec;
    }
    boolean useRawType = !generatedComponentModel.isTypeAccessible(resolvedBindings.key().type());
    FrameworkField contributionBindingField =
        FrameworkField.forResolvedBindings(
            resolvedBindings, frameworkInstanceCreationExpression.alternativeFrameworkClass());

    TypeName fieldType;
    if (!fieldInitializationState.equals(InitializationState.DELEGATED)
        && specificType().isPresent()) {
      // For some larger components, this causes javac to compile much faster by getting the
      // field type to exactly match the type of the expression being assigned to it.
      fieldType = specificType().get();
    } else if (useRawType) {
      fieldType = contributionBindingField.type().rawType;
    } else {
      fieldType = contributionBindingField.type();
    }

    FieldSpec.Builder contributionField =
        FieldSpec.builder(
            fieldType,
            generatedComponentModel.getUniqueFieldName(contributionBindingField.name()));
    contributionField.addModifiers(PRIVATE);
    if (useRawType && !specificType().isPresent()) {
      contributionField.addAnnotation(AnnotationSpecs.suppressWarnings(RAWTYPES));
    }
    fieldSpec = contributionField.build();
    generatedComponentModel.addField(FRAMEWORK_FIELD, fieldSpec);

    return fieldSpec;
  }

  /** Returns the type of the instance when it is a specific factory type. */
  @Override
  public Optional<TypeName> specificType() {
    return frameworkInstanceCreationExpression.specificType();
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

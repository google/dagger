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
import static dagger.internal.codegen.AnnotationSpecs.Suppression.RAWTYPES;
import static dagger.internal.codegen.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.GeneratedComponentModel.FieldSpecKind.FRAMEWORK_FIELD;
import static javax.lang.model.element.Modifier.PRIVATE;

import com.google.common.collect.ImmutableList;
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
abstract class FrameworkFieldInitializer implements FrameworkFieldSupplier {

  protected final GeneratedComponentModel generatedComponentModel;
  private final ComponentBindingExpressions componentBindingExpressions;
  private final ResolvedBindings resolvedBindings;
  private FieldSpec fieldSpec;
  private InitializationState fieldInitializationState = InitializationState.UNINITIALIZED;

  /**
   * Indicates the type of the initializer when has been replaced with a more-specific factory type.
   * This is used by {@code FrameworkInstanceBindingExpression} to create fields with the
   * most-specific type available.  This allows javac to complete much faster for large components.
   */
  private Optional<TypeName> fieldTypeReplacement = Optional.empty();

  protected FrameworkFieldInitializer(
      GeneratedComponentModel generatedComponentModel,
      ComponentBindingExpressions componentBindingExpressions,
      ResolvedBindings resolvedBindings) {
    this.generatedComponentModel = checkNotNull(generatedComponentModel);
    this.componentBindingExpressions = checkNotNull(componentBindingExpressions);
    this.resolvedBindings = checkNotNull(resolvedBindings);
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
        CodeBlock fieldInitialization = getFieldInitialization();
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
        generatedComponentModel.addInitialization(
            CodeBlock.of("this.$N = new $T<>();", getOrCreateField(), DelegateFactory.class));
        fieldInitializationState = InitializationState.DELEGATED;
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
    boolean useRawType =
        !isTypeAccessibleFrom(
            resolvedBindings.key().type(), generatedComponentModel.name().packageName());
    FrameworkField contributionBindingField =
        FrameworkField.forResolvedBindings(resolvedBindings, alternativeFrameworkClass());

    TypeName fieldType;
    if (fieldTypeReplacement.isPresent()) {
      // For some larger components, this causes javac to compile much faster by getting the
      // field type to exactly match the type of the expression being assigned to it.
      fieldType = fieldTypeReplacement.get();
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
    if (useRawType && !fieldTypeReplacement.isPresent()) {
      contributionField.addAnnotation(AnnotationSpecs.suppressWarnings(RAWTYPES));
    }
    fieldSpec = contributionField.build();
    generatedComponentModel.addField(FRAMEWORK_FIELD, fieldSpec);

    return fieldSpec;
  }

  @Override
  public boolean fieldTypeReplaced() {
    return fieldTypeReplacement.isPresent();
  }

  /**
   * Returns the framework class to use for the field, if different from the one implied by the
   * binding. This implementation returns {@link Optional#empty()}.
   */
  protected Optional<ClassName> alternativeFrameworkClass() {
    return Optional.empty();
  }

  /** Returns the expression to use to initialize the field. */
  protected abstract CodeBlock getFieldInitialization();

  /** Returns a list of code blocks for referencing all of the given binding's dependencies. */
  protected final ImmutableList<CodeBlock> getBindingDependencyExpressions(Binding binding) {
    ImmutableList<FrameworkDependency> dependencies = binding.frameworkDependencies();
    return dependencies.stream().map(this::getDependencyExpression).collect(toImmutableList());
  }

  /** Returns a code block referencing the given dependency. */
  protected final CodeBlock getDependencyExpression(FrameworkDependency frameworkDependency) {
    return componentBindingExpressions
        .getDependencyExpression(frameworkDependency, generatedComponentModel.name())
        .codeBlock();
  }

  protected final void setFieldTypeReplacement(TypeName typeName) {
    this.fieldTypeReplacement = Optional.of(typeName);
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

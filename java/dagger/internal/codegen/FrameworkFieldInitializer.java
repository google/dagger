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
import static dagger.internal.codegen.ComponentImplementation.FieldSpecKind.FRAMEWORK_FIELD;
import static dagger.internal.codegen.javapoet.AnnotationSpecs.Suppression.RAWTYPES;
import static javax.lang.model.element.Modifier.PRIVATE;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.TypeName;
import dagger.internal.DelegateFactory;
import dagger.internal.codegen.javapoet.AnnotationSpecs;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.producers.internal.DelegateProducer;
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
     * Returns the framework class to use for the field, if different from the one implied by the
     * binding. This implementation returns {@link Optional#empty()}.
     */
    default Optional<ClassName> alternativeFrameworkClass() {
      return Optional.empty();
    }

    /**
     * Returns {@code true} if instead of using {@link #creationExpression()} to create a framework
     * instance, a case in {@link InnerSwitchingProviders} should be created for this binding.
     */
    // TODO(ronshapiro): perhaps this isn't the right approach. Instead of saying "Use
    // SetFactory.EMPTY because you will only get 1 class for all types of bindings that use
    // SetFactory", maybe we should still use an inner switching provider but the same switching
    // provider index for all cases.
    default boolean useInnerSwitchingProvider() {
      return true;
    }
  }

  private final ComponentImplementation componentImplementation;
  private final ResolvedBindings resolvedBindings;
  private final FrameworkInstanceCreationExpression frameworkInstanceCreationExpression;
  private FieldSpec fieldSpec;
  private InitializationState fieldInitializationState = InitializationState.UNINITIALIZED;

  FrameworkFieldInitializer(
      ComponentImplementation componentImplementation,
      ResolvedBindings resolvedBindings,
      FrameworkInstanceCreationExpression frameworkInstanceCreationExpression) {
    this.componentImplementation = checkNotNull(componentImplementation);
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
    return MemberSelect.localField(componentImplementation.name(), checkNotNull(fieldSpec).name);
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

        if (isReplacingSuperclassFrameworkInstance()
            || fieldInitializationState == InitializationState.DELEGATED) {
          codeBuilder.add(
              "$T.setDelegate($N, $L);", delegateType(), fieldSpec, fieldInitialization);
        } else {
          codeBuilder.add(initCode);
        }
        componentImplementation.addInitialization(codeBuilder.build());

        fieldInitializationState = InitializationState.INITIALIZED;
        break;

      case INITIALIZING:
        // We were recursively invoked, so create a delegate factory instead
        fieldInitializationState = InitializationState.DELEGATED;
        componentImplementation.addInitialization(
            CodeBlock.of("this.$N = new $T<>();", getOrCreateField(), delegateType()));
        break;

      case DELEGATED:
      case INITIALIZED:
        break;
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
    boolean useRawType = !componentImplementation.isTypeAccessible(resolvedBindings.key().type());
    FrameworkField contributionBindingField =
        FrameworkField.forResolvedBindings(
            resolvedBindings, frameworkInstanceCreationExpression.alternativeFrameworkClass());

    TypeName fieldType =
        useRawType ? contributionBindingField.type().rawType : contributionBindingField.type();

    FieldSpec.Builder contributionField =
        FieldSpec.builder(
            fieldType, componentImplementation.getUniqueFieldName(contributionBindingField.name()));
    contributionField.addModifiers(PRIVATE);
    if (useRawType) {
      contributionField.addAnnotation(AnnotationSpecs.suppressWarnings(RAWTYPES));
    }

    if (isReplacingSuperclassFrameworkInstance()) {
      // If a binding is modified in a subclass, the framework instance will be replaced in the
      // subclass implementation. The superclass framework instance initialization will run first,
      // however, and may refer to the modifiable binding method returning this type's modified
      // framework instance before it is initialized, so we use a delegate factory as a placeholder
      // until it has properly been initialized.
      contributionField.initializer("new $T<>()", delegateType());
    }

    fieldSpec = contributionField.build();
    componentImplementation.addField(FRAMEWORK_FIELD, fieldSpec);

    return fieldSpec;
  }

  /**
   * Returns true if this framework field is replacing a superclass's implementation of the
   * framework field.
   */
  private boolean isReplacingSuperclassFrameworkInstance() {
    return componentImplementation
        .superclassImplementation()
        .flatMap(
            superclassImplementation ->
                // TODO(b/117833324): can we constrain this further?
                superclassImplementation.getModifiableBindingMethod(
                    BindingRequest.bindingRequest(
                        resolvedBindings.key(),
                        isProvider() ? FrameworkType.PROVIDER : FrameworkType.PRODUCER_NODE)))
        .isPresent();
  }

  private Class<?> delegateType() {
    return isProvider() ? DelegateFactory.class : DelegateProducer.class;
  }

  private boolean isProvider() {
    return resolvedBindings.bindingType().equals(BindingType.PROVISION)
        && frameworkInstanceCreationExpression
            .alternativeFrameworkClass()
            .map(TypeNames.PROVIDER::equals)
            .orElse(true);
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

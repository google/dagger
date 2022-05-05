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

package dagger.internal.codegen.writing;

import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.binding.SourceFiles.generatedClassNameForBinding;
import static dagger.internal.codegen.javapoet.AnnotationSpecs.Suppression.RAWTYPES;
import static dagger.internal.codegen.writing.ComponentImplementation.FieldSpecKind.FRAMEWORK_FIELD;
import static javax.lang.model.element.Modifier.PRIVATE;

import androidx.room.compiler.processing.XType;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import dagger.internal.DelegateFactory;
import dagger.internal.codegen.binding.BindingType;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.binding.FrameworkField;
import dagger.internal.codegen.javapoet.AnnotationSpecs;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.writing.ComponentImplementation.CompilerMode;
import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;
import dagger.spi.model.BindingKind;
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
  }

  private final ShardImplementation shardImplementation;
  private final ContributionBinding binding;
  private final FrameworkInstanceCreationExpression frameworkInstanceCreationExpression;
  private final CompilerMode compilerMode;
  private FieldSpec fieldSpec;
  private InitializationState fieldInitializationState = InitializationState.UNINITIALIZED;

  FrameworkFieldInitializer(
      ComponentImplementation componentImplementation,
      ContributionBinding binding,
      FrameworkInstanceCreationExpression frameworkInstanceCreationExpression) {
    this.binding = checkNotNull(binding);
    this.shardImplementation = checkNotNull(componentImplementation).shardImplementation(binding);
    this.compilerMode = componentImplementation.compilerMode();
    this.frameworkInstanceCreationExpression = checkNotNull(frameworkInstanceCreationExpression);
  }

  /**
   * Returns the {@link MemberSelect} for the framework field, and adds the field and its
   * initialization code to the component if it's needed and not already added.
   */
  @Override
  public final MemberSelect memberSelect() {
    initializeField();
    return MemberSelect.localField(shardImplementation, checkNotNull(fieldSpec).name);
  }

  /** Adds the field and its initialization code to the component. */
  private void initializeField() {
    switch (fieldInitializationState) {
      case UNINITIALIZED:
        // Change our state in case we are recursively invoked via initializeRequestRepresentation
        fieldInitializationState = InitializationState.INITIALIZING;
        CodeBlock.Builder codeBuilder = CodeBlock.builder();
        CodeBlock fieldInitialization = frameworkInstanceCreationExpression.creationExpression();
        CodeBlock initCode = CodeBlock.of("this.$N = $L;", getOrCreateField(), fieldInitialization);

        if (fieldInitializationState == InitializationState.DELEGATED) {
          codeBuilder.add(
              "$T.setDelegate($N, $L);", delegateType(), fieldSpec, fieldInitialization);
        } else {
          codeBuilder.add(initCode);
        }
        shardImplementation.addInitialization(codeBuilder.build());

        fieldInitializationState = InitializationState.INITIALIZED;
        break;

      case INITIALIZING:
        fieldSpec = getOrCreateField();
        // We were recursively invoked, so create a delegate factory instead to break the loop.
        // However, because SwitchingProvider takes no dependencies, even if they are recursively
        // invoked, we don't need to delegate it since there is no dependency cycle.
        if (FrameworkInstanceKind.from(binding, compilerMode)
            .equals(FrameworkInstanceKind.SWITCHING_PROVIDER)) {
          break;
        }

        fieldInitializationState = InitializationState.DELEGATED;
        shardImplementation.addInitialization(
            CodeBlock.of("this.$N = new $T<>();", fieldSpec, delegateType()));
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
    boolean useRawType = !shardImplementation.isTypeAccessible(binding.key().type().xprocessing());
    FrameworkField contributionBindingField =
        FrameworkField.forBinding(
            binding, frameworkInstanceCreationExpression.alternativeFrameworkClass());

    TypeName fieldType =
        useRawType ? contributionBindingField.type().rawType : contributionBindingField.type();

    if (binding.kind() == BindingKind.ASSISTED_INJECTION) {
      // An assisted injection factory doesn't extend Provider, so we reference the generated
      // factory type directly (i.e. Foo_Factory<T> instead of Provider<Foo<T>>).
      TypeName[] typeParameters =
          binding.key().type().xprocessing().getTypeArguments().stream()
              .map(XType::getTypeName)
              .toArray(TypeName[]::new);
      fieldType =
          typeParameters.length == 0
              ? generatedClassNameForBinding(binding)
              : ParameterizedTypeName.get(generatedClassNameForBinding(binding), typeParameters);
    }

    FieldSpec.Builder contributionField =
        FieldSpec.builder(
            fieldType, shardImplementation.getUniqueFieldName(contributionBindingField.name()));
    contributionField.addModifiers(PRIVATE);
    if (useRawType) {
      contributionField.addAnnotation(AnnotationSpecs.suppressWarnings(RAWTYPES));
    }

    fieldSpec = contributionField.build();
    shardImplementation.addField(FRAMEWORK_FIELD, fieldSpec);

    return fieldSpec;
  }

  private ClassName delegateType() {
    return isProvider() ? TypeNames.DELEGATE_FACTORY : TypeNames.DELEGATE_PRODUCER;
  }

  private boolean isProvider() {
    return binding.bindingType().equals(BindingType.PROVISION)
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

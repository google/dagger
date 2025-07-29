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

import static androidx.room.compiler.codegen.compat.XConverters.toJavaPoet;
import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.binding.SourceFiles.generatedClassNameForBinding;
import static dagger.internal.codegen.writing.ComponentImplementation.FieldSpecKind.FRAMEWORK_FIELD;
import static dagger.internal.codegen.xprocessing.XAnnotationSpecs.Suppression.RAWTYPES;
import static dagger.internal.codegen.xprocessing.XAnnotationSpecs.suppressWarnings;
import static javax.lang.model.element.Modifier.PRIVATE;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XCodeBlock;
import androidx.room.compiler.codegen.XPropertySpec;
import androidx.room.compiler.codegen.XTypeName;
import androidx.room.compiler.processing.XType;
import dagger.internal.codegen.binding.BindingType;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.binding.FrameworkField;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.model.BindingKind;
import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;
import dagger.internal.codegen.xprocessing.XPropertySpecs;
import dagger.internal.codegen.xprocessing.XTypeNames;
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
    XCodeBlock creationExpression();

    /**
     * Returns the framework class to use for the field, if different from the one implied by the
     * binding. This implementation returns {@link Optional#empty()}.
     */
    default Optional<XClassName> alternativeFrameworkClass() {
      return Optional.empty();
    }

    /**
     * Returns the preferred name for the framework field.
     *
     * <p>The actual name used may be different if there is a naming conflict. If empty, a name will
     * be generated based on the binding key.
     */
    default Optional<String> preferredFieldName() {
      return Optional.empty();
    }
  }

  private final CompilerOptions compilerOptions;
  private final ShardImplementation shardImplementation;
  private final ContributionBinding binding;
  private final FrameworkInstanceCreationExpression frameworkInstanceCreationExpression;
  private XPropertySpec propertySpec;
  private InitializationState fieldInitializationState = InitializationState.UNINITIALIZED;

  FrameworkFieldInitializer(
      CompilerOptions compilerOptions,
      ComponentImplementation componentImplementation,
      ContributionBinding binding,
      FrameworkInstanceCreationExpression frameworkInstanceCreationExpression) {
    this.compilerOptions = checkNotNull(compilerOptions);
    this.binding = checkNotNull(binding);
    this.shardImplementation = checkNotNull(componentImplementation).shardImplementation(binding);
    this.frameworkInstanceCreationExpression = checkNotNull(frameworkInstanceCreationExpression);
  }

  /**
   * Returns the {@link MemberSelect} for the framework field, and adds the field and its
   * initialization code to the component if it's needed and not already added.
   */
  @Override
  public final MemberSelect memberSelect() {
    initializeField();
    return MemberSelect.localField(
        shardImplementation, checkNotNull(toJavaPoet(propertySpec)).name);
  }

  /** Adds the field and its initialization code to the component. */
  private void initializeField() {
    switch (fieldInitializationState) {
      case UNINITIALIZED:
        // Change our state in case we are recursively invoked via initializeRequestRepresentation
        fieldInitializationState = InitializationState.INITIALIZING;
        XCodeBlock.Builder codeBuilder = XCodeBlock.builder();
        XCodeBlock fieldInitialization = frameworkInstanceCreationExpression.creationExpression();
        XCodeBlock initCode =
            XCodeBlock.of("this.%N = %L;", getOrCreateField(), fieldInitialization);

        if (fieldInitializationState == InitializationState.DELEGATED) {
          XCodeBlock delegateFactory = XCodeBlock.of("%N", propertySpec);
          XCodeBlock delegate = fieldInitialization;
          codeBuilder.add("%T.setDelegate(%L, %L);", delegateType(), delegateFactory, delegate);
        } else {
          codeBuilder.add(initCode);
        }
        shardImplementation.addInitialization(codeBuilder.build());

        fieldInitializationState = InitializationState.INITIALIZED;
        break;

      case INITIALIZING:
        propertySpec = getOrCreateField();
        // We were recursively invoked, so create a delegate factory instead to break the loop.

        // TODO(erichang): For the most part SwitchingProvider takes no dependencies so even if they
        // are recursively invoked, we don't need to delegate it since there is no dependency cycle.
        // However, there is a case with a scoped @Binds where we reference the impl binding when
        // passing it into DoubleCheck. For this case, we do need to delegate it. There might be
        // a way to only do delegates in this situation, but we'd need to keep track of what other
        // bindings use this.

        fieldInitializationState = InitializationState.DELEGATED;
        shardImplementation.addInitialization(
            XCodeBlock.of("this.%N = new %T<>();", propertySpec, delegateType()));
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
  private XPropertySpec getOrCreateField() {
    if (propertySpec != null) {
      return propertySpec;
    }
    FrameworkField contributionBindingField =
        FrameworkField.forBinding(
            binding,
            frameworkInstanceCreationExpression.alternativeFrameworkClass(),
            compilerOptions);

    XTypeName fieldType = frameworkFieldType(contributionBindingField);

    if (binding.kind() == BindingKind.ASSISTED_INJECTION) {
      // An assisted injection factory doesn't extend Provider, so we reference the generated
      // factory type directly (i.e. Foo_Factory<T> instead of Provider<Foo<T>>).
      XTypeName[] typeParameters =
          binding.key().type().xprocessing().getTypeArguments().stream()
              .map(XType::asTypeName)
              .toArray(XTypeName[]::new);
      fieldType =
          typeParameters.length == 0
              ? generatedClassNameForBinding(binding)
              : generatedClassNameForBinding(binding).parametrizedBy(typeParameters);
    }

    XPropertySpecs.Builder contributionField =
        XPropertySpecs.builder(
            shardImplementation.getUniqueFieldName(
                frameworkInstanceCreationExpression
                    .preferredFieldName()
                    .orElse(contributionBindingField.name())),
            fieldType);
    // TODO(bcorso): remove once dagger.generatedClassExtendsComponent flag is removed.
    if (!shardImplementation.isShardClassPrivate()) {
      contributionField.addModifiers(PRIVATE);
    }
    if (useRawFrameworkFieldType()) {
      contributionField.addAnnotation(suppressWarnings(RAWTYPES));
    }

    propertySpec = contributionField.build();
    shardImplementation.addField(FRAMEWORK_FIELD, propertySpec);

    return propertySpec;
  }

  private XTypeName frameworkFieldType(FrameworkField frameworkField) {
    return useRawFrameworkFieldType()
        ? frameworkField.type().getRawTypeName()
        : frameworkField.type();
  }

  private boolean useRawFrameworkFieldType() {
    return !shardImplementation.isTypeAccessible(binding.key().type().xprocessing());
  }

  private XClassName delegateType() {
    return isProvider() ? XTypeNames.DELEGATE_FACTORY : XTypeNames.DELEGATE_PRODUCER;
  }

  private boolean isProvider() {
    return binding.bindingType().equals(BindingType.PROVISION)
        && frameworkInstanceCreationExpression
            .alternativeFrameworkClass()
            .map(XTypeNames.JAVAX_PROVIDER::equals)
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

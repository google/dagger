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
import static dagger.internal.codegen.AnnotationSpecs.Suppression.RAWTYPES;
import static dagger.internal.codegen.MemberSelect.staticMemberSelect;
import static dagger.internal.codegen.TypeNames.DELEGATE_FACTORY;
import static dagger.internal.codegen.TypeNames.PRODUCER;
import static javax.lang.model.element.Modifier.PRIVATE;

import com.google.common.collect.ImmutableMap;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import dagger.internal.DelegateFactory;
import java.util.Optional;
import javax.lang.model.util.Elements;

/** The code expressions to declare, initialize, and/or access a binding in a component. */
final class BindingExpression extends RequestFulfillment {
  private final Optional<FieldSpec> fieldSpec;
  private final RequestFulfillment requestFulfillmentDelegate;
  private final HasBindingExpressions hasBindingExpressions;
  private final boolean isProducerFromProvider;
  private InitializationState fieldInitializationState = InitializationState.UNINITIALIZED;

  private BindingExpression(
      RequestFulfillment requestFulfillmentDelegate,
      Optional<FieldSpec> fieldSpec,
      HasBindingExpressions hasBindingExpressions,
      boolean isProducerFromProvider) {
    super(requestFulfillmentDelegate.bindingKey());
    this.requestFulfillmentDelegate = requestFulfillmentDelegate;
    this.fieldSpec = fieldSpec;
    this.hasBindingExpressions = hasBindingExpressions;
    this.isProducerFromProvider = isProducerFromProvider;
  }

  @Override
  CodeBlock getSnippetForDependencyRequest(DependencyRequest request, ClassName requestingClass) {
    // TODO(user): We don't always have to initialize ourselves depending on the request and
    // inlining.
    maybeInitializeField();
    return requestFulfillmentDelegate.getSnippetForDependencyRequest(request, requestingClass);
  }

  @Override
  CodeBlock getSnippetForFrameworkDependency(
      FrameworkDependency frameworkDependency, ClassName requestingClass) {
    maybeInitializeField();
    return requestFulfillmentDelegate.getSnippetForFrameworkDependency(
        frameworkDependency, requestingClass);
  }

  /** Returns {@code true} if this binding expression has a field. */
  boolean hasFieldSpec() {
    return fieldSpec.isPresent();
  }

  /**
   * Returns the name of the binding's underlying field.
   *
   * @throws UnsupportedOperationException if {@link #hasFieldSpec()} is {@code false}
   */
  private String fieldName() {
    checkHasField();
    return fieldSpec.get().name;
  }

  /** Returns true if this binding expression represents a producer from provider. */
  // TODO(user): remove this and represent this via a subtype of BindingExpression
  boolean isProducerFromProvider() {
    return isProducerFromProvider;
  }

  /**
   * Sets the initialization state for the binding's underlying field. Only valid for field types.
   *
   * @throws UnsupportedOperationException if {@link #hasFieldSpec()} is {@code false}
   */
  private void setFieldInitializationState(InitializationState fieldInitializationState) {
    checkHasField();
    checkArgument(this.fieldInitializationState.compareTo(fieldInitializationState) < 0);
    this.fieldInitializationState = fieldInitializationState;
  }

  private void checkHasField() {
    if (!hasFieldSpec()) {
      throw new UnsupportedOperationException();
    }
  }

  // Adds our field and initialization of our field to the component.
  private void maybeInitializeField() {
    if (!hasFieldSpec()) {
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
                checkNotNull(hasBindingExpressions.getFieldInitialization(this)));

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
        hasBindingExpressions.addInitialization(codeBuilder.build());
        hasBindingExpressions.addField(fieldSpec.get());

        setFieldInitializationState(InitializationState.INITIALIZED);
        break;

      case INITIALIZING:
        // We were recursively invoked, so create a delegate factory instead
        hasBindingExpressions.addInitialization(
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
  enum InitializationState {
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

  /** Factory for building a {@link BindingExpression}. */
  static final class Factory {
    private final CompilerOptions compilerOptions;
    private final ClassName componentName;
    private final UniqueNameSet componentFieldNames;
    private final HasBindingExpressions hasBindingExpressions;
    private final ImmutableMap<BindingKey, String> subcomponentNames;
    private final BindingGraph graph;
    private final Elements elements;

    Factory(
        CompilerOptions compilerOptions,
        ClassName componentName,
        UniqueNameSet componentFieldNames,
        HasBindingExpressions hasBindingExpressions,
        ImmutableMap<BindingKey, String> subcomponentNames,
        BindingGraph graph,
        Elements elements) {
      this.compilerOptions = checkNotNull(compilerOptions);
      this.componentName = checkNotNull(componentName);
      this.componentFieldNames = checkNotNull(componentFieldNames);
      this.hasBindingExpressions = checkNotNull(hasBindingExpressions);
      this.subcomponentNames = checkNotNull(subcomponentNames);
      this.graph = checkNotNull(graph);
      this.elements = elements;
    }

    /** Creates a binding expression for a field. */
    BindingExpression forField(ResolvedBindings resolvedBindings) {
      FieldSpec fieldSpec = generateFrameworkField(resolvedBindings, Optional.empty());
      MemberSelect memberSelect = MemberSelect.localField(componentName, fieldSpec.name);
      return new BindingExpression(
          createRequestFulfillment(resolvedBindings, memberSelect),
          Optional.of(fieldSpec),
          hasBindingExpressions,
          false);
    }

    BindingExpression forProducerFromProviderField(ResolvedBindings resolvedBindings) {
      FieldSpec fieldSpec = generateFrameworkField(resolvedBindings, Optional.of(PRODUCER));
      MemberSelect memberSelect = MemberSelect.localField(componentName, fieldSpec.name);
      return new BindingExpression(
          new ProducerFieldRequestFulfillment(resolvedBindings.bindingKey(), memberSelect),
          Optional.of(fieldSpec),
          hasBindingExpressions,
          true);
    }

    /** Creates a binding expression for a static method call. */
    Optional<BindingExpression> forStaticMethod(ResolvedBindings resolvedBindings) {
      Optional<MemberSelect> memberSelect = staticMemberSelect(resolvedBindings);
      return memberSelect.map(
          value ->
              new BindingExpression(
                  createRequestFulfillment(resolvedBindings, value),
                  Optional.empty(),
                  hasBindingExpressions,
                  false));
    }

    /**
     * Adds a field representing the resolved bindings, optionally forcing it to use a particular
     * binding type (instead of the type the resolved bindings would typically use).
     */
    private FieldSpec generateFrameworkField(
        ResolvedBindings resolvedBindings, Optional<ClassName> frameworkClass) {
      boolean useRawType = useRawType(resolvedBindings);

      FrameworkField contributionBindingField =
          FrameworkField.forResolvedBindings(resolvedBindings, frameworkClass);
      FieldSpec.Builder contributionField =
          FieldSpec.builder(
              useRawType
                  ? contributionBindingField.type().rawType
                  : contributionBindingField.type(),
              componentFieldNames.getUniqueName(contributionBindingField.name()));
      contributionField.addModifiers(PRIVATE);
      if (useRawType) {
        contributionField.addAnnotation(AnnotationSpecs.suppressWarnings(RAWTYPES));
      }

      return contributionField.build();
    }

    private boolean useRawType(ResolvedBindings resolvedBindings) {
      Optional<String> bindingPackage = resolvedBindings.bindingPackage();
      return bindingPackage.isPresent()
          && !bindingPackage.get().equals(componentName.packageName());
    }

    private RequestFulfillment createRequestFulfillment(
        ResolvedBindings resolvedBindings, MemberSelect memberSelect) {
      BindingKey bindingKey = resolvedBindings.bindingKey();
      switch (resolvedBindings.bindingType()) {
        case MEMBERS_INJECTION:
          return new MembersInjectorRequestFulfillment(bindingKey, memberSelect);
        case PRODUCTION:
          return new ProducerFieldRequestFulfillment(bindingKey, memberSelect);
        case PROVISION:
          ProvisionBinding provisionBinding =
              (ProvisionBinding) resolvedBindings.contributionBinding();

          ProviderFieldRequestFulfillment providerFieldRequestFulfillment =
              new ProviderFieldRequestFulfillment(bindingKey, memberSelect);

          switch (provisionBinding.bindingKind()) {
            case SUBCOMPONENT_BUILDER:
              return new SubcomponentBuilderRequestFulfillment(
                  bindingKey, providerFieldRequestFulfillment, subcomponentNames.get(bindingKey));
            case SYNTHETIC_MULTIBOUND_SET:
              return new SetBindingRequestFulfillment(
                  bindingKey,
                  provisionBinding,
                  graph,
                  hasBindingExpressions,
                  providerFieldRequestFulfillment,
                  elements);
            case SYNTHETIC_OPTIONAL_BINDING:
              return new OptionalBindingRequestFulfillment(
                  bindingKey,
                  provisionBinding,
                  providerFieldRequestFulfillment,
                  hasBindingExpressions);
            case INJECTION:
            case PROVISION:
              if (provisionBinding.implicitDependencies().isEmpty()
                  && !provisionBinding.scope().isPresent()
                  && !provisionBinding.requiresModuleInstance()
                  && provisionBinding.bindingElement().isPresent()) {
                return new SimpleMethodRequestFulfillment(
                    compilerOptions,
                    bindingKey,
                    provisionBinding,
                    providerFieldRequestFulfillment,
                    hasBindingExpressions);
              }
              // fall through
            default:
              return providerFieldRequestFulfillment;
          }
        default:
          throw new AssertionError();
      }
    }
  }
}

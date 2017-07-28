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
import static dagger.internal.codegen.MemberSelect.staticMemberSelect;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import dagger.internal.DelegateFactory;
import java.util.Optional;
import java.util.function.BiConsumer;
import javax.lang.model.util.Elements;

/** The code expressions to declare, initialize, and/or access a binding in a component. */
final class BindingExpression extends RequestFulfillment {
  private final Optional<FieldSpec> fieldSpec;
  private final RequestFulfillment requestFulfillmentDelegate;
  private CodeBlock initializeDeferredBindingFields;
  private CodeBlock initializeField;
  private InitializationState fieldInitializationState = InitializationState.UNINITIALIZED;

  private BindingExpression(
      RequestFulfillment requestFulfillmentDelegate, Optional<FieldSpec> fieldSpec) {
    super(requestFulfillmentDelegate.bindingKey());
    this.requestFulfillmentDelegate = requestFulfillmentDelegate;
    this.fieldSpec = fieldSpec;
  }

  @Override
  CodeBlock getSnippetForDependencyRequest(DependencyRequest request, ClassName requestingClass) {
    return requestFulfillmentDelegate.getSnippetForDependencyRequest(request, requestingClass);
  }

  @Override
  CodeBlock getSnippetForFrameworkDependency(
      FrameworkDependency frameworkDependency, ClassName requestingClass) {
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
  String fieldName() {
    checkHasField();
    return fieldSpec.get().name;
  }

  /**
   * Sets the code for initializing the binding's underlying field.
   *
   * @throws UnsupportedOperationException if {@link #hasFieldSpec()} is {@code false}
   */
  void setInitializationCode(CodeBlock initializeDeferredBindingFields, CodeBlock initializeField) {
    checkHasField();
    this.initializeDeferredBindingFields = checkNotNull(initializeDeferredBindingFields);
    this.initializeField = checkNotNull(initializeField);
  }

  /**
   * Returns the initialization code for the binding's underlying field.
   *
   * @throws UnsupportedOperationException if {@link #hasFieldSpec()} is {@code false}
   */
  private CodeBlock getInitializationCode() {
    checkHasField();
    checkState(initializeDeferredBindingFields != null && initializeField != null);
    return CodeBlocks.concat(ImmutableList.of(initializeDeferredBindingFields, initializeField));
  }

  /**
   * Returns the initialization state for the binding's underlying field.
   *
   * @throws UnsupportedOperationException if {@link #hasFieldSpec()} is {@code false}
   */
  InitializationState fieldInitializationState() {
    checkHasField();
    return fieldInitializationState;
  }

  /**
   * Sets the initialization state for the binding's underlying field. Only valid for field types.
   *
   * @throws UnsupportedOperationException if {@link #hasFieldSpec()} is {@code false}
   */
  void setFieldInitializationState(InitializationState fieldInitializationState) {
    checkHasField();
    checkArgument(this.fieldInitializationState.compareTo(fieldInitializationState) < 0);
    this.fieldInitializationState = fieldInitializationState;
  }

  /** Calls the consumer to initialize the binding's underlying field if it has one. */
  void initializeField(BiConsumer<FieldSpec, CodeBlock> initializationConsumer) {
    if (hasFieldSpec()) {
      initializationConsumer.accept(fieldSpec.get(), getInitializationCode());
    }
  }

  private void checkHasField() {
    if (!hasFieldSpec()) {
      throw new UnsupportedOperationException();
    }
  }

  /** Initialization state for a factory field. */
  enum InitializationState {
    /** The field is {@code null}. */
    UNINITIALIZED,

    /** The field is set to a {@link DelegateFactory}. */
    DELEGATED,

    /** The field is set to an undelegated factory. */
    INITIALIZED;
  }

  /** Factory for building a {@link BindingExpression}. */
  static final class Factory {
    private final CompilerOptions compilerOptions;
    private final ClassName componentName;
    private final HasBindingExpressions hasBindingExpressions;
    private final ImmutableMap<BindingKey, String> subcomponentNames;
    private final BindingGraph graph;
    private final Elements elements;

    Factory(
        CompilerOptions compilerOptions,
        ClassName componentName,
        HasBindingExpressions hasBindingExpressions,
        ImmutableMap<BindingKey, String> subcomponentNames,
        BindingGraph graph,
        Elements elements) {
      this.compilerOptions = checkNotNull(compilerOptions);
      this.componentName = checkNotNull(componentName);
      this.hasBindingExpressions = checkNotNull(hasBindingExpressions);
      this.subcomponentNames = checkNotNull(subcomponentNames);
      this.graph = checkNotNull(graph);
      this.elements = elements;
    }

    /** Creates a binding expression for a field. */
    BindingExpression forField(ResolvedBindings resolvedBindings, FieldSpec fieldSpec) {
      MemberSelect memberSelect = MemberSelect.localField(componentName, fieldSpec.name);
      return new BindingExpression(
          createRequestFulfillment(resolvedBindings, memberSelect),
          Optional.of(fieldSpec));
    }

    /** Creates a binding expression for a static method call. */
    Optional<BindingExpression> forStaticMethod(ResolvedBindings resolvedBindings) {
      Optional<MemberSelect> memberSelect = staticMemberSelect(resolvedBindings);
      return memberSelect.map(
          value ->
              new BindingExpression(
                  createRequestFulfillment(resolvedBindings, value),
                  Optional.empty()));
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

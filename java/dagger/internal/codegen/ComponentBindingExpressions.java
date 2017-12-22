/*
 * Copyright (C) 2016 The Dagger Authors.
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
import static dagger.internal.codegen.Accessibility.isRawTypeAccessible;
import static dagger.internal.codegen.Accessibility.isTypeAccessibleFrom;
import static dagger.internal.codegen.ContributionBinding.Kind.INJECTION;
import static dagger.internal.codegen.ContributionBinding.Kind.PROVISION;
import static dagger.internal.codegen.ContributionBinding.Kind.SYNTHETIC_MULTIBOUND_MAP;
import static dagger.internal.codegen.ContributionBinding.Kind.SYNTHETIC_MULTIBOUND_SET;
import static dagger.internal.codegen.MemberSelect.staticMemberSelect;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Table;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.model.Key;
import dagger.model.RequestKind;
import java.util.EnumSet;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

/** A central repository of code expressions used to access any binding available to a component. */
final class ComponentBindingExpressions {

  // TODO(dpb,ronshapiro): refactor this and ComponentRequirementFields into a
  // HierarchicalComponentMap<K, V>, or perhaps this use a flattened ImmutableMap, built from its
  // parents? If so, maybe make BindingExpression.Factory create it.

  private final Optional<ComponentBindingExpressions> parent;
  private final BindingGraph graph;
  private final DaggerTypes types;
  private final BindingExpressionFactory bindingExpressionFactory;
  private final Table<Key, RequestKind, BindingExpression> expressions = HashBasedTable.create();

  ComponentBindingExpressions(
      BindingGraph graph,
      GeneratedComponentModel generatedComponentModel,
      SubcomponentNames subcomponentNames,
      ComponentRequirementFields componentRequirementFields,
      OptionalFactories optionalFactories,
      DaggerTypes types,
      Elements elements,
      CompilerOptions compilerOptions) {
    this(
        Optional.empty(),
        graph,
        generatedComponentModel,
        subcomponentNames,
        componentRequirementFields,
        new ReferenceReleasingManagerFields(graph, generatedComponentModel),
        optionalFactories,
        types,
        elements,
        compilerOptions);
  }

  private ComponentBindingExpressions(
      Optional<ComponentBindingExpressions> parent,
      BindingGraph graph,
      GeneratedComponentModel generatedComponentModel,
      SubcomponentNames subcomponentNames,
      ComponentRequirementFields componentRequirementFields,
      ReferenceReleasingManagerFields referenceReleasingManagerFields,
      OptionalFactories optionalFactories,
      DaggerTypes types,
      Elements elements,
      CompilerOptions compilerOptions) {
    this.parent = parent;
    this.graph = graph;
    this.types = types;
    this.bindingExpressionFactory =
        new BindingExpressionFactory(
            graph,
            generatedComponentModel,
            subcomponentNames,
            this,
            componentRequirementFields,
            referenceReleasingManagerFields,
            optionalFactories,
            types,
            elements,
            compilerOptions);
  }

  /**
   * Returns a new object representing the bindings available from a child component of this one.
   */
  ComponentBindingExpressions forChildComponent(
      BindingGraph childGraph,
      GeneratedComponentModel childComponentModel,
      ComponentRequirementFields childComponentRequirementFields) {
    return new ComponentBindingExpressions(
        Optional.of(this),
        childGraph,
        childComponentModel,
        bindingExpressionFactory.subcomponentNames,
        childComponentRequirementFields,
        bindingExpressionFactory.referenceReleasingManagerFields,
        bindingExpressionFactory.optionalFactories,
        bindingExpressionFactory.types,
        bindingExpressionFactory.elements,
        bindingExpressionFactory.compilerOptions);
  }

  /**
   * Returns an expression that evaluates to the value of a dependency request for a binding owned
   * by this component or an ancestor.
   *
   * @param requestingClass the class that will contain the expression
   * @throws IllegalStateException if there is no binding expression that satisfies the dependency
   *     request
   */
  Expression getDependencyExpression(Key key, RequestKind requestKind, ClassName requestingClass) {
    return getBindingExpression(key, requestKind).getDependencyExpression(requestingClass);
  }

  /**
   * Returns an expression that evaluates to the value of a dependency request for a binding owned
   * by this component or an ancestor.
   *
   * @param requestingClass the class that will contain the expression
   * @throws IllegalStateException if there is no binding expression that satisfies the dependency
   *     request
   */
  Expression getDependencyExpression(DependencyRequest request, ClassName requestingClass) {
    return getDependencyExpression(request.key(), request.kind(), requestingClass);
  }

  /**
   * Returns an expression that evaluates to the value of a framework dependency for a binding owned
   * in this component or an ancestor.
   *
   * @param requestingClass the class that will contain the expression
   * @throws IllegalStateException if there is no binding expression that satisfies the dependency
   *     request
   */
  Expression getDependencyExpression(
      FrameworkDependency frameworkDependency, ClassName requestingClass) {
    return getDependencyExpression(
        frameworkDependency.key(), frameworkDependency.dependencyRequestKind(), requestingClass);
  }

  /**
   * Returns an expression that evaluates to the value of a dependency request, for passing to a
   * binding method, an {@code @Inject}-annotated constructor or member, or a proxy for one.
   *
   * <p>If the method is a generated static {@link InjectionMethods injection method}, each
   * parameter will be {@link Object} if the dependency's raw type is inaccessible. If that is the
   * case for this dependency, the returned expression will use a cast to evaluate to the raw type.
   *
   * @param requestingClass the class that will contain the expression
   */
  // TODO(b/64024402) Merge with getDependencyExpression(DependencyRequest, ClassName) if possible.
  Expression getDependencyArgumentExpression(
      DependencyRequest dependencyRequest, ClassName requestingClass) {

    TypeMirror dependencyType = dependencyRequest.key().type();
    Expression dependencyExpression = getDependencyExpression(dependencyRequest, requestingClass);

    if (!isTypeAccessibleFrom(dependencyType, requestingClass.packageName())
        && isRawTypeAccessible(dependencyType, requestingClass.packageName())) {
      return dependencyExpression.castTo(types.erasure(dependencyType));
    }

    return dependencyExpression;
  }

  /**
   * Returns an expression for the implementation of a component method with the given request.
   *
   * @throws IllegalStateException if there is no binding expression that satisfies the dependency
   *     request
   */
  CodeBlock getComponentMethodImplementation(
      ComponentMethodDescriptor componentMethod, ClassName requestingClass) {
    return getBindingExpression(
            componentMethod.dependencyRequest().get().key(),
            componentMethod.dependencyRequest().get().kind())
        .getComponentMethodImplementation(componentMethod, requestingClass);
  }

  private BindingExpression getBindingExpression(Key key, RequestKind requestKind) {
    ResolvedBindings resolvedBindings = graph.resolvedBindings(requestKind, key);
    if (resolvedBindings != null && !resolvedBindings.ownedBindings().isEmpty()) {
      if (!expressions.contains(key, requestKind)) {
        expressions.put(
            key, requestKind, bindingExpressionFactory.create(resolvedBindings, requestKind));
      }
      return expressions.get(key, requestKind);
    }
    return parent
        .map(p -> p.getBindingExpression(key, requestKind))
        .orElseThrow(
            () ->
                new IllegalStateException(
                    String.format("no expression found for %s-%s", key, requestKind)));
  }

  /** Factory for building a {@link BindingExpression}. */
  private static final class BindingExpressionFactory {
    // TODO(user): Consider using PrivateMethodBindingExpression for other/all BEs?
    private static final ImmutableSet<ContributionBinding.Kind> PRIVATE_METHOD_KINDS =
        ImmutableSet.copyOf(
            EnumSet.of(SYNTHETIC_MULTIBOUND_SET, SYNTHETIC_MULTIBOUND_MAP, INJECTION, PROVISION));

    private final BindingGraph graph;
    private final GeneratedComponentModel generatedComponentModel;
    private final ComponentBindingExpressions componentBindingExpressions;
    private final ComponentRequirementFields componentRequirementFields;
    private final ReferenceReleasingManagerFields referenceReleasingManagerFields;
    private final SubcomponentNames subcomponentNames;
    private final OptionalFactories optionalFactories;
    private final CompilerOptions compilerOptions;
    private final DaggerTypes types;
    private final Elements elements;
    private final MembersInjectionMethods membersInjectionMethods;

    BindingExpressionFactory(
        BindingGraph graph,
        GeneratedComponentModel generatedComponentModel,
        SubcomponentNames subcomponentNames,
        ComponentBindingExpressions componentBindingExpressions,
        ComponentRequirementFields componentRequirementFields,
        ReferenceReleasingManagerFields referenceReleasingManagerFields,
        OptionalFactories optionalFactories,
        DaggerTypes types,
        Elements elements,
        CompilerOptions compilerOptions) {
      this.graph = graph;
      this.generatedComponentModel = checkNotNull(generatedComponentModel);
      this.subcomponentNames = checkNotNull(subcomponentNames);
      this.componentBindingExpressions = componentBindingExpressions;
      this.componentRequirementFields = checkNotNull(componentRequirementFields);
      this.referenceReleasingManagerFields = checkNotNull(referenceReleasingManagerFields);
      this.optionalFactories = checkNotNull(optionalFactories);
      this.types = types;
      this.elements = checkNotNull(elements);
      this.compilerOptions = checkNotNull(compilerOptions);
      this.membersInjectionMethods =
          new MembersInjectionMethods(
              generatedComponentModel, componentBindingExpressions, graph, elements, types);
    }

    /** Creates a binding expression. */
    BindingExpression create(ResolvedBindings resolvedBindings, RequestKind requestKind) {
      switch (resolvedBindings.bindingType()) {
        case MEMBERS_INJECTION:
          return membersInjectionBindingExpression(resolvedBindings);

        case PROVISION:
          return provisionBindingExpression(resolvedBindings, requestKind);

        case PRODUCTION:
          return frameworkInstanceBindingExpression(resolvedBindings, requestKind);

        default:
          throw new AssertionError(resolvedBindings);
      }
    }

    /** Returns a binding expression for a members injection binding. */
    private MembersInjectionBindingExpression membersInjectionBindingExpression(
        ResolvedBindings resolvedBindings) {
      return new MembersInjectionBindingExpression(resolvedBindings, membersInjectionMethods);
    }

    /**
     * Returns a binding expression that uses a {@link javax.inject.Provider} for provision
     * bindings, a {@link dagger.producers.Producer} for production bindings, or a {@link
     * dagger.MembersInjector} for members injection bindings.
     */
    private FrameworkInstanceBindingExpression frameworkInstanceBindingExpression(
        ResolvedBindings resolvedBindings, RequestKind requestKind) {
      Optional<MemberSelect> staticMethod = staticMemberSelect(resolvedBindings);
      return new FrameworkInstanceBindingExpression(
          resolvedBindings,
          requestKind,
          componentBindingExpressions,
          resolvedBindings.bindingType().frameworkType(),
          staticMethod.isPresent()
              ? staticMethod::get
              : frameworkFieldInitializer(resolvedBindings),
          types,
          elements);
    }

    /**
     * Returns an initializer for a {@link javax.inject.Provider} field for provision bindings, a
     * {@link dagger.producers.Producer} field for production bindings, or a {@link
     * dagger.MembersInjector} field for members injection bindings.
     */
    private FrameworkFieldInitializer frameworkFieldInitializer(ResolvedBindings resolvedBindings) {
      switch (resolvedBindings.bindingType()) {
        case PRODUCTION:
        case PROVISION:
          return new ProviderOrProducerFieldInitializer(
              resolvedBindings,
              subcomponentNames,
              generatedComponentModel,
              componentBindingExpressions,
              componentRequirementFields,
              referenceReleasingManagerFields,
              compilerOptions,
              graph,
              optionalFactories);
        default:
          throw new AssertionError(resolvedBindings);
      }
    }

    /** Returns a binding expression for a provision binding. */
    private BindingExpression provisionBindingExpression(
        ResolvedBindings resolvedBindings, RequestKind requestKind) {
      FrameworkInstanceBindingExpression frameworkInstanceBindingExpression =
          requestKind.equals(RequestKind.PRODUCER)
              ? producerFromProviderInstanceBindingExpression(resolvedBindings, requestKind)
              : frameworkInstanceBindingExpression(resolvedBindings, requestKind);

      BindingExpression inlineBindingExpression =
          inlineProvisionBindingExpression(frameworkInstanceBindingExpression);

      if (usePrivateMethod(resolvedBindings.contributionBinding())) {
        return new PrivateMethodBindingExpression(
            generatedComponentModel,
            componentBindingExpressions,
            inlineBindingExpression,
            referenceReleasingManagerFields,
            compilerOptions,
            types,
            elements);
      }

      return inlineBindingExpression;
    }

    /**
     * Returns a binding expression that uses a {@link dagger.producers.Producer} field for a
     * provision binding.
     */
    private FrameworkInstanceBindingExpression producerFromProviderInstanceBindingExpression(
        ResolvedBindings resolvedBindings, RequestKind requestKind) {
      checkArgument(resolvedBindings.bindingType().frameworkType().equals(FrameworkType.PROVIDER));
      return new FrameworkInstanceBindingExpression(
          resolvedBindings,
          requestKind,
          componentBindingExpressions,
          FrameworkType.PRODUCER,
          new ProducerFromProviderFieldInitializer(
              resolvedBindings, generatedComponentModel, componentBindingExpressions),
          types,
          elements);
    }

    private BindingExpression inlineProvisionBindingExpression(
        BindingExpression bindingExpression) {
      ProvisionBinding provisionBinding =
          (ProvisionBinding) bindingExpression.resolvedBindings().contributionBinding();
      switch (provisionBinding.bindingKind()) {
        case COMPONENT:
          return new ComponentInstanceBindingExpression(
              bindingExpression, provisionBinding, generatedComponentModel.name(), types);

        case COMPONENT_DEPENDENCY:
          return new ComponentRequirementBindingExpression(
              bindingExpression,
              ComponentRequirement.forDependency(provisionBinding.key().type()),
              componentRequirementFields,
              types);

        case COMPONENT_PROVISION:
          return new ComponentProvisionBindingExpression(
              bindingExpression,
              provisionBinding,
              graph,
              componentRequirementFields,
              compilerOptions,
              types);

        case SUBCOMPONENT_BUILDER:
          return new SubcomponentBuilderBindingExpression(
              bindingExpression,
              provisionBinding,
              subcomponentNames.get(bindingExpression.key()),
              types);

        case SYNTHETIC_MULTIBOUND_SET:
          return new SetBindingExpression(
              provisionBinding,
              graph,
              componentBindingExpressions,
              bindingExpression,
              types,
              elements);

        case SYNTHETIC_MULTIBOUND_MAP:
          return new MapBindingExpression(
              provisionBinding,
              graph,
              componentBindingExpressions,
              bindingExpression,
              types,
              elements);

        case SYNTHETIC_OPTIONAL_BINDING:
          return new OptionalBindingExpression(
              provisionBinding, bindingExpression, componentBindingExpressions, types);

        case SYNTHETIC_DELEGATE_BINDING:
          return DelegateBindingExpression.create(
              graph, bindingExpression, componentBindingExpressions, types, elements);

        case BOUND_INSTANCE:
          return new ComponentRequirementBindingExpression(
              bindingExpression,
              ComponentRequirement.forBoundInstance(provisionBinding),
              componentRequirementFields,
              types);

        case INJECTION:
        case PROVISION:
          if (canUseSimpleMethod(provisionBinding)) {
            return new SimpleMethodBindingExpression(
                compilerOptions,
                provisionBinding,
                bindingExpression,
                componentBindingExpressions,
                membersInjectionMethods,
                componentRequirementFields,
                types,
                elements);
          }
          // fall through

        default:
          return bindingExpression;
      }
    }

    private boolean usePrivateMethod(ContributionBinding binding) {
      return (!binding.scope().isPresent() || compilerOptions.experimentalAndroidMode())
          && PRIVATE_METHOD_KINDS.contains(binding.bindingKind());
    }

    private boolean canUseSimpleMethod(ContributionBinding binding) {
      // Use the inlined form when in experimentalAndroidMode, as PrivateMethodBindingExpression
      // implements scoping directly
      // TODO(user): Also inline releasable references in experimentalAndroidMode
      return !binding.scope().isPresent()
          || (compilerOptions.experimentalAndroidMode()
              && !referenceReleasingManagerFields.requiresReleasableReferences(
                  binding.scope().get()));
    }
  }
}

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
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.Accessibility.isRawTypeAccessible;
import static dagger.internal.codegen.Accessibility.isTypeAccessibleFrom;
import static dagger.internal.codegen.BindingType.MEMBERS_INJECTION;
import static dagger.internal.codegen.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.ContributionBinding.FactoryCreationStrategy.SINGLETON_INSTANCE;
import static dagger.internal.codegen.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.MemberSelect.staticMemberSelect;
import static dagger.internal.codegen.SourceFiles.membersInjectorNameForType;
import static dagger.internal.codegen.TypeNames.DOUBLE_CHECK;
import static dagger.internal.codegen.TypeNames.REFERENCE_RELEASING_PROVIDER;
import static dagger.internal.codegen.TypeNames.SINGLE_CHECK;
import static dagger.model.BindingKind.COMPONENT;
import static dagger.model.BindingKind.COMPONENT_DEPENDENCY;
import static dagger.model.BindingKind.INJECTION;
import static dagger.model.BindingKind.MULTIBOUND_MAP;
import static dagger.model.BindingKind.MULTIBOUND_SET;
import static dagger.model.BindingKind.PROVISION;

import com.google.auto.common.MoreTypes;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Table;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import dagger.internal.InstanceFactory;
import dagger.internal.MembersInjectors;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;
import dagger.model.BindingKind;
import dagger.model.DependencyRequest;
import dagger.model.Key;
import dagger.model.RequestKind;
import dagger.model.Scope;
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
  private final GeneratedComponentModel generatedComponentModel;
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
    this.generatedComponentModel = generatedComponentModel;
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
   * Returns the expressions for each of the given {@linkplain FrameworkDependency framework
   * dependencies}.
   */
  ImmutableList<CodeBlock> getDependencyExpressions(
      ImmutableList<FrameworkDependency> frameworkDependencies, ClassName requestingClass) {
    return frameworkDependencies
        .stream()
        .map(dependency -> getDependencyExpression(dependency, requestingClass).codeBlock())
        .collect(toImmutableList());
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

  /** Returns the implementation of a component method. */
  MethodSpec getComponentMethod(ComponentMethodDescriptor componentMethod) {
    checkArgument(componentMethod.dependencyRequest().isPresent());
    DependencyRequest dependencyRequest = componentMethod.dependencyRequest().get();
    return MethodSpec.overriding(
            componentMethod.methodElement(),
            MoreTypes.asDeclared(graph.componentType().asType()),
            types)
        .addCode(
            getBindingExpression(dependencyRequest.key(), dependencyRequest.kind())
                .getComponentMethodImplementation(componentMethod, generatedComponentModel.name()))
        .build();
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
    private static final ImmutableSet<BindingKind> PRIVATE_METHOD_KINDS =
        ImmutableSet.copyOf(
            EnumSet.of(MULTIBOUND_SET, MULTIBOUND_MAP, INJECTION, PROVISION));

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
     * Returns a binding expression that uses a {@link javax.inject.Provider} for provision bindings
     * or a {@link dagger.producers.Producer} for production bindings.
     */
    private FrameworkInstanceBindingExpression frameworkInstanceBindingExpression(
        ResolvedBindings resolvedBindings, RequestKind requestKind) {
      Optional<MemberSelect> staticMethod = staticMemberSelect(resolvedBindings);
      FrameworkInstanceCreationExpression frameworkInstanceCreationExpression =
          resolvedBindings.scope().isPresent()
              ? scope(
                  resolvedBindings.scope().get(),
                  frameworkInstanceCreationExpression(resolvedBindings))
              : frameworkInstanceCreationExpression(resolvedBindings);
      return new FrameworkInstanceBindingExpression(
          resolvedBindings,
          requestKind,
          componentBindingExpressions,
          resolvedBindings.bindingType().frameworkType(),
          staticMethod.isPresent()
              ? staticMethod::get
              : new FrameworkFieldInitializer(
                  generatedComponentModel, resolvedBindings, frameworkInstanceCreationExpression),
          types,
          elements);
    }

    private FrameworkInstanceCreationExpression scope(
        Scope scope, FrameworkInstanceCreationExpression unscoped) {
      if (referenceReleasingManagerFields.requiresReleasableReferences(scope)) {
        return () ->
            CodeBlock.of(
                "$T.create($L, $L)",
                REFERENCE_RELEASING_PROVIDER,
                unscoped.creationExpression(),
                referenceReleasingManagerFields.getExpression(
                    scope, generatedComponentModel.name()));
      } else {
        return () ->
            CodeBlock.of(
                "$T.provider($L)",
                scope.isReusable() ? SINGLE_CHECK : DOUBLE_CHECK,
                unscoped.creationExpression());
      }
    }

    /**
     * Returns a creation expression for a {@link javax.inject.Provider} for provision bindings or a
     * {@link dagger.producers.Producer} for production bindings.
     */
    private FrameworkInstanceCreationExpression frameworkInstanceCreationExpression(
        ResolvedBindings resolvedBindings) {
      checkArgument(!resolvedBindings.bindingType().equals(MEMBERS_INJECTION));
      ContributionBinding binding = resolvedBindings.contributionBinding();
      switch (binding.kind()) {
        case COMPONENT:
          // The type parameter can be removed when we drop java 7 source support
          return () ->
              CodeBlock.of("$T.<$T>create(this)", InstanceFactory.class, binding.key().type());

        case BOUND_INSTANCE:
        case COMPONENT_DEPENDENCY:
          return new ComponentRequirementProviderCreationExpression(
              binding, generatedComponentModel, componentRequirementFields);

        case COMPONENT_PROVISION:
          return new DependencyMethodProviderCreationExpression(
              binding, generatedComponentModel, componentRequirementFields, compilerOptions, graph);

        case SUBCOMPONENT_BUILDER:
          return new SubcomponentBuilderProviderCreationExpression(
              binding.key().type(), subcomponentNames.get(binding.key()));

        case INJECTION:
        case PROVISION:
          return new InjectionOrProvisionProviderCreationExpression(
              binding,
              generatedComponentModel,
              componentBindingExpressions,
              componentRequirementFields);

        case COMPONENT_PRODUCTION:
          return new DependencyMethodProducerCreationExpression(
              binding, generatedComponentModel, componentRequirementFields, graph);

        case PRODUCTION:
          return new ProducerCreationExpression(
              binding,
              generatedComponentModel,
              componentBindingExpressions,
              componentRequirementFields);

        case MULTIBOUND_SET:
          return new SetFactoryCreationExpression(
              binding, generatedComponentModel, componentBindingExpressions, graph);

        case MULTIBOUND_MAP:
          return new MapFactoryCreationExpression(
              binding, generatedComponentModel, componentBindingExpressions, graph);

        case RELEASABLE_REFERENCE_MANAGER:
          return new ReleasableReferenceManagerProviderCreationExpression(
              binding, generatedComponentModel, referenceReleasingManagerFields);

        case RELEASABLE_REFERENCE_MANAGERS:
          return new ReleasableReferenceManagerSetProviderCreationExpression(
              binding, generatedComponentModel, referenceReleasingManagerFields, graph);

        case DELEGATE:
          return new DelegatingFrameworkInstanceCreationExpression(
              binding, generatedComponentModel, componentBindingExpressions);

        case OPTIONAL:
          if (binding.explicitDependencies().isEmpty()) {
            return () -> optionalFactories.absentOptionalProvider(binding);
          } else {
            return () ->
                optionalFactories.presentOptionalFactory(
                    binding,
                    componentBindingExpressions
                        .getDependencyExpression(
                            getOnlyElement(binding.frameworkDependencies()),
                            generatedComponentModel.name())
                        .codeBlock());
          }

        case MEMBERS_INJECTOR:
          TypeMirror membersInjectedType =
              getOnlyElement(MoreTypes.asDeclared(binding.key().type()).getTypeArguments());

          if (((ProvisionBinding) binding).injectionSites().isEmpty()) {
            return () ->
                // The type parameter can be removed when we drop Java 7 source support.
                CodeBlock.of(
                    "$T.create($T.<$T>noOp())",
                    InstanceFactory.class,
                    MembersInjectors.class,
                    membersInjectedType);
          } else {
            return () ->
                CodeBlock.of(
                    "$T.create($T.create($L))",
                    InstanceFactory.class,
                    membersInjectorNameForType(MoreTypes.asTypeElement(membersInjectedType)),
                    makeParametersCodeBlock(
                        componentBindingExpressions.getDependencyExpressions(
                            binding.frameworkDependencies(), generatedComponentModel.name())));
          }

        default:
          throw new AssertionError(binding);
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

      Optional<ComponentMethodDescriptor> componentMethod =
          findMatchingComponentMethod(resolvedBindings.key(), requestKind);
      BindingKind bindingKind = resolvedBindings.contributionBinding().kind();
      if (componentMethod.isPresent()
          // Requests for a component or a component field should access the component or field
          // directly, even if a component method exists.
          && !bindingKind.equals(COMPONENT)
          && !bindingKind.equals(COMPONENT_DEPENDENCY)) {
        return new ComponentMethodBindingExpression(
            resolvedBindings,
            requestKind,
            methodImplementation(inlineBindingExpression),
            generatedComponentModel.name(),
            componentMethod.get(),
            componentBindingExpressions);
      } else if (shouldUsePrivateMethod(resolvedBindings.contributionBinding(), requestKind)) {
        return new PrivateMethodBindingExpression(
            resolvedBindings,
            requestKind,
            methodImplementation(inlineBindingExpression),
            generatedComponentModel);
      }

      return inlineBindingExpression;
    }

    private BindingMethodImplementation methodImplementation(BindingExpression bindingExpression) {
      return compilerOptions.experimentalAndroidMode()
          ? new AndroidModeBindingMethodImplementation(
              bindingExpression,
              types,
              elements,
              generatedComponentModel,
              componentBindingExpressions,
              referenceReleasingManagerFields)
          : new BindingMethodImplementation(
              bindingExpression, generatedComponentModel.name(), types, elements);
    }

    /**
     * Returns true if requesters should call a no-arg, private method.
     *
     * <p>In default mode, private methods are used for unscoped {@code INSTANCE} and {@code FUTURE}
     * requests that require at least one dependency. (Those with no dependencies can simply use
     * their factory class's single instance.)
     *
     * <p>In Android mode, private methods are used for all provision bindings unless the request:
     *
     * <ul>
     *   <li>has releasable reference scope; TODO(user): enable for releasable reference scope
     *   <li>is for an unscoped framework type (Provider, Lazy, ProviderOfLazy) that can use the
     *       singleton instance of the factory class.
     *   <li>is for an unscoped non-framework type that has no dependencies, which means users can
     *       call a nullary method anyway.
     * </ul>
     */
    private boolean shouldUsePrivateMethod(ContributionBinding binding, RequestKind requestKind) {
      // TODO(user): enable for releasable references.
      Optional<Scope> releasableReferenceScope =
          binding.scope().filter(referenceReleasingManagerFields::requiresReleasableReferences);
      if (!PRIVATE_METHOD_KINDS.contains(binding.kind()) || releasableReferenceScope.isPresent()) {
        return false;
      }
      if (compilerOptions.experimentalAndroidMode()) {
        switch (requestKind) {
          case PROVIDER:
          case LAZY:
          case PROVIDER_OF_LAZY:
            return binding.scope().isPresent()
                || !binding.factoryCreationStrategy().equals(SINGLETON_INSTANCE);
          default:
            return binding.scope().isPresent() || !binding.dependencies().isEmpty();
        }
      } else {
        return (requestKind.equals(RequestKind.INSTANCE) || requestKind.equals(RequestKind.FUTURE))
            && !binding.scope().isPresent()
            && !binding.dependencies().isEmpty();
      }
    }

    /** Returns the first component method associated with this request kind, if one exists. */
    private Optional<ComponentMethodDescriptor> findMatchingComponentMethod(
        Key key, RequestKind requestKind) {
      Optional<ComponentMethodDescriptor> componentMethod =
          graph
              .componentDescriptor()
              .componentMethods()
              .stream()
              .filter(method -> doesComponentMethodMatch(method, key, requestKind))
              .findFirst();
      return componentMethod;
    }

    /** Returns true if the component method matches the dependency request binding key and kind. */
    private boolean doesComponentMethodMatch(
        ComponentMethodDescriptor componentMethod, Key key, RequestKind requestKind) {
      return componentMethod
          .dependencyRequest()
          .filter(request -> request.key().equals(key))
          .filter(request -> request.kind().equals(requestKind))
          .isPresent();
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
          new FrameworkFieldInitializer(
              generatedComponentModel,
              resolvedBindings,
              new ProducerFromProviderCreationExpression(
                  resolvedBindings.contributionBinding(),
                  generatedComponentModel,
                  componentBindingExpressions)),
          types,
          elements);
    }

    private BindingExpression inlineProvisionBindingExpression(
        BindingExpression bindingExpression) {
      ProvisionBinding provisionBinding =
          (ProvisionBinding) bindingExpression.resolvedBindings().contributionBinding();
      switch (provisionBinding.kind()) {
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

        case MULTIBOUND_SET:
          return new SetBindingExpression(
              provisionBinding,
              graph,
              componentBindingExpressions,
              bindingExpression,
              types,
              elements);

        case MULTIBOUND_MAP:
          return new MapBindingExpression(
              provisionBinding,
              graph,
              componentBindingExpressions,
              bindingExpression,
              types,
              elements);

        case OPTIONAL:
          return new OptionalBindingExpression(
              provisionBinding, bindingExpression, componentBindingExpressions, types);

        case DELEGATE:
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

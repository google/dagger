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
import static com.google.common.base.Verify.verify;
import static dagger.internal.codegen.BindingRequest.bindingRequest;
import static dagger.internal.codegen.BindingType.MEMBERS_INJECTION;
import static dagger.internal.codegen.DelegateBindingExpression.isBindsScopeStrongerThanDependencyScope;
import static dagger.internal.codegen.MemberSelect.staticFactoryCreation;
import static dagger.internal.codegen.RequestKinds.isDerivedFromProvider;
import static dagger.internal.codegen.javapoet.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.javapoet.TypeNames.DOUBLE_CHECK;
import static dagger.internal.codegen.javapoet.TypeNames.SINGLE_CHECK;
import static dagger.internal.codegen.langmodel.Accessibility.isRawTypeAccessible;
import static dagger.internal.codegen.langmodel.Accessibility.isRawTypePubliclyAccessible;
import static dagger.internal.codegen.langmodel.Accessibility.isTypeAccessibleFrom;
import static dagger.model.BindingKind.DELEGATE;
import static dagger.model.BindingKind.MULTIBOUND_MAP;
import static dagger.model.BindingKind.MULTIBOUND_SET;
import static javax.lang.model.element.Modifier.ABSTRACT;

import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;
import dagger.internal.codegen.MethodBindingExpression.MethodImplementationStrategy;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.DependencyRequest;
import dagger.model.RequestKind;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.lang.model.SourceVersion;
import javax.lang.model.type.TypeMirror;

/** A central repository of code expressions used to access any binding available to a component. */
@PerComponentImplementation
final class ComponentBindingExpressions {
  // TODO(dpb,ronshapiro): refactor this and ComponentRequirementExpressions into a
  // HierarchicalComponentMap<K, V>, or perhaps this use a flattened ImmutableMap, built from its
  // parents? If so, maybe make BindingExpression.Factory create it.

  private final Optional<ComponentBindingExpressions> parent;
  private final BindingGraph graph;
  private final ComponentImplementation componentImplementation;
  private final ComponentRequirementExpressions componentRequirementExpressions;
  private final OptionalFactories optionalFactories;
  private final DaggerTypes types;
  private final DaggerElements elements;
  private final SourceVersion sourceVersion;
  private final CompilerOptions compilerOptions;
  private final MembersInjectionMethods membersInjectionMethods;
  private final InnerSwitchingProviders innerSwitchingProviders;
  private final ModifiableBindingExpressions modifiableBindingExpressions;
  private final Map<BindingRequest, BindingExpression> expressions = new HashMap<>();

  @Inject
  ComponentBindingExpressions(
      @ParentComponent Optional<ComponentBindingExpressions> parent,
      BindingGraph graph,
      ComponentImplementation componentImplementation,
      ComponentRequirementExpressions componentRequirementExpressions,
      OptionalFactories optionalFactories,
      DaggerTypes types,
      DaggerElements elements,
      SourceVersion sourceVersion,
      @GenerationCompilerOptions CompilerOptions compilerOptions) {
    this.parent = parent;
    this.graph = graph;
    this.componentImplementation = componentImplementation;
    this.componentRequirementExpressions = checkNotNull(componentRequirementExpressions);
    this.optionalFactories = checkNotNull(optionalFactories);
    this.types = checkNotNull(types);
    this.elements = checkNotNull(elements);
    this.sourceVersion = checkNotNull(sourceVersion);
    this.compilerOptions = checkNotNull(compilerOptions);
    this.membersInjectionMethods =
        new MembersInjectionMethods(componentImplementation, this, graph, elements, types);
    this.innerSwitchingProviders =
        new InnerSwitchingProviders(componentImplementation, this, types);
    this.modifiableBindingExpressions =
        new ModifiableBindingExpressions(
            parent.map(cbe -> cbe.modifiableBindingExpressions),
            this,
            graph,
            componentImplementation,
            compilerOptions,
            types);
  }

  /* Returns the {@link ModifiableBindingExpressions} for this component. */
  ModifiableBindingExpressions modifiableBindingExpressions() {
    return modifiableBindingExpressions;
  }

  /**
   * Returns an expression that evaluates to the value of a binding request for a binding owned by
   * this component or an ancestor.
   *
   * @param requestingClass the class that will contain the expression
   * @throws IllegalStateException if there is no binding expression that satisfies the request
   */
  Expression getDependencyExpression(BindingRequest request, ClassName requestingClass) {
    return getBindingExpression(request).getDependencyExpression(requestingClass);
  }

  /**
   * Equivalent to {@link #getDependencyExpression(BindingRequest, ClassName)} that is used only
   * when the request is for implementation of a component method.
   *
   * @throws IllegalStateException if there is no binding expression that satisfies the request
   */
  Expression getDependencyExpressionForComponentMethod(
      BindingRequest request,
      ComponentMethodDescriptor componentMethod,
      ComponentImplementation componentImplementation) {
    return getBindingExpression(request)
        .getDependencyExpressionForComponentMethod(componentMethod, componentImplementation);
  }

  /**
   * Returns the {@link CodeBlock} for the method arguments used with the factory {@code create()}
   * method for the given {@link ContributionBinding binding}.
   */
  CodeBlock getCreateMethodArgumentsCodeBlock(ContributionBinding binding) {
    return makeParametersCodeBlock(getCreateMethodArgumentsCodeBlocks(binding));
  }

  private ImmutableList<CodeBlock> getCreateMethodArgumentsCodeBlocks(ContributionBinding binding) {
    ImmutableList.Builder<CodeBlock> arguments = ImmutableList.builder();

    if (binding.requiresModuleInstance()) {
      arguments.add(
          componentRequirementExpressions.getExpressionDuringInitialization(
              ComponentRequirement.forModule(binding.contributingModule().get().asType()),
              componentImplementation.name()));
    }

    binding.frameworkDependencies().stream()
        .map(BindingRequest::bindingRequest)
        .map(request -> getDependencyExpression(request, componentImplementation.name()))
        .map(Expression::codeBlock)
        .forEach(arguments::add);

    return arguments.build();
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
  Expression getDependencyArgumentExpression(
      DependencyRequest dependencyRequest, ClassName requestingClass) {

    TypeMirror dependencyType = dependencyRequest.key().type();
    BindingRequest bindingRequest = bindingRequest(dependencyRequest);
    Expression dependencyExpression = getDependencyExpression(bindingRequest, requestingClass);

    if (compilerOptions.aheadOfTimeSubcomponents()) {
      TypeMirror requestedType =
          bindingRequest.requestedType(dependencyRequest.key().type(), types);
      // If dependencyExpression.type() has been erased to it's publicly accessible type in AOT,
      // we must sometimes cast the expression so that it is usable in the current component. To do
      // so, we check that without the cast the assignment would fail, that argument to this proxy
      // method erased the type, and that the raw type of the requested type is actually accessible
      // in the current class so that the cast is valid.
      if (!types.isAssignable(dependencyExpression.type(), requestedType)
          && !isRawTypePubliclyAccessible(requestedType)
          && isRawTypeAccessible(requestedType, requestingClass.packageName())) {
        return dependencyExpression.castTo(types.erasure(requestedType));
      }
    }

    if (dependencyRequest.kind().equals(RequestKind.INSTANCE)
        && !isTypeAccessibleFrom(dependencyType, requestingClass.packageName())
        && isRawTypeAccessible(dependencyType, requestingClass.packageName())) {
      return dependencyExpression.castTo(types.erasure(dependencyType));
    }

    return dependencyExpression;
  }

  /** Returns the implementation of a component method. */
  MethodSpec getComponentMethod(ComponentMethodDescriptor componentMethod) {
    checkArgument(componentMethod.dependencyRequest().isPresent());
    BindingRequest request = bindingRequest(componentMethod.dependencyRequest().get());
    MethodSpec.Builder method =
        MethodSpec.overriding(
            componentMethod.methodElement(),
            MoreTypes.asDeclared(graph.componentTypeElement().asType()),
            types);
    // Even though this is not used if the method is abstract, we need to invoke the binding
    // expression in order for the side of effect of the method being added to the
    // ComponentImplementation
    CodeBlock methodBody =
        getBindingExpression(request)
            .getComponentMethodImplementation(componentMethod, componentImplementation);
    if (!componentImplementation.superclassImplementation().isPresent()
        && !modifiableBindingExpressions
            .getModifiableBindingType(request)
            .hasBaseClassImplementation()
        && !componentImplementation.getModifiableBindingMethod(request).isPresent()) {
      return method.addModifiers(ABSTRACT).build();
    }
    return method.addCode(methodBody).build();
  }

  /** Returns the {@link BindingExpression} for the given {@link BindingRequest}. */
  BindingExpression getBindingExpression(BindingRequest request) {
    if (expressions.containsKey(request)) {
      return expressions.get(request);
    }
    Optional<BindingExpression> expression =
        modifiableBindingExpressions.maybeCreateModifiableBindingExpression(request);
    if (!expression.isPresent()) {
      ResolvedBindings resolvedBindings = graph.resolvedBindings(request);
      if (resolvedBindings != null
          && !resolvedBindings.bindingsOwnedBy(graph.componentDescriptor()).isEmpty()) {
        expression = Optional.of(createBindingExpression(resolvedBindings, request));
      }
    }
    if (!expression.isPresent()
        && compilerOptions.aheadOfTimeSubcomponents()
        && request.requestKind().isPresent()
        && isDerivedFromProvider(request.requestKind().get())) {
      RequestKind requestKind = request.requestKind().get();
      expression =
          Optional.of(
              new DerivedFromFrameworkInstanceBindingExpression(
                  request.key(), FrameworkType.PROVIDER, requestKind, this, types));
    }

    if (expression.isPresent()) {
      expressions.put(request, expression.get());
      return expression.get();
    }
    checkArgument(parent.isPresent(), "no expression found for %s", request);
    return parent.get().getBindingExpression(request);
  }

  /** Creates a binding expression. */
  BindingExpression createBindingExpression(
      ResolvedBindings resolvedBindings, BindingRequest request) {
    switch (resolvedBindings.bindingType()) {
      case MEMBERS_INJECTION:
        checkArgument(request.isRequestKind(RequestKind.MEMBERS_INJECTION));
        return new MembersInjectionBindingExpression(resolvedBindings, membersInjectionMethods);

      case PROVISION:
        return provisionBindingExpression(resolvedBindings, request);

      case PRODUCTION:
        return productionBindingExpression(resolvedBindings, request);
    }
    throw new AssertionError(resolvedBindings);
  }

  /**
   * Returns a binding expression that uses a {@link javax.inject.Provider} for provision bindings
   * or a {@link dagger.producers.Producer} for production bindings.
   */
  private BindingExpression frameworkInstanceBindingExpression(ResolvedBindings resolvedBindings) {
    // TODO(user): Consider merging the static factory creation logic into CreationExpressions?
    Optional<MemberSelect> staticMethod =
        useStaticFactoryCreation(resolvedBindings.contributionBinding())
            ? staticFactoryCreation(resolvedBindings)
            : Optional.empty();
    FrameworkInstanceCreationExpression frameworkInstanceCreationExpression =
        resolvedBindings.scope().isPresent()
            ? scope(resolvedBindings, frameworkInstanceCreationExpression(resolvedBindings))
            : frameworkInstanceCreationExpression(resolvedBindings);
    FrameworkInstanceSupplier frameworkInstanceSupplier =
        staticMethod.isPresent()
            ? staticMethod::get
            : new FrameworkFieldInitializer(
                componentImplementation, resolvedBindings, frameworkInstanceCreationExpression);

    switch (resolvedBindings.bindingType()) {
      case PROVISION:
        return new ProviderInstanceBindingExpression(
            resolvedBindings, frameworkInstanceSupplier, types, elements);
      case PRODUCTION:
        return new ProducerNodeInstanceBindingExpression(
            resolvedBindings, frameworkInstanceSupplier, types, elements, componentImplementation);
      default:
        throw new AssertionError("invalid binding type: " + resolvedBindings.bindingType());
    }
  }

  private FrameworkInstanceCreationExpression scope(
      ResolvedBindings resolvedBindings, FrameworkInstanceCreationExpression unscoped) {
    return () ->
        CodeBlock.of(
            "$T.provider($L)",
            resolvedBindings.scope().get().isReusable() ? SINGLE_CHECK : DOUBLE_CHECK,
            unscoped.creationExpression());
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
        // The cast can be removed when we drop java 7 source support
        return new InstanceFactoryCreationExpression(
            () -> CodeBlock.of("($T) this", binding.key().type()));

      case BOUND_INSTANCE:
        return instanceFactoryCreationExpression(
            binding, ComponentRequirement.forBoundInstance(binding));

      case COMPONENT_DEPENDENCY:
        return instanceFactoryCreationExpression(
            binding, ComponentRequirement.forDependency(binding.key().type()));

      case COMPONENT_PROVISION:
        return new DependencyMethodProviderCreationExpression(
            binding,
            componentImplementation,
            componentRequirementExpressions,
            compilerOptions,
            graph);

      case SUBCOMPONENT_CREATOR:
        return new AnonymousProviderCreationExpression(
            binding, this, componentImplementation.name());

      case INJECTION:
      case PROVISION:
        return new InjectionOrProvisionProviderCreationExpression(binding, this);

      case COMPONENT_PRODUCTION:
        return new DependencyMethodProducerCreationExpression(
            binding, componentImplementation, componentRequirementExpressions, graph);

      case PRODUCTION:
        return new ProducerCreationExpression(binding, this);

      case MULTIBOUND_SET:
        return new SetFactoryCreationExpression(binding, componentImplementation, this, graph);

      case MULTIBOUND_MAP:
        return new MapFactoryCreationExpression(
            binding, componentImplementation, this, graph, elements);

      case DELEGATE:
        return new DelegatingFrameworkInstanceCreationExpression(
            binding, componentImplementation, this);

      case OPTIONAL:
        return new OptionalFactoryInstanceCreationExpression(
            optionalFactories, binding, componentImplementation, this);

      case MEMBERS_INJECTOR:
        return new MembersInjectorProviderCreationExpression((ProvisionBinding) binding, this);

      default:
        throw new AssertionError(binding);
    }
  }

  private InstanceFactoryCreationExpression instanceFactoryCreationExpression(
      ContributionBinding binding, ComponentRequirement componentRequirement) {
    return new InstanceFactoryCreationExpression(
        binding.nullableType().isPresent(),
        () ->
            componentRequirementExpressions.getExpressionDuringInitialization(
                componentRequirement, componentImplementation.name()));
  }

  /** Returns a binding expression for a provision binding. */
  private BindingExpression provisionBindingExpression(
      ResolvedBindings resolvedBindings, BindingRequest request) {
    if (!request.requestKind().isPresent()) {
      verify(
          request.frameworkType().get().equals(FrameworkType.PRODUCER_NODE),
          "expected a PRODUCER_NODE: %s",
          request);
      return producerFromProviderBindingExpression(resolvedBindings);
    }
    RequestKind requestKind = request.requestKind().get();
    switch (requestKind) {
      case INSTANCE:
        return instanceBindingExpression(resolvedBindings);

      case PROVIDER:
        return providerBindingExpression(resolvedBindings);

      case LAZY:
      case PRODUCED:
      case PROVIDER_OF_LAZY:
        return new DerivedFromFrameworkInstanceBindingExpression(
            resolvedBindings.key(), FrameworkType.PROVIDER, requestKind, this, types);

      case PRODUCER:
        return producerFromProviderBindingExpression(resolvedBindings);

      case FUTURE:
        return new ImmediateFutureBindingExpression(resolvedBindings, this, types, sourceVersion);

      case MEMBERS_INJECTION:
        throw new IllegalArgumentException();
    }

    throw new AssertionError();
  }

  /** Returns a binding expression for a production binding. */
  private BindingExpression productionBindingExpression(
      ResolvedBindings resolvedBindings, BindingRequest request) {
    if (request.frameworkType().isPresent()) {
      return frameworkInstanceBindingExpression(resolvedBindings);
    } else {
      // If no FrameworkType is present, a RequestKind is guaranteed to be present.
      RequestKind requestKind = request.requestKind().get();
      return new DerivedFromFrameworkInstanceBindingExpression(
          resolvedBindings.key(), FrameworkType.PRODUCER_NODE, requestKind, this, types);
    }
  }

  /**
   * Returns a binding expression for {@link RequestKind#PROVIDER} requests.
   *
   * <p>{@code @Binds} bindings that don't {@linkplain #needsCaching(ResolvedBindings) need to be
   * cached} can use a {@link DelegateBindingExpression}.
   *
   * <p>In fastInit mode, use an {@link InnerSwitchingProviders inner switching provider} unless
   * that provider's case statement will simply call {@code get()} on another {@link Provider} (in
   * which case, just use that Provider directly).
   *
   * <p>Otherwise, return a {@link FrameworkInstanceBindingExpression}.
   */
  private BindingExpression providerBindingExpression(ResolvedBindings resolvedBindings) {
    if (resolvedBindings.contributionBinding().kind().equals(DELEGATE)
        && !needsCaching(resolvedBindings)) {
      return new DelegateBindingExpression(
          resolvedBindings, RequestKind.PROVIDER, this, types, elements);
    } else if (compilerOptions.fastInit()
        && frameworkInstanceCreationExpression(resolvedBindings).useInnerSwitchingProvider()
        && !(instanceBindingExpression(resolvedBindings)
            instanceof DerivedFromFrameworkInstanceBindingExpression)) {
      return wrapInMethod(
          resolvedBindings,
          bindingRequest(resolvedBindings.key(), RequestKind.PROVIDER),
          innerSwitchingProviders.newBindingExpression(resolvedBindings.contributionBinding()));
    }
    return frameworkInstanceBindingExpression(resolvedBindings);
  }

  /**
   * Returns a binding expression that uses a {@link dagger.producers.Producer} field for a
   * provision binding.
   */
  private FrameworkInstanceBindingExpression producerFromProviderBindingExpression(
      ResolvedBindings resolvedBindings) {
    checkArgument(resolvedBindings.bindingType().equals(BindingType.PROVISION));
    return new ProducerNodeInstanceBindingExpression(
        resolvedBindings,
        new FrameworkFieldInitializer(
            componentImplementation,
            resolvedBindings,
            new ProducerFromProviderCreationExpression(
                resolvedBindings.contributionBinding(), componentImplementation, this)),
        types,
        elements,
        componentImplementation);
  }

  /**
   * Returns a binding expression for {@link RequestKind#INSTANCE} requests.
   *
   * <p>If there is a direct expression (not calling {@link Provider#get()}) we can use for an
   * instance of this binding, return it, wrapped in a method if the binding {@linkplain
   * #needsCaching(ResolvedBindings) needs to be cached} or the expression has dependencies.
   *
   * <p>In fastInit mode, we can use direct expressions unless the binding needs to be cached.
   */
  private BindingExpression instanceBindingExpression(ResolvedBindings resolvedBindings) {
    Optional<BindingExpression> maybeDirectInstanceExpression =
        unscopedDirectInstanceExpression(resolvedBindings);
    if (canUseDirectInstanceExpression(resolvedBindings)
        && maybeDirectInstanceExpression.isPresent()) {
      BindingExpression directInstanceExpression = maybeDirectInstanceExpression.get();
      return directInstanceExpression.requiresMethodEncapsulation()
              || needsCaching(resolvedBindings)
          ? wrapInMethod(
              resolvedBindings,
              bindingRequest(resolvedBindings.key(), RequestKind.INSTANCE),
              directInstanceExpression)
          : directInstanceExpression;
    }
    return new DerivedFromFrameworkInstanceBindingExpression(
        resolvedBindings.key(), FrameworkType.PROVIDER, RequestKind.INSTANCE, this, types);
  }

  /**
   * Returns an unscoped binding expression for an {@link RequestKind#INSTANCE} that does not call
   * {@code get()} on its provider, if there is one.
   */
  private Optional<BindingExpression> unscopedDirectInstanceExpression(
      ResolvedBindings resolvedBindings) {
    switch (resolvedBindings.contributionBinding().kind()) {
      case DELEGATE:
        return Optional.of(
            new DelegateBindingExpression(
                resolvedBindings, RequestKind.INSTANCE, this, types, elements));

      case COMPONENT:
        return Optional.of(
            new ComponentInstanceBindingExpression(
                resolvedBindings, componentImplementation.name()));

      case COMPONENT_DEPENDENCY:
        return Optional.of(
            new ComponentRequirementBindingExpression(
                resolvedBindings,
                ComponentRequirement.forDependency(resolvedBindings.key().type()),
                componentRequirementExpressions));

      case COMPONENT_PROVISION:
        return Optional.of(
            new ComponentProvisionBindingExpression(
                resolvedBindings, graph, componentRequirementExpressions, compilerOptions));

      case SUBCOMPONENT_CREATOR:
        return Optional.of(
            new SubcomponentCreatorBindingExpression(
                resolvedBindings,
                componentImplementation.getSubcomponentCreatorSimpleName(resolvedBindings.key())));

      case MULTIBOUND_SET:
        return Optional.of(
            new SetBindingExpression(
                resolvedBindings, componentImplementation, graph, this, types, elements));

      case MULTIBOUND_MAP:
        return Optional.of(
            new MapBindingExpression(
                resolvedBindings, componentImplementation, graph, this, types, elements));

      case OPTIONAL:
        return Optional.of(
            new OptionalBindingExpression(resolvedBindings, this, types, sourceVersion));

      case BOUND_INSTANCE:
        return Optional.of(
            new ComponentRequirementBindingExpression(
                resolvedBindings,
                ComponentRequirement.forBoundInstance(resolvedBindings.contributionBinding()),
                componentRequirementExpressions));

      case INJECTION:
      case PROVISION:
        return Optional.of(
            new SimpleMethodBindingExpression(
                resolvedBindings,
                compilerOptions,
                this,
                membersInjectionMethods,
                componentRequirementExpressions,
                types,
                elements,
                sourceVersion));

      case MEMBERS_INJECTOR:
        return Optional.empty();

      case MEMBERS_INJECTION:
      case COMPONENT_PRODUCTION:
      case PRODUCTION:
        throw new IllegalArgumentException(
            resolvedBindings.contributionBinding().kind().toString());
    }
    throw new AssertionError();
  }

  /**
   * Returns {@code true} if the binding should use the static factory creation strategy.
   *
   * <p>In default mode, we always use the static factory creation strategy. In fastInit mode, we
   * prefer to use a SwitchingProvider instead of static factories in order to reduce class loading;
   * however, we allow static factories that can reused across multiple bindings, e.g. {@code
   * MapFactory} or {@code SetFactory}.
   */
  private boolean useStaticFactoryCreation(ContributionBinding binding) {
    return !compilerOptions.fastInit()
        || binding.kind().equals(MULTIBOUND_MAP)
        || binding.kind().equals(MULTIBOUND_SET);
  }

  /**
   * Returns {@code true} if we can use a direct (not {@code Provider.get()}) expression for this
   * binding. If the binding doesn't {@linkplain #needsCaching(ResolvedBindings) need to be cached},
   * we can.
   *
   * <p>In fastInit mode, we can use a direct expression even if the binding {@linkplain
   * #needsCaching(ResolvedBindings) needs to be cached}.
   */
  private boolean canUseDirectInstanceExpression(ResolvedBindings resolvedBindings) {
    return !needsCaching(resolvedBindings) || compilerOptions.fastInit();
  }

  /**
   * Returns a binding expression that uses a given one as the body of a method that users call. If
   * a component provision method matches it, it will be the method implemented. If it does not
   * match a component provision method and the binding is modifiable, then a new public modifiable
   * binding method will be written. If the binding doesn't match a component method and is not
   * modifiable, then a new private method will be written.
   */
  BindingExpression wrapInMethod(
      ResolvedBindings resolvedBindings,
      BindingRequest request,
      BindingExpression bindingExpression) {
    // If we've already wrapped the expression, then use the delegate.
    if (bindingExpression instanceof MethodBindingExpression) {
      return bindingExpression;
    }

    MethodImplementationStrategy methodImplementationStrategy =
        methodImplementationStrategy(resolvedBindings, request);
    Optional<ComponentMethodDescriptor> matchingComponentMethod =
        graph.componentDescriptor().firstMatchingComponentMethod(request);

    if (modifiableBindingExpressions.getModifiableBindingType(request).isModifiable()
        && (componentImplementation.superclassImplementation().isPresent()
            || !matchingComponentMethod.isPresent())) {
      return modifiableBindingExpressions.wrapInModifiableMethodBindingExpression(
          request, resolvedBindings, methodImplementationStrategy, bindingExpression);
    } else if (matchingComponentMethod.isPresent()) {
      ComponentMethodDescriptor componentMethod = matchingComponentMethod.get();
      return new ComponentMethodBindingExpression(
          request,
          resolvedBindings,
          methodImplementationStrategy,
          bindingExpression,
          componentImplementation,
          componentMethod,
          types);
    } else {
      return new PrivateMethodBindingExpression(
          request,
          resolvedBindings,
          methodImplementationStrategy,
          bindingExpression,
          componentImplementation,
          types);
    }
  }

  private MethodImplementationStrategy methodImplementationStrategy(
      ResolvedBindings resolvedBindings, BindingRequest request) {
    if (compilerOptions.fastInit()) {
      if (request.isRequestKind(RequestKind.PROVIDER)) {
        return MethodImplementationStrategy.SINGLE_CHECK;
      } else if (request.isRequestKind(RequestKind.INSTANCE) && needsCaching(resolvedBindings)) {
        return resolvedBindings.scope().get().isReusable()
            ? MethodImplementationStrategy.SINGLE_CHECK
            : MethodImplementationStrategy.DOUBLE_CHECK;
      }
    }
    return MethodImplementationStrategy.SIMPLE;
  }

  /**
   * Returns {@code true} if the component needs to make sure the provided value is cached.
   *
   * <p>The component needs to cache the value for scoped bindings except for {@code @Binds}
   * bindings whose scope is no stronger than their delegate's.
   */
  private boolean needsCaching(ResolvedBindings resolvedBindings) {
    if (!resolvedBindings.scope().isPresent()) {
      return false;
    }
    if (resolvedBindings.contributionBinding().kind().equals(DELEGATE)) {
      return isBindsScopeStrongerThanDependencyScope(resolvedBindings, graph);
    }
    return true;
  }
}

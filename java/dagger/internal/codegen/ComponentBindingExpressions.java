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
import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.Accessibility.isRawTypeAccessible;
import static dagger.internal.codegen.Accessibility.isTypeAccessibleFrom;
import static dagger.internal.codegen.BindingType.MEMBERS_INJECTION;
import static dagger.internal.codegen.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.DelegateBindingExpression.isBindsScopeStrongerThanDependencyScope;
import static dagger.internal.codegen.MemberSelect.staticFactoryCreation;
import static dagger.internal.codegen.TypeNames.DOUBLE_CHECK;
import static dagger.internal.codegen.TypeNames.REFERENCE_RELEASING_PROVIDER;
import static dagger.internal.codegen.TypeNames.SINGLE_CHECK;
import static dagger.model.BindingKind.DELEGATE;
import static dagger.model.BindingKind.MULTIBOUND_MAP;
import static dagger.model.BindingKind.MULTIBOUND_SET;
import static javax.lang.model.element.Modifier.PUBLIC;

import com.google.auto.common.MoreTypes;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;
import dagger.internal.codegen.ModifiableBindingMethods.ModifiableBindingMethod;
import dagger.model.DependencyRequest;
import dagger.model.Key;
import dagger.model.RequestKind;
import java.util.Optional;
import javax.inject.Provider;
import javax.lang.model.type.TypeMirror;

/** A central repository of code expressions used to access any binding available to a component. */
final class ComponentBindingExpressions {

  // TODO(dpb,ronshapiro): refactor this and ComponentRequirementFields into a
  // HierarchicalComponentMap<K, V>, or perhaps this use a flattened ImmutableMap, built from its
  // parents? If so, maybe make BindingExpression.Factory create it.

  private final Optional<ComponentBindingExpressions> parent;
  private final BindingGraph graph;
  private final GeneratedComponentModel generatedComponentModel;
  private final SubcomponentNames subcomponentNames;
  private final ComponentRequirementFields componentRequirementFields;
  private final ReferenceReleasingManagerFields referenceReleasingManagerFields;
  private final OptionalFactories optionalFactories;
  private final DaggerTypes types;
  private final DaggerElements elements;
  private final CompilerOptions compilerOptions;
  private final MembersInjectionMethods membersInjectionMethods;
  private final InnerSwitchingProviders innerSwitchingProviders;
  private final StaticSwitchingProviders staticSwitchingProviders;
  private final Table<Key, RequestKind, BindingExpression> expressions = HashBasedTable.create();

  ComponentBindingExpressions(
      BindingGraph graph,
      GeneratedComponentModel generatedComponentModel,
      SubcomponentNames subcomponentNames,
      ComponentRequirementFields componentRequirementFields,
      OptionalFactories optionalFactories,
      DaggerTypes types,
      DaggerElements elements,
      CompilerOptions compilerOptions) {
    this(
        Optional.empty(),
        graph,
        generatedComponentModel,
        subcomponentNames,
        componentRequirementFields,
        new ReferenceReleasingManagerFields(graph, generatedComponentModel, compilerOptions),
        new StaticSwitchingProviders(generatedComponentModel, types),
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
      StaticSwitchingProviders staticSwitchingProviders,
      OptionalFactories optionalFactories,
      DaggerTypes types,
      DaggerElements elements,
      CompilerOptions compilerOptions) {
    this.parent = parent;
    this.graph = graph;
    this.generatedComponentModel = generatedComponentModel;
    this.subcomponentNames = checkNotNull(subcomponentNames);
    this.componentRequirementFields = checkNotNull(componentRequirementFields);
    this.referenceReleasingManagerFields = checkNotNull(referenceReleasingManagerFields);
    this.optionalFactories = checkNotNull(optionalFactories);
    this.types = checkNotNull(types);
    this.elements = checkNotNull(elements);
    this.compilerOptions = checkNotNull(compilerOptions);
    this.membersInjectionMethods =
        new MembersInjectionMethods(generatedComponentModel, this, graph, elements, types);
    this.innerSwitchingProviders =
        new InnerSwitchingProviders(generatedComponentModel, this, types);
    this.staticSwitchingProviders = staticSwitchingProviders;
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
        subcomponentNames,
        childComponentRequirementFields,
        referenceReleasingManagerFields,
        staticSwitchingProviders,
        optionalFactories,
        types,
        elements,
        compilerOptions);
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
   * Returns the {@link CodeBlock} for the method argmuments used with the factory {@code create()}
   * method for the given {@link ContributionBinding binding}.
   */
  CodeBlock getCreateMethodArgumentsCodeBlock(ContributionBinding binding) {
    return makeParametersCodeBlock(getCreateMethodArgumentsCodeBlocks(binding));
  }

  private ImmutableList<CodeBlock> getCreateMethodArgumentsCodeBlocks(ContributionBinding binding) {
    ImmutableList.Builder<CodeBlock> arguments = ImmutableList.builder();

    if (binding.requiresModuleInstance()) {
      arguments.add(
          componentRequirementFields.getExpressionDuringInitialization(
              ComponentRequirement.forModule(binding.contributingModule().get().asType()),
              generatedComponentModel.name()));
    }

    binding
        .frameworkDependencies()
        .stream()
        .map(dependency -> getDependencyExpression(dependency, generatedComponentModel.name()))
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
  // TODO(b/64024402) Merge with getDependencyExpression(DependencyRequest, ClassName) if possible.
  Expression getDependencyArgumentExpression(
      DependencyRequest dependencyRequest, ClassName requestingClass) {

    TypeMirror dependencyType = dependencyRequest.key().type();
    Expression dependencyExpression = getDependencyExpression(dependencyRequest, requestingClass);

    if (dependencyRequest.kind().equals(RequestKind.INSTANCE)
        && !isTypeAccessibleFrom(dependencyType, requestingClass.packageName())
        && isRawTypeAccessible(dependencyType, requestingClass.packageName())) {
      return dependencyExpression.castTo(types.erasure(dependencyType));
    }

    return dependencyExpression;
  }

  /**
   * Returns the implementation of a component method. Returns {@link Optional#empty} if the
   * component method implementation should not be emitted.
   */
  Optional<MethodSpec> getComponentMethod(ComponentMethodDescriptor componentMethod) {
    checkArgument(componentMethod.dependencyRequest().isPresent());
    DependencyRequest dependencyRequest = componentMethod.dependencyRequest().get();
    MethodSpec.Builder methodBuilder =
        MethodSpec.overriding(
            componentMethod.methodElement(),
            MoreTypes.asDeclared(graph.componentType().asType()),
            types);

    ModifiableBindingType type =
        getModifiableBindingType(dependencyRequest.key(), dependencyRequest.kind());
    if (type.isModifiable()) {
      generatedComponentModel.registerModifiableBindingMethod(
          type, dependencyRequest.key(), dependencyRequest.kind(), methodBuilder.build());
      if (!type.hasBaseClassImplementation()) {
        // A component method should not be emitted if it encapsulates a modifiable binding that
        // cannot be satisfied by the abstract base class implementation of a subcomponent.
        checkState(
            !generatedComponentModel.supermodel().isPresent(),
            "Attempting to generate a component method in a subtype of the abstract subcomponent "
                + "base class.");
        return Optional.empty();
      }
    }

    return Optional.of(
        methodBuilder
            .addCode(
                getBindingExpression(dependencyRequest.key(), dependencyRequest.kind())
                    .getComponentMethodImplementation(componentMethod, generatedComponentModel))
            .build());
  }

  /**
   * Returns the implementation of a method encapsulating a modifiable binding in a supertype
   * implementation of this subcomponent. Returns {@link Optional#empty()} when the binding cannot
   * or should not be modified by the current binding graph. This is only relevant for ahead-of-time
   * subcomponents.
   */
  Optional<MethodSpec> getModifiableBindingMethod(ModifiableBindingMethod modifiableBindingMethod) {
    if (shouldOverrideModifiableBindingMethod(modifiableBindingMethod)) {
      Expression bindingExpression =
          getDependencyExpression(
              modifiableBindingMethod.key(),
              modifiableBindingMethod.kind(),
              generatedComponentModel.name());
      MethodSpec baseMethod = modifiableBindingMethod.baseMethod();
      return Optional.of(
          MethodSpec.methodBuilder(baseMethod.name)
              .addModifiers(PUBLIC)
              .returns(baseMethod.returnType)
              .addAnnotation(Override.class)
              .addStatement("return $L", bindingExpression.codeBlock())
              .build());
    }
    return Optional.empty();
  }

  private BindingExpression getBindingExpression(Key key, RequestKind requestKind) {
    if (expressions.contains(key, requestKind)) {
      return expressions.get(key, requestKind);
    }
    Optional<BindingExpression> expression = Optional.empty();
    ModifiableBindingType modifiableBindingType = getModifiableBindingType(key, requestKind);
    if (modifiableBindingType.isModifiable()) {
      expression =
          Optional.of(createModifiableBindingExpression(modifiableBindingType, key, requestKind));
    } else if (resolvedInThisComponent(key, requestKind)) {
      ResolvedBindings resolvedBindings = graph.resolvedBindings(requestKind, key);
      expression = Optional.of(createBindingExpression(resolvedBindings, requestKind));
    }
    if (expression.isPresent()) {
      expressions.put(key, requestKind, expression.get());
      return expression.get();
    }
    checkArgument(parent.isPresent(), "no expression found for %s-%s", key, requestKind);
    return parent.get().getBindingExpression(key, requestKind);
  }

  /** Creates a binding expression. */
  private BindingExpression createBindingExpression(
      ResolvedBindings resolvedBindings, RequestKind requestKind) {
    switch (resolvedBindings.bindingType()) {
      case MEMBERS_INJECTION:
        checkArgument(requestKind.equals(RequestKind.MEMBERS_INJECTION));
        return new MembersInjectionBindingExpression(resolvedBindings, membersInjectionMethods);

      case PROVISION:
        return provisionBindingExpression(resolvedBindings, requestKind);

      case PRODUCTION:
        if (requestKind.equals(RequestKind.PRODUCER)) {
          return frameworkInstanceBindingExpression(resolvedBindings);
        } else {
          return new DerivedFromFrameworkInstanceBindingExpression(
              resolvedBindings, requestKind, this, types);
        }

      default:
        throw new AssertionError(resolvedBindings);
    }
  }

  /**
   * Creates a binding expression for a binding that may be modified across implementations of a
   * subcomponent. This is only relevant for ahead-of-time subcomponents.
   */
  private BindingExpression createModifiableBindingExpression(
      ModifiableBindingType type, Key key, RequestKind requestKind) {
    switch (type) {
      case GENERATED_INSTANCE:
        ResolvedBindings resolvedBindings = graph.resolvedBindings(requestKind, key);
        return new GeneratedInstanceBindingExpression(
            generatedComponentModel, resolvedBindings, requestKind);
      case MISSING:
        return new MissingBindingExpression(generatedComponentModel, key, requestKind);
      default:
        throw new IllegalStateException(
            String.format(
                "Building binding expression for unsupported ModifiableBindingType [%s].", type));
    }
  }

  /**
   * The reason why a binding may need to be modified across implementations of a subcomponent, if
   * at all. This is only relevant for ahead-of-time subcomponents.
   */
  private ModifiableBindingType getModifiableBindingType(Key key, RequestKind requestKind) {
    if (!compilerOptions.aheadOfTimeSubcomponents()) {
      return ModifiableBindingType.NONE;
    }

    // When generating a final (concrete) implementation of a (sub)component the binding is no
    // longer considered modifiable. It cannot be further modified by a subclass implementation.
    if (!generatedComponentModel.isAbstract()) {
      return ModifiableBindingType.NONE;
    }

    if (resolvedInThisComponent(key, requestKind)) {
      ResolvedBindings resolvedBindings = graph.resolvedBindings(requestKind, key);
      if (resolvedBindings.contributionBindings().isEmpty()) {
        // TODO(ronshapiro): Confirm whether a resolved binding must have a single contribution
        // binding.
        return ModifiableBindingType.NONE;
      }

      ContributionBinding binding = resolvedBindings.contributionBinding();
      if (binding.requiresGeneratedInstance()) {
        return ModifiableBindingType.GENERATED_INSTANCE;
      }
    } else if (!resolvableBinding(key, requestKind)) {
      return ModifiableBindingType.MISSING;
    }

    // TODO(b/72748365): Add support for remaining types.
    return ModifiableBindingType.NONE;
  }

  /**
   * Returns true if the current binding graph can, and should, modify a binding by overriding a
   * modfiable binding method. This is only relevant for ahead-of-time subcomponents.
   */
  private boolean shouldOverrideModifiableBindingMethod(
      ModifiableBindingMethod modifiableBindingMethod) {
    switch (modifiableBindingMethod.type()) {
      case GENERATED_INSTANCE:
        return !generatedComponentModel.isAbstract();
      case MISSING:
        // TODO(b/72748365): investigate beder@'s comment about having intermediate component
        // ancestors satisfy missing bindings of their children with their own missing binding
        // methods so that we can minimize the cases where we need to reach into doubly-nested
        // descendant component implementations
        return resolvableBinding(modifiableBindingMethod.key(), modifiableBindingMethod.kind());
      default:
        throw new IllegalStateException(
            String.format(
                "Overriding modifiable binding method with unsupported ModifiableBindingType [%s].",
                modifiableBindingMethod.type()));
    }
  }

  /**
   * Returns true if the binding can be resolved by the graph for this component or any parent
   * component.
   */
  private boolean resolvableBinding(Key key, RequestKind requestKind) {
    for (ComponentBindingExpressions expressions = this;
        expressions != null;
        expressions = expressions.parent.orElse(null)) {
      if (expressions.resolvedInThisComponent(key, requestKind)) {
        return true;
      }
    }
    return false;
  }

  /** Returns true if the binding can be resolved by the graph for this component. */
  private boolean resolvedInThisComponent(Key key, RequestKind requestKind) {
    ResolvedBindings resolvedBindings = graph.resolvedBindings(requestKind, key);
    return resolvedBindings != null && !resolvedBindings.ownedBindings().isEmpty();
  }

  /**
   * Returns a binding expression that uses a {@link javax.inject.Provider} for provision bindings
   * or a {@link dagger.producers.Producer} for production bindings.
   */
  private FrameworkInstanceBindingExpression frameworkInstanceBindingExpression(
      ResolvedBindings resolvedBindings) {
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
                generatedComponentModel, resolvedBindings, frameworkInstanceCreationExpression);

    FrameworkType frameworkType = resolvedBindings.bindingType().frameworkType();
    switch (frameworkType) {
      case PROVIDER:
        return new ProviderInstanceBindingExpression(
            resolvedBindings, frameworkInstanceSupplier, types, elements);
      case PRODUCER:
        return new ProducerInstanceBindingExpression(
            resolvedBindings, frameworkInstanceSupplier, types, elements);
      default:
        throw new AssertionError("invalid framework type: " + frameworkType);
    }
  }

  private FrameworkInstanceCreationExpression scope(
      ResolvedBindings resolvedBindings, FrameworkInstanceCreationExpression unscoped) {
    if (requiresReleasableReferences(resolvedBindings)) {
      return () ->
          CodeBlock.of(
              "$T.create($L, $L)",
              REFERENCE_RELEASING_PROVIDER,
              unscoped.creationExpression(),
              referenceReleasingManagerFields.getExpression(
                  resolvedBindings.scope().get(), generatedComponentModel.name()));
    } else {
      return () ->
          CodeBlock.of(
              "$T.provider($L)",
              resolvedBindings.scope().get().isReusable() ? SINGLE_CHECK : DOUBLE_CHECK,
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
            binding, generatedComponentModel, componentRequirementFields, compilerOptions, graph);

      case SUBCOMPONENT_BUILDER:
        return new SubcomponentBuilderProviderCreationExpression(
            binding.key().type(), subcomponentNames.get(binding.key()));

      case INJECTION:
      case PROVISION:
        return compilerOptions.experimentalAndroidMode2()
            ? staticSwitchingProviders.newCreationExpression(binding, this)
            : new InjectionOrProvisionProviderCreationExpression(binding, this);

      case COMPONENT_PRODUCTION:
        return new DependencyMethodProducerCreationExpression(
            binding, generatedComponentModel, componentRequirementFields, graph);

      case PRODUCTION:
        return new ProducerCreationExpression(binding, this);

      case MULTIBOUND_SET:
        return new SetFactoryCreationExpression(binding, generatedComponentModel, this, graph);

      case MULTIBOUND_MAP:
        return new MapFactoryCreationExpression(
            binding, generatedComponentModel, this, graph, elements);

      case RELEASABLE_REFERENCE_MANAGER:
        return new ReleasableReferenceManagerProviderCreationExpression(
            binding, generatedComponentModel, referenceReleasingManagerFields);

      case RELEASABLE_REFERENCE_MANAGERS:
        return new ReleasableReferenceManagerSetProviderCreationExpression(
            binding, generatedComponentModel, referenceReleasingManagerFields, graph);

      case DELEGATE:
        return new DelegatingFrameworkInstanceCreationExpression(
            binding, generatedComponentModel, this);

      case OPTIONAL:
        return new OptionalFactoryInstanceCreationExpression(
            optionalFactories, binding, generatedComponentModel, this);

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
            componentRequirementFields.getExpressionDuringInitialization(
                componentRequirement, generatedComponentModel.name()));
  }

  /** Returns a binding expression for a provision binding. */
  private BindingExpression provisionBindingExpression(
      ResolvedBindings resolvedBindings, RequestKind requestKind) {
    switch (requestKind) {
      case INSTANCE:
        return instanceBindingExpression(resolvedBindings);

      case PROVIDER:
        return providerBindingExpression(resolvedBindings);

      case LAZY:
      case PRODUCED:
      case PROVIDER_OF_LAZY:
        return new DerivedFromFrameworkInstanceBindingExpression(
            resolvedBindings, requestKind, this, types);

      case PRODUCER:
        return producerFromProviderBindingExpression(resolvedBindings);

      case FUTURE:
        return new ImmediateFutureBindingExpression(resolvedBindings, this, types);

      case MEMBERS_INJECTION:
        throw new IllegalArgumentException();
    }

    throw new AssertionError();
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
          RequestKind.PROVIDER,
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
    checkArgument(resolvedBindings.bindingType().frameworkType().equals(FrameworkType.PROVIDER));
    return new ProducerInstanceBindingExpression(
        resolvedBindings,
        new FrameworkFieldInitializer(
            generatedComponentModel,
            resolvedBindings,
            new ProducerFromProviderCreationExpression(
                resolvedBindings.contributionBinding(), generatedComponentModel, this)),
        types,
        elements);
  }

  /**
   * Returns a binding expression for {@link RequestKind#INSTANCE} requests.
   *
   * <p>If there is a direct expression (not calling {@link Provider#get()}) we can use for an
   * instance of this binding, return it, wrapped in a method if the binding {@linkplain
   * #needsCaching(ResolvedBindings) needs to be cached} or the expression has dependencies.
   *
   * <p>In default mode, we can use direct expressions for bindings that don't need to be cached in
   * a reference-releasing scope.
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
          ? wrapInMethod(resolvedBindings, RequestKind.INSTANCE, directInstanceExpression)
          : directInstanceExpression;
    }
    return new DerivedFromFrameworkInstanceBindingExpression(
        resolvedBindings, RequestKind.INSTANCE, this, types);
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
                resolvedBindings, generatedComponentModel.name()));

      case COMPONENT_DEPENDENCY:
        return Optional.of(
            new ComponentRequirementBindingExpression(
                resolvedBindings,
                ComponentRequirement.forDependency(resolvedBindings.key().type()),
                componentRequirementFields));

      case COMPONENT_PROVISION:
        return Optional.of(
            new ComponentProvisionBindingExpression(
                resolvedBindings, graph, componentRequirementFields, compilerOptions));

      case SUBCOMPONENT_BUILDER:
        return Optional.of(
            new SubcomponentBuilderBindingExpression(
                resolvedBindings, subcomponentNames.get(resolvedBindings.key())));

      case MULTIBOUND_SET:
        return Optional.of(
            new SetBindingExpression(resolvedBindings, graph, this, types, elements));

      case MULTIBOUND_MAP:
        return Optional.of(
            new MapBindingExpression(resolvedBindings, graph, this, types, elements));

      case OPTIONAL:
        return Optional.of(new OptionalBindingExpression(resolvedBindings, this, types));

      case BOUND_INSTANCE:
        return Optional.of(
            new ComponentRequirementBindingExpression(
                resolvedBindings,
                ComponentRequirement.forBoundInstance(resolvedBindings.contributionBinding()),
                componentRequirementFields));

      case INJECTION:
      case PROVISION:
        return Optional.of(
            new SimpleMethodBindingExpression(
                resolvedBindings,
                compilerOptions,
                this,
                membersInjectionMethods,
                componentRequirementFields,
                elements));

      case MEMBERS_INJECTOR:
      case RELEASABLE_REFERENCE_MANAGER:
      case RELEASABLE_REFERENCE_MANAGERS:
        // TODO(dpb): Implement direct expressions for these.
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
   * In default mode, we always use the static factory creation strategy. In fastInit mode, we
   * prefer to use a SwitchingProvider instead of static factories in order to reduce class loading;
   * however, we allow static factories that can reused across multiple bindings, e.g.
   * {@code MapFactory} or {@code SetFactory}.
   */
  private boolean useStaticFactoryCreation(ContributionBinding binding) {
    return !(compilerOptions.experimentalAndroidMode2() || compilerOptions.fastInit())
        || binding.kind().equals(MULTIBOUND_MAP)
        || binding.kind().equals(MULTIBOUND_SET);
  }

  /**
   * Returns {@code true} if we can use a direct (not {@code Provider.get()}) expression for this
   * binding. If the binding doesn't {@linkplain #needsCaching(ResolvedBindings) need to be cached},
   * we can.
   *
   * <p>In fastInit mode, we can use a direct expression even if the binding {@linkplain
   * #needsCaching(ResolvedBindings) needs to be cached} as long as it's not in a
   * reference-releasing scope.
   */
  private boolean canUseDirectInstanceExpression(ResolvedBindings resolvedBindings) {
    return !needsCaching(resolvedBindings)
        || (compilerOptions.fastInit() && !requiresReleasableReferences(resolvedBindings));
  }

  /**
   * Returns a binding expression that uses a given one as the body of a method that users call. If
   * a component provision method matches it, it will be the method implemented. If not, a new
   * private method will be written.
   */
  private BindingExpression wrapInMethod(
      ResolvedBindings resolvedBindings,
      RequestKind requestKind,
      BindingExpression bindingExpression) {
    BindingMethodImplementation methodImplementation =
        methodImplementation(resolvedBindings, requestKind, bindingExpression);

    return findMatchingComponentMethod(resolvedBindings.key(), requestKind)
        .<BindingExpression>map(
            componentMethod ->
                new ComponentMethodBindingExpression(
                    methodImplementation, generatedComponentModel, componentMethod))
        .orElseGet(
            () ->
                new PrivateMethodBindingExpression(
                    resolvedBindings, requestKind, methodImplementation, generatedComponentModel));
  }

  /** Returns the first component method associated with this request kind, if one exists. */
  private Optional<ComponentMethodDescriptor> findMatchingComponentMethod(
      Key key, RequestKind requestKind) {
    return graph
        .componentDescriptor()
        .componentMethods()
        .stream()
        .filter(method -> doesComponentMethodMatch(method, key, requestKind))
        .findFirst();
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

  private BindingMethodImplementation methodImplementation(
      ResolvedBindings resolvedBindings,
      RequestKind requestKind,
      BindingExpression bindingExpression) {
    if (compilerOptions.fastInit()) {
      if (requestKind.equals(RequestKind.PROVIDER)) {
        return new SingleCheckedMethodImplementation(
            resolvedBindings, requestKind, bindingExpression, types, generatedComponentModel);
      } else if (requestKind.equals(RequestKind.INSTANCE) && needsCaching(resolvedBindings)) {
        return resolvedBindings.scope().get().isReusable()
            ? new SingleCheckedMethodImplementation(
                resolvedBindings, requestKind, bindingExpression, types, generatedComponentModel)
            : new DoubleCheckedMethodImplementation(
                resolvedBindings, requestKind, bindingExpression, types, generatedComponentModel);
      }
    }

    return new BindingMethodImplementation(
        resolvedBindings, requestKind, bindingExpression, generatedComponentModel.name(), types);
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

  // TODO(user): Enable releasable references in fastInit
  private boolean requiresReleasableReferences(ResolvedBindings resolvedBindings) {
    return resolvedBindings.scope().isPresent()
        && referenceReleasingManagerFields.requiresReleasableReferences(
            resolvedBindings.scope().get());
  }
}

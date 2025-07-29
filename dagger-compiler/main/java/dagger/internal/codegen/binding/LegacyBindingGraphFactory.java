/*
 * Copyright (C) 2014 The Dagger Authors.
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

package dagger.internal.codegen.binding;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.base.Util.reentrantComputeIfAbsent;
import static dagger.internal.codegen.binding.AssistedInjectionAnnotations.isAssistedFactoryType;
import static dagger.internal.codegen.extension.DaggerCollectors.onlyElement;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.model.BindingKind.ASSISTED_INJECTION;
import static dagger.internal.codegen.model.BindingKind.DELEGATE;
import static dagger.internal.codegen.model.BindingKind.INJECTION;
import static dagger.internal.codegen.model.BindingKind.OPTIONAL;
import static dagger.internal.codegen.model.BindingKind.SUBCOMPONENT_CREATOR;
import static dagger.internal.codegen.model.RequestKind.MEMBERS_INJECTION;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static dagger.internal.codegen.xprocessing.XTypes.isTypeOf;
import static java.util.function.Predicate.isEqual;

import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import dagger.Reusable;
import dagger.internal.codegen.base.ContributionType;
import dagger.internal.codegen.base.Keys;
import dagger.internal.codegen.base.MapType;
import dagger.internal.codegen.base.SetType;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.model.BindingGraph.ComponentNode;
import dagger.internal.codegen.model.BindingKind;
import dagger.internal.codegen.model.ComponentPath;
import dagger.internal.codegen.model.DaggerTypeElement;
import dagger.internal.codegen.model.DependencyRequest;
import dagger.internal.codegen.model.Key;
import dagger.internal.codegen.model.RequestKind;
import dagger.internal.codegen.model.Scope;
import dagger.internal.codegen.xprocessing.XTypeNames;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import javax.inject.Inject;

/** A factory for {@link BindingGraph} objects. */
public final class LegacyBindingGraphFactory {

  static boolean useLegacyBindingGraphFactory(
      CompilerOptions compilerOptions, ComponentDescriptor componentDescriptor) {
    return !compilerOptions.useBindingGraphFix();
  }

  static boolean hasStrictMultibindingsExemption(
      CompilerOptions compilerOptions, ContributionBinding binding) {
    // We only give the exemption to multibound map contributions.
    if (!binding.contributionType().equals(ContributionType.MAP)) {
      return false;
    }
    return !compilerOptions.strictMultibindingValidation();
  }

  private final InjectBindingRegistry injectBindingRegistry;
  private final KeyFactory keyFactory;
  private final BindingFactory bindingFactory;
  private final BindingNode.Factory bindingNodeFactory;
  private final ComponentDeclarations.Factory componentDeclarationsFactory;
  private final LegacyBindingGraphConverter legacyBindingGraphConverter;
  private final CompilerOptions compilerOptions;

  @Inject
  LegacyBindingGraphFactory(
      InjectBindingRegistry injectBindingRegistry,
      KeyFactory keyFactory,
      BindingFactory bindingFactory,
      BindingNode.Factory bindingNodeFactory,
      ComponentDeclarations.Factory componentDeclarationsFactory,
      LegacyBindingGraphConverter legacyBindingGraphConverter,
      CompilerOptions compilerOptions) {
    this.injectBindingRegistry = injectBindingRegistry;
    this.keyFactory = keyFactory;
    this.bindingFactory = bindingFactory;
    this.bindingNodeFactory = bindingNodeFactory;
    this.componentDeclarationsFactory = componentDeclarationsFactory;
    this.legacyBindingGraphConverter = legacyBindingGraphConverter;
    this.compilerOptions = compilerOptions;
  }

  /**
   * Creates a binding graph for a component.
   *
   * @param createFullBindingGraph if {@code true}, the binding graph will include all bindings;
   *     otherwise it will include only bindings reachable from at least one entry point
   */
  public BindingGraph create(
      ComponentDescriptor componentDescriptor, boolean createFullBindingGraph) {
    return legacyBindingGraphConverter.convert(
        createLegacyBindingGraph(Optional.empty(), componentDescriptor, createFullBindingGraph),
        createFullBindingGraph);
  }

  private LegacyBindingGraph createLegacyBindingGraph(
      Optional<Resolver> parentResolver,
      ComponentDescriptor componentDescriptor,
      boolean createFullBindingGraph) {
    Resolver requestResolver = new Resolver(parentResolver, componentDescriptor);

    componentDescriptor.entryPointMethods().stream()
        .map(method -> method.dependencyRequest().get())
        .forEach(
            entryPoint -> {
              if (entryPoint.kind().equals(MEMBERS_INJECTION)) {
                requestResolver.resolveMembersInjection(entryPoint.key());
              } else {
                requestResolver.resolve(entryPoint.key());
              }
            });

    if (createFullBindingGraph) {
      // Resolve the keys for all bindings in all modules, stripping any multibinding contribution
      // identifier so that the multibinding itself is resolved.
      requestResolver.declarations.allDeclarations().stream()
          // TODO(b/349155899): Consider resolving all declarations in full binding graph mode, not
          //   just those from modules.
          .filter(declaration -> declaration.contributingModule().isPresent())
          .map(Declaration::key)
          .map(Key::withoutMultibindingContributionIdentifier)
          .forEach(requestResolver::resolve);
    }

    // Resolve all bindings for subcomponents, creating subgraphs for all subcomponents that have
    // been detected during binding resolution. If a binding for a subcomponent is never resolved,
    // no BindingGraph will be created for it and no implementation will be generated. This is
    // done in a queue since resolving one subcomponent might resolve a key for a subcomponent
    // from a parent graph. This is done until no more new subcomponents are resolved.
    Set<ComponentDescriptor> resolvedSubcomponents = new HashSet<>();
    ImmutableList.Builder<LegacyBindingGraph> subgraphs = ImmutableList.builder();
    for (ComponentDescriptor subcomponent :
        Iterables.consumingIterable(requestResolver.subcomponentsToResolve)) {
      if (resolvedSubcomponents.add(subcomponent)) {
        subgraphs.add(
            createLegacyBindingGraph(
                Optional.of(requestResolver), subcomponent, createFullBindingGraph));
      }
    }

    return new LegacyBindingGraph(requestResolver, subgraphs.build());
  }

  /** Represents a fully resolved binding graph. */
  static final class LegacyBindingGraph {
    private final Resolver resolver;
    private final ImmutableList<LegacyBindingGraph> resolvedSubgraphs;
    private final ComponentNode componentNode;

    LegacyBindingGraph(Resolver resolver, ImmutableList<LegacyBindingGraph> resolvedSubgraphs) {
      this.resolver = resolver;
      this.resolvedSubgraphs = resolvedSubgraphs;
      this.componentNode =
          ComponentNodeImpl.create(resolver.componentPath, resolver.componentDescriptor);
    }

    /** Returns the {@link ComponentNode} associated with this binding graph. */
    public ComponentNode componentNode() {
      return componentNode;
    }

    /** Returns the {@link ComponentPath} associated with this binding graph. */
    public ComponentPath componentPath() {
      return resolver.componentPath;
    }

    /** Returns the {@link ComponentDescriptor} associated with this binding graph. */
    public ComponentDescriptor componentDescriptor() {
      return resolver.componentDescriptor;
    }

    /**
     * Returns the {@link LegacyResolvedBindings} in this graph or a parent graph that matches the
     * given request.
     *
     * <p>An exception is thrown if there are no resolved bindings found for the request; however,
     * this should never happen since all dependencies should have been resolved at this point.
     */
    public LegacyResolvedBindings resolvedBindings(BindingRequest request) {
      return request.isRequestKind(RequestKind.MEMBERS_INJECTION)
          ? resolver.getResolvedMembersInjectionBindings(request.key())
          : resolver.getResolvedContributionBindings(request.key());
    }

    /**
     * Returns all {@link LegacyResolvedBindings} for the given request.
     *
     * <p>Note that this only returns the bindings resolved in this component. Bindings resolved in
     * parent components are not included.
     */
    public Iterable<LegacyResolvedBindings> resolvedBindings() {
      // Don't return an immutable collection - this is only ever used for looping over all bindings
      // in the graph. Copying is wasteful, especially if is a hashing collection, since the values
      // should all, by definition, be distinct.
      return Iterables.concat(
          resolver.resolvedMembersInjectionBindings.values(),
          resolver.resolvedContributionBindings.values());
    }

    /** Returns the resolved subgraphs. */
    public ImmutableList<LegacyBindingGraph> subgraphs() {
      return resolvedSubgraphs;
    }
  }

  /**
   * The collection of bindings that have been resolved for a key. For valid graphs, contains
   * exactly one binding.
   *
   * <p>Separate {@link LegacyResolvedBindings} instances should be used if a {@link
   * MembersInjectionBinding} and a {@link ProvisionBinding} for the same key exist in the same
   * component. (This will only happen if a type has an {@code @Inject} constructor and members, the
   * component has a members injection method, and the type is also requested normally.)
   */
  @AutoValue
  abstract static class LegacyResolvedBindings {
    /**
     * Creates a {@link LegacyResolvedBindings} appropriate for when there are no bindings for a
     * key.
     */
    static LegacyResolvedBindings create(Key key) {
      return create(key, ImmutableSet.of());
    }

    /** Creates a {@link LegacyResolvedBindings} for a single binding. */
    static LegacyResolvedBindings create(Key key, BindingNode bindingNode) {
      return create(key, ImmutableSet.of(bindingNode));
    }

    /** Creates a {@link LegacyResolvedBindings} for multiple bindings. */
    static LegacyResolvedBindings create(Key key, ImmutableSet<BindingNode> bindingNodes) {
      return new AutoValue_LegacyBindingGraphFactory_LegacyResolvedBindings(key, bindingNodes);
    }

    /** The binding key for which the {@link #bindings()} have been resolved. */
    abstract Key key();

    /** All binding nodes for {@link #key()}, regardless of which component owns them. */
    abstract ImmutableSet<BindingNode> bindingNodes();

    // Computing the hash code is an expensive operation.
    @Memoized
    @Override
    public abstract int hashCode();

    // Suppresses ErrorProne warning that hashCode was overridden w/o equals
    @Override
    public abstract boolean equals(Object other);

    /** All bindings for {@link #key()}, regardless of which component owns them. */
    final ImmutableSet<Binding> bindings() {
      return bindingNodes().stream()
          .map(BindingNode::delegate)
          .collect(toImmutableSet());
    }

    /** Returns {@code true} if there are no {@link #bindings()}. */
    final boolean isEmpty() {
      return bindingNodes().isEmpty();
    }

    /** All bindings for {@link #key()} that are owned by a component. */
    ImmutableSet<BindingNode> bindingNodesOwnedBy(ComponentPath componentPath) {
      return bindingNodes().stream()
          .filter(bindingNode -> bindingNode.componentPath().equals(componentPath))
          .collect(toImmutableSet());
    }

    /** Returns the binding node representing the given binding, or throws ISE if none exist. */
    final BindingNode forBinding(Binding binding) {
      return bindingNodes().stream()
          .filter(bindingNode -> bindingNode.delegate().equals(binding))
          .collect(onlyElement());
    }
  }

  private final class Resolver {
    final ComponentPath componentPath;
    final Optional<Resolver> parentResolver;
    final ComponentDescriptor componentDescriptor;
    final ComponentDeclarations declarations;
    final Map<Key, LegacyResolvedBindings> resolvedContributionBindings = new LinkedHashMap<>();
    final Map<Key, LegacyResolvedBindings> resolvedMembersInjectionBindings = new LinkedHashMap<>();
    final Deque<Key> cycleStack = new ArrayDeque<>();
    final Map<Key, Boolean> keyDependsOnLocalBindingsCache = new HashMap<>();
    final Map<Binding, Boolean> bindingDependsOnLocalBindingsCache = new HashMap<>();
    final Queue<ComponentDescriptor> subcomponentsToResolve = new ArrayDeque<>();

    Resolver(Optional<Resolver> parentResolver, ComponentDescriptor componentDescriptor) {
      this.parentResolver = parentResolver;
      this.componentDescriptor = checkNotNull(componentDescriptor);
      DaggerTypeElement componentType = DaggerTypeElement.from(componentDescriptor.typeElement());
      componentPath =
          parentResolver.isPresent()
              ? parentResolver.get().componentPath.childPath(componentType)
              : ComponentPath.create(ImmutableList.of(componentType));
      declarations =
          componentDeclarationsFactory.create(
              parentResolver.map(parent -> parent.componentDescriptor),
              componentDescriptor);
      subcomponentsToResolve.addAll(
          componentDescriptor.childComponentsDeclaredByFactoryMethods().values());
      subcomponentsToResolve.addAll(
          componentDescriptor.childComponentsDeclaredByBuilderEntryPoints().values());
    }

    /**
     * Returns the resolved contribution bindings for the given {@link Key}:
     *
     * <ul>
     *   <li>All explicit bindings for:
     *       <ul>
     *         <li>the requested key
     *         <li>{@code Set<T>} if the requested key's type is {@code Set<Produced<T>>}
     *         <li>{@code Map<K, Provider<V>>} if the requested key's type is {@code Map<K,
     *             Producer<V>>}.
     *       </ul>
     *   <li>An implicit {@link Inject @Inject}-annotated constructor binding if there is one and
     *       there are no explicit bindings or synthetic bindings.
     * </ul>
     */
    LegacyResolvedBindings lookUpBindings(Key requestKey) {
      Set<ContributionBinding> bindings = new LinkedHashSet<>();
      Set<ContributionBinding> multibindingContributions = new LinkedHashSet<>();
      Set<MultibindingDeclaration> multibindingDeclarations = new LinkedHashSet<>();
      Set<OptionalBindingDeclaration> optionalBindingDeclarations = new LinkedHashSet<>();
      Set<SubcomponentDeclaration> subcomponentDeclarations = new LinkedHashSet<>();

      // Gather all bindings, multibindings, optional, and subcomponent declarations/contributions.
      for (Resolver resolver : getResolverLineage()) {
        bindings.addAll(resolver.getLocalExplicitBindings(requestKey));
        multibindingContributions.addAll(resolver.getLocalMultibindingContributions(requestKey));
        multibindingDeclarations.addAll(resolver.declarations.multibindings(requestKey));
        subcomponentDeclarations.addAll(resolver.declarations.subcomponents(requestKey));
        // The optional binding declarations are keyed by the unwrapped type.
        keyFactory.unwrapOptional(requestKey)
            .map(resolver.declarations::optionalBindings)
            .ifPresent(optionalBindingDeclarations::addAll);
      }

      // Add synthetic multibinding
      if (!multibindingContributions.isEmpty() || !multibindingDeclarations.isEmpty()) {
        if (MapType.isMap(requestKey)) {
          bindings.add(bindingFactory.multiboundMap(requestKey, multibindingContributions));
        } else if (SetType.isSet(requestKey)) {
          bindings.add(bindingFactory.multiboundSet(requestKey, multibindingContributions));
        } else {
          throw new AssertionError("Unexpected type in multibinding key: " + requestKey);
        }
      }

      // Add synthetic optional binding
      if (!optionalBindingDeclarations.isEmpty()) {
        ImmutableSet<Binding> optionalContributions =
            lookUpBindings(keyFactory.unwrapOptional(requestKey).get()).bindings();
        bindings.add(
            optionalContributions.isEmpty()
                ? bindingFactory.syntheticAbsentOptionalDeclaration(requestKey)
                : bindingFactory.syntheticPresentOptionalDeclaration(
                    requestKey, optionalContributions));
      }

      // Add subcomponent creator binding
      if (!subcomponentDeclarations.isEmpty()) {
        ContributionBinding binding =
            bindingFactory.subcomponentCreatorBinding(
                ImmutableSet.copyOf(subcomponentDeclarations));
        bindings.add(binding);
        addSubcomponentToOwningResolver(binding);
      }

      // Add members injector binding
      if (isTypeOf(requestKey.type().xprocessing(), XTypeNames.MEMBERS_INJECTOR)) {
        injectBindingRegistry.getOrFindMembersInjectorBinding(requestKey).ifPresent(bindings::add);
      }

      // Add Assisted Factory binding
      if (isDeclared(requestKey.type().xprocessing())
          && isAssistedFactoryType(requestKey.type().xprocessing().getTypeElement())) {
        bindings.add(
            bindingFactory.assistedFactoryBinding(
                requestKey.type().xprocessing().getTypeElement(),
                Optional.of(requestKey.type().xprocessing())));
      }

      // If there are no bindings, add the implicit @Inject-constructed binding if there is one.
      if (bindings.isEmpty()) {
        injectBindingRegistry
            .getOrFindInjectionBinding(requestKey)
            .filter(this::isCorrectlyScopedInSubcomponent)
            .ifPresent(bindings::add);
      }

      return LegacyResolvedBindings.create(
          requestKey,
          bindings.stream()
              .map(
                  binding -> {
                    Optional<BindingNode> bindingNodeOwnedByAncestor =
                        getBindingNodeOwnedByAncestor(requestKey, binding);
                    // If a binding is owned by an ancestor we use the corresponding BindingNode
                    // instance directly rather than creating a new instance to avoid accidentally
                    // including additional multi/optional/subcomponent declarations that don't
                    // exist in the ancestor's BindingNode instance.
                    return bindingNodeOwnedByAncestor.isPresent()
                          ? bindingNodeOwnedByAncestor.get()
                          : bindingNodeFactory.forContributionBindings(
                              componentPath,
                              binding,
                              multibindingDeclarations,
                              optionalBindingDeclarations,
                              subcomponentDeclarations);
                  })
              .collect(toImmutableSet()));
    }

    /**
     * Returns true if this binding graph resolution is for a subcomponent and the {@code @Inject}
     * binding's scope correctly matches one of the components in the current component ancestry.
     * If not, it means the binding is not owned by any of the currently known components, and will
     * be owned by a future ancestor (or, if never owned, will result in an incompatibly scoped
     * binding error at the root component).
     */
    private boolean isCorrectlyScopedInSubcomponent(ContributionBinding binding) {
      checkArgument(binding.kind() == INJECTION || binding.kind() == ASSISTED_INJECTION);
      if (!rootComponent().isSubcomponent()
          || !binding.scope().isPresent()
          || binding.scope().get().isReusable()) {
        return true;
      }

      Resolver owningResolver = getOwningResolver(binding).orElse(this);
      ComponentDescriptor owningComponent = owningResolver.componentDescriptor;
      return owningComponent.scopes().contains(binding.scope().get());
    }

    private ComponentDescriptor rootComponent() {
      return parentResolver.map(Resolver::rootComponent).orElse(componentDescriptor);
    }

    /** Returns the resolved members injection bindings for the given {@link Key}. */
    LegacyResolvedBindings lookUpMembersInjectionBinding(Key requestKey) {
      // no explicit deps for members injection, so just look it up
      Optional<MembersInjectionBinding> binding =
          injectBindingRegistry.getOrFindMembersInjectionBinding(requestKey);
      return binding.isPresent()
          ? LegacyResolvedBindings.create(
              requestKey,
              bindingNodeFactory.forMembersInjectionBinding(componentPath, binding.get()))
          : LegacyResolvedBindings.create(requestKey);
    }

    /**
     * When a binding is resolved for a {@link SubcomponentDeclaration}, adds corresponding {@link
     * ComponentDescriptor subcomponent} to a queue in the owning component's resolver. The queue
     * will be used to detect which subcomponents need to be resolved.
     */
    private void addSubcomponentToOwningResolver(ContributionBinding subcomponentCreatorBinding) {
      checkArgument(subcomponentCreatorBinding.kind().equals(SUBCOMPONENT_CREATOR));
      Resolver owningResolver = getOwningResolver(subcomponentCreatorBinding).get();

      XTypeElement builderType =
          subcomponentCreatorBinding.key().type().xprocessing().getTypeElement();
      owningResolver.subcomponentsToResolve.add(
          owningResolver.componentDescriptor.getChildComponentWithBuilderType(builderType));
    }

    private ImmutableSet<ContributionBinding> createDelegateBindings(
        ImmutableSet<DelegateDeclaration> delegateDeclarations) {
      ImmutableSet.Builder<ContributionBinding> builder = ImmutableSet.builder();
      for (DelegateDeclaration delegateDeclaration : delegateDeclarations) {
        builder.add(createDelegateBinding(delegateDeclaration));
      }
      return builder.build();
    }

    /**
     * Creates one (and only one) delegate binding for a delegate declaration, based on the resolved
     * bindings of the right-hand-side of a {@link dagger.Binds} method. If there are duplicate
     * bindings for the dependency key, there should still be only one binding for the delegate key.
     */
    private ContributionBinding createDelegateBinding(DelegateDeclaration delegateDeclaration) {
      Key delegateKey = delegateDeclaration.delegateRequest().key();
      if (cycleStack.contains(delegateKey)) {
        return bindingFactory.unresolvedDelegateBinding(delegateDeclaration);
      }

      LegacyResolvedBindings resolvedDelegate;
      try {
        cycleStack.push(delegateKey);
        resolvedDelegate = lookUpBindings(delegateKey);
      } finally {
        cycleStack.pop();
      }
      if (resolvedDelegate.bindings().isEmpty()) {
        // This is guaranteed to result in a missing binding error, so it doesn't matter if the
        // binding is a Provision or Production, except if it is a @IntoMap method, in which
        // case the key will be of type Map<K, Provider<V>>, which will be "upgraded" into a
        // Map<K, Producer<V>> if it's requested in a ProductionComponent. This may result in a
        // strange error, that the RHS needs to be provided with an @Inject or @Provides
        // annotated method, but a user should be able to figure out if a @Produces annotation
        // is needed.
        // TODO(gak): revisit how we model missing delegates if/when we clean up how we model
        // binding declarations
        return bindingFactory.unresolvedDelegateBinding(delegateDeclaration);
      }
      // It doesn't matter which of these is selected, since they will later on produce a
      // duplicate binding error.
      ContributionBinding explicitDelegate =
          (ContributionBinding) resolvedDelegate.bindings().iterator().next();
      return bindingFactory.delegateBinding(delegateDeclaration, explicitDelegate);
    }

    /**
     * Returns a {@link BindingNode} for the given binding that is owned by an ancestor component,
     * if one exists. Otherwise returns {@link Optional#empty()}.
     */
    private Optional<BindingNode> getBindingNodeOwnedByAncestor(
        Key requestKey, ContributionBinding binding) {
      if (canBeResolvedInParent(requestKey, binding)) {
        // Resolve in the parent to make sure we have the most recent multi/optional contributions.
        parentResolver.get().resolve(requestKey);
        if (!requiresResolution(binding)) {
          return Optional.of(getPreviouslyResolvedBindings(requestKey).get().forBinding(binding));
        }
      }
      return Optional.empty();
    }

    private boolean canBeResolvedInParent(Key requestKey, ContributionBinding binding) {
      if (parentResolver.isEmpty()) {
        return false;
      }
      Optional<Resolver> owningResolver = getOwningResolver(binding);
      if (owningResolver.isPresent()) {
        return !owningResolver.get().equals(this);
      }
      return !Keys.isComponentOrCreator(requestKey)
          // TODO(b/305748522): Allow caching for assisted injection bindings.
          && binding.kind() != BindingKind.ASSISTED_INJECTION
          && getPreviouslyResolvedBindings(requestKey).isPresent()
          && getPreviouslyResolvedBindings(requestKey).get().bindings().contains(binding);
    }

    private Optional<Resolver> getOwningResolver(ContributionBinding binding) {
      // TODO(ronshapiro): extract the different pieces of this method into their own methods
      if ((binding.scope().isPresent() && binding.scope().get().isProductionScope())
          || binding.kind().equals(BindingKind.PRODUCTION)) {
        for (Resolver requestResolver : getResolverLineage()) {
          // Resolve @Inject @ProductionScope bindings at the highest production component.
          if (binding.kind().equals(INJECTION)
              && requestResolver.componentDescriptor.isProduction()) {
            return Optional.of(requestResolver);
          }

          // Resolve explicit @Produces and @ProductionScope bindings at the highest component that
          // installs the binding.
          if (requestResolver.containsExplicitBinding(binding)) {
            return Optional.of(requestResolver);
          }
        }
      }

      if (binding.scope().isPresent() && binding.scope().get().isReusable()) {
        for (Resolver requestResolver : getResolverLineage().reverse()) {
          // If a @Reusable binding was resolved in an ancestor, use that component.
          LegacyResolvedBindings resolvedBindings =
              requestResolver.resolvedContributionBindings.get(binding.key());
          if (resolvedBindings != null && resolvedBindings.bindings().contains(binding)) {
            return Optional.of(requestResolver);
          }
        }
        // If a @Reusable binding was not resolved in any ancestor, resolve it here.
        return Optional.empty();
      }

      // TODO(b/359893922): we currently iterate from child to parent to find an owning resolver,
      // but we probably want to iterate from parent to child to catch missing bindings in
      // misconfigured repeated modules.
      for (Resolver requestResolver : getResolverLineage().reverse()) {
        if (requestResolver.containsExplicitBinding(binding)) {
          return Optional.of(requestResolver);
        }
      }

      // look for scope separately.  we do this for the case where @Singleton can appear twice
      // in the † compatibility mode
      Optional<Scope> bindingScope = binding.scope();
      if (bindingScope.isPresent()) {
        for (Resolver requestResolver : getResolverLineage().reverse()) {
          if (requestResolver.componentDescriptor.scopes().contains(bindingScope.get())) {
            return Optional.of(requestResolver);
          }
        }
      }
      return Optional.empty();
    }

    private boolean containsExplicitBinding(ContributionBinding binding) {
      return declarations.bindings(binding.key()).contains(binding)
          || resolverContainsDelegateDeclarationForBinding(binding)
          || !declarations.subcomponents(binding.key()).isEmpty();
    }

    /** Returns true if {@code binding} was installed in a module in this resolver's component. */
    private boolean resolverContainsDelegateDeclarationForBinding(ContributionBinding binding) {
      if (!binding.kind().equals(DELEGATE)) {
        return false;
      }
      if (hasStrictMultibindingsExemption(compilerOptions, binding)) {
        return false;
      }
      return declarations.delegates(binding.key()).stream()
          .anyMatch(
              declaration ->
                  declaration.contributingModule().equals(binding.contributingModule())
                  && declaration.bindingElement().equals(binding.bindingElement()));
    }

    /** Returns the resolver lineage from parent to child. */
    private ImmutableList<Resolver> getResolverLineage() {
      ImmutableList.Builder<Resolver> resolverList = ImmutableList.builder();
      for (Optional<Resolver> currentResolver = Optional.of(this);
          currentResolver.isPresent();
          currentResolver = currentResolver.get().parentResolver) {
        resolverList.add(currentResolver.get());
      }
      return resolverList.build().reverse();
    }

    /**
     * Returns the explicit {@link ContributionBinding}s that match the {@code key} from this
     * resolver.
     */
    private ImmutableSet<ContributionBinding> getLocalExplicitBindings(Key key) {
      return ImmutableSet.<ContributionBinding>builder()
          .addAll(declarations.bindings(key))
          .addAll(createDelegateBindings(declarations.delegates(key)))
          .build();
    }

    /**
     * Returns the explicit multibinding contributions that contribute to the map or set requested
     * by {@code key} from this resolver.
     */
    private ImmutableSet<ContributionBinding> getLocalMultibindingContributions(Key key) {
      return ImmutableSet.<ContributionBinding>builder()
          .addAll(declarations.multibindingContributions(key))
          .addAll(createDelegateBindings(declarations.delegateMultibindingContributions(key)))
          .build();
    }

    /**
     * Returns the {@link OptionalBindingDeclaration}s that match the {@code key} from this and all
     * ancestor resolvers.
     */
    private ImmutableSet<OptionalBindingDeclaration> getOptionalBindingDeclarations(Key key) {
      Optional<Key> unwrapped = keyFactory.unwrapOptional(key);
      if (unwrapped.isEmpty()) {
        return ImmutableSet.of();
      }
      ImmutableSet.Builder<OptionalBindingDeclaration> declarations = ImmutableSet.builder();
      for (Resolver resolver : getResolverLineage()) {
        declarations.addAll(resolver.declarations.optionalBindings(unwrapped.get()));
      }
      return declarations.build();
    }

    /**
     * Returns the {@link LegacyResolvedBindings} for {@code key} that was resolved in this resolver
     * or an ancestor resolver. Only checks for {@link ContributionBinding}s as {@link
     * MembersInjectionBinding}s are not inherited.
     */
    private Optional<LegacyResolvedBindings> getPreviouslyResolvedBindings(Key key) {
      Optional<LegacyResolvedBindings> result =
          Optional.ofNullable(resolvedContributionBindings.get(key));
      if (result.isPresent()) {
        return result;
      } else if (parentResolver.isPresent()) {
        return parentResolver.get().getPreviouslyResolvedBindings(key);
      } else {
        return Optional.empty();
      }
    }

    private void resolveMembersInjection(Key key) {
      LegacyResolvedBindings bindings = lookUpMembersInjectionBinding(key);
      resolveDependencies(bindings);
      resolvedMembersInjectionBindings.put(key, bindings);
    }

    void resolve(Key key) {
      // If we find a cycle, stop resolving. The original request will add it with all of the
      // other resolved deps.
      if (cycleStack.contains(key)) {
        return;
      }

      // If the binding was previously resolved in this (sub)component, don't resolve it again.
      if (resolvedContributionBindings.containsKey(key)) {
        return;
      }

      cycleStack.push(key);
      try {
        LegacyResolvedBindings bindings = lookUpBindings(key);
        resolvedContributionBindings.put(key, bindings);
        resolveDependencies(bindings);
      } finally {
        cycleStack.pop();
      }
    }

    /**
     * {@link #resolve(Key) Resolves} each of the dependencies of the bindings owned by this
     * component.
     */
    private void resolveDependencies(LegacyResolvedBindings resolvedBindings) {
      for (BindingNode binding : resolvedBindings.bindingNodesOwnedBy(componentPath)) {
        for (DependencyRequest dependency : binding.dependencies()) {
          resolve(dependency.key());
        }
      }
    }

    private LegacyResolvedBindings getResolvedContributionBindings(Key key) {
      if (resolvedContributionBindings.containsKey(key)) {
        return resolvedContributionBindings.get(key);
      }
      if (parentResolver.isPresent()) {
        return parentResolver.get().getResolvedContributionBindings(key);
      }
      throw new AssertionError("No resolved bindings for key: " + key);
    }

    private LegacyResolvedBindings getResolvedMembersInjectionBindings(Key key) {
      return resolvedMembersInjectionBindings.get(key);
    }

    private boolean requiresResolution(Binding binding) {
      return new RequiresResolutionChecker().requiresResolution(binding);
    }

    private final class RequiresResolutionChecker {
      private final Set<Object> cycleChecker = new HashSet<>();

      /**
       * Returns {@code true} if any of the bindings resolved for {@code key} are multibindings with
       * contributions declared within this component's modules or optional bindings with present
       * values declared within this component's modules, or if any of its unscoped dependencies
       * depend on such bindings.
       *
       * <p>We don't care about scoped dependencies because they will never depend on bindings from
       * subcomponents.
       *
       * @throws IllegalArgumentException if {@link #getPreviouslyResolvedBindings(Key)} is empty
       */
      private boolean requiresResolution(Key key) {
        // Don't recur infinitely if there are valid cycles in the dependency graph.
        // http://b/23032377
        if (!cycleChecker.add(key)) {
          return false;
        }
        return reentrantComputeIfAbsent(
            keyDependsOnLocalBindingsCache, key, this::requiresResolutionUncached);
      }

      /**
       * Returns {@code true} if {@code binding} is unscoped (or has {@link Reusable @Reusable}
       * scope) and depends on multibindings with contributions declared within this component's
       * modules, or if any of its unscoped or {@link Reusable @Reusable} scoped dependencies depend
       * on such local multibindings.
       *
       * <p>We don't care about non-reusable scoped dependencies because they will never depend on
       * multibindings with contributions from subcomponents.
       */
      private boolean requiresResolution(Binding binding) {
        if (!cycleChecker.add(binding)) {
          return false;
        }
        return reentrantComputeIfAbsent(
            bindingDependsOnLocalBindingsCache, binding, this::requiresResolutionUncached);
      }

      private boolean requiresResolutionUncached(Key key) {
        checkArgument(
            getPreviouslyResolvedBindings(key).isPresent(),
            "no previously resolved bindings in %s for %s",
            Resolver.this,
            key);
        LegacyResolvedBindings previouslyResolvedBindings =
            getPreviouslyResolvedBindings(key).get();
        if (hasLocalBindings(previouslyResolvedBindings)) {
          return true;
        }

        for (Binding binding : previouslyResolvedBindings.bindings()) {
          if (requiresResolution(binding)) {
            return true;
          }
        }
        return false;
      }

      private boolean requiresResolutionUncached(Binding binding) {
        if ((!binding.scope().isPresent() || binding.scope().get().isReusable())
            // TODO(beder): Figure out what happens with production subcomponents.
            && !binding.kind().equals(BindingKind.PRODUCTION)) {
          for (DependencyRequest dependency : binding.dependencies()) {
            if (requiresResolution(dependency.key())) {
              return true;
            }
          }
        }
        return false;
      }
    }

    private boolean hasLocalBindings(LegacyResolvedBindings resolvedBindings) {
      return hasLocalMultibindingContributions(resolvedBindings.key())
          || hasLocalOptionalBindingContribution(resolvedBindings);
    }

    /**
     * Returns {@code true} if there is at least one multibinding contribution declared within
     * this component's modules that matches the key.
     */
    private boolean hasLocalMultibindingContributions(Key requestKey) {
      return !declarations.multibindingContributions(requestKey).isEmpty()
          || !declarations.delegateMultibindingContributions(requestKey).isEmpty();
    }

    /**
     * Returns {@code true} if there is a contribution in this component for an {@code
     * Optional<Foo>} key that has not been contributed in a parent.
     */
    private boolean hasLocalOptionalBindingContribution(LegacyResolvedBindings resolvedBindings) {
      return hasLocalOptionalBindingContribution(
          resolvedBindings.key(), resolvedBindings.bindings());
    }

    private boolean hasLocalOptionalBindingContribution(
          Key key, ImmutableSet<? extends Binding> previouslyResolvedBindings) {
      if (previouslyResolvedBindings.stream()
          .map(Binding::kind)
          .anyMatch(isEqual(OPTIONAL))) {
        return hasLocalExplicitBindings(keyFactory.unwrapOptional(key).get());
      } else {
        // If a parent contributes a @Provides Optional<Foo> binding and a child has a
        // @BindsOptionalOf Foo method, the two should conflict, even if there is no binding for
        // Foo on its own
        return !getOptionalBindingDeclarations(key).isEmpty();
      }
    }

    /**
     * Returns {@code true} if there is at least one explicit binding that matches the given key.
     */
    private boolean hasLocalExplicitBindings(Key requestKey) {
      return !declarations.bindings(requestKey).isEmpty()
          || !declarations.delegates(requestKey).isEmpty();
    }
  }
}

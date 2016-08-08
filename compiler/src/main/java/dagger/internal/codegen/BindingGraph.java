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

package dagger.internal.codegen;

import static com.google.auto.common.MoreElements.getAnnotationMirror;
import static com.google.auto.common.MoreElements.hasModifiers;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Predicates.in;
import static com.google.common.base.Predicates.not;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Iterables.isEmpty;
import static dagger.internal.codegen.BindingKey.contribution;
import static dagger.internal.codegen.ComponentDescriptor.Kind.PRODUCTION_COMPONENT;
import static dagger.internal.codegen.ComponentDescriptor.isComponentProductionMethod;
import static dagger.internal.codegen.ConfigurationAnnotations.getComponentDependencies;
import static dagger.internal.codegen.ContributionBinding.Kind.SYNTHETIC_MULTIBOUND_KINDS;
import static dagger.internal.codegen.ContributionBinding.Kind.SYNTHETIC_OPTIONAL_BINDING;
import static dagger.internal.codegen.Key.indexByKey;
import static dagger.internal.codegen.Scope.reusableScope;
import static javax.lang.model.element.Modifier.ABSTRACT;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.base.VerifyException;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeTraverser;
import dagger.Component;
import dagger.Reusable;
import dagger.Subcomponent;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.ContributionBinding.Kind;
import dagger.internal.codegen.Key.HasKey;
import dagger.producers.Produced;
import dagger.producers.Producer;
import dagger.producers.ProductionComponent;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

/**
 * The canonical representation of a full-resolved graph.
 *
 * @author Gregory Kick
 */
@AutoValue
abstract class BindingGraph {
  abstract ComponentDescriptor componentDescriptor();
  abstract ImmutableMap<BindingKey, ResolvedBindings> resolvedBindings();
  abstract ImmutableSet<BindingGraph> subgraphs();

  /**
   * Returns the set of modules that are owned by this graph regardless of whether or not any of
   * their bindings are used in this graph. For graphs representing top-level {@link Component
   * components}, this set will be the same as
   * {@linkplain ComponentDescriptor#transitiveModules the component's transitive modules}. For
   * {@linkplain Subcomponent subcomponents}, this set will be the transitive modules that are not
   * owned by any of their ancestors.
   */
  abstract ImmutableSet<ModuleDescriptor> ownedModules();

  ImmutableSet<TypeElement> ownedModuleTypes() {
    return FluentIterable.from(ownedModules()).transform(ModuleDescriptor::moduleElement).toSet();
  }

  private static final TreeTraverser<BindingGraph> SUBGRAPH_TRAVERSER =
      new TreeTraverser<BindingGraph>() {
        @Override
        public Iterable<BindingGraph> children(BindingGraph node) {
          return node.subgraphs();
        }
      };

  /**
   * The types for which the component needs instances.
   * <ul>
   * <li>component dependencies
   * <li>{@linkplain #ownedModules() owned modules} with concrete instance bindings that are used in
   *     the graph
   * </ul>
   */
  ImmutableSet<TypeElement> componentRequirements() {
    return SUBGRAPH_TRAVERSER
        .preOrderTraversal(this)
        .transformAndConcat(graph -> graph.resolvedBindings().values())
        .transformAndConcat(ResolvedBindings::contributionBindings)
        .filter(ContributionBinding::requiresModuleInstance)
        .transformAndConcat(bindingDeclaration -> bindingDeclaration.contributingModule().asSet())
        .filter(in(ownedModuleTypes()))
        .append(componentDescriptor().dependencies())
        .toSet();
  }
  /**
   * Returns the {@link ComponentDescriptor}s for this component and its subcomponents.
   */
  ImmutableSet<ComponentDescriptor> componentDescriptors() {
    return SUBGRAPH_TRAVERSER
        .preOrderTraversal(this)
        .transform(BindingGraph::componentDescriptor)
        .toSet();
  }

  ImmutableSet<TypeElement> availableDependencies() {
    return FluentIterable.from(componentDescriptor().transitiveModuleTypes())
        .filter(not(hasModifiers(ABSTRACT)))
        .append(componentDescriptor().dependencies())
        .toSet();
  }

  static final class Factory {
    private final Elements elements;
    private final InjectBindingRegistry injectBindingRegistry;
    private final Key.Factory keyFactory;
    private final ProvisionBinding.Factory provisionBindingFactory;
    private final ProductionBinding.Factory productionBindingFactory;

    Factory(
        Elements elements,
        InjectBindingRegistry injectBindingRegistry,
        Key.Factory keyFactory,
        ProvisionBinding.Factory provisionBindingFactory,
        ProductionBinding.Factory productionBindingFactory) {
      this.elements = elements;
      this.injectBindingRegistry = injectBindingRegistry;
      this.keyFactory = keyFactory;
      this.provisionBindingFactory = provisionBindingFactory;
      this.productionBindingFactory = productionBindingFactory;
    }

    BindingGraph create(ComponentDescriptor componentDescriptor) {
      return create(Optional.<Resolver>absent(), componentDescriptor);
    }

    private BindingGraph create(
        Optional<Resolver> parentResolver, ComponentDescriptor componentDescriptor) {
      ImmutableSet.Builder<ContributionBinding> explicitBindingsBuilder = ImmutableSet.builder();
      ImmutableSet.Builder<DelegateDeclaration> delegatesBuilder = ImmutableSet.builder();
      ImmutableSet.Builder<OptionalBindingDeclaration> optionalsBuilder = ImmutableSet.builder();

      // binding for the component itself
      TypeElement componentDefinitionType = componentDescriptor.componentDefinitionType();
      explicitBindingsBuilder.add(provisionBindingFactory.forComponent(componentDefinitionType));

      // Collect Component dependencies.
      Optional<AnnotationMirror> componentMirror =
          getAnnotationMirror(componentDefinitionType, Component.class)
              .or(getAnnotationMirror(componentDefinitionType, ProductionComponent.class));
      ImmutableSet<TypeElement> componentDependencyTypes = componentMirror.isPresent()
          ? MoreTypes.asTypeElements(getComponentDependencies(componentMirror.get()))
          : ImmutableSet.<TypeElement>of();
      for (TypeElement componentDependency : componentDependencyTypes) {
        explicitBindingsBuilder.add(provisionBindingFactory.forComponent(componentDependency));
      }
      ImmutableSet<ExecutableElement> dependencyMethods = componentDescriptor.dependencyMethodIndex().keySet();
      for (ExecutableElement dependencyMethod : dependencyMethods) {
        explicitBindingsBuilder.add(
                componentDescriptor.kind().equals(PRODUCTION_COMPONENT)
                        && isComponentProductionMethod(elements, dependencyMethod)
                    ? productionBindingFactory.forComponentMethod(dependencyMethod)
                    : provisionBindingFactory.forComponentMethod(dependencyMethod));
      }

      for (Map.Entry<ComponentMethodDescriptor, ComponentDescriptor>
          componentMethodAndSubcomponent :
              componentDescriptor.subcomponentsByBuilderMethod().entrySet()) {
        ComponentMethodDescriptor componentMethod = componentMethodAndSubcomponent.getKey();
        ComponentDescriptor subcomponentDescriptor = componentMethodAndSubcomponent.getValue();
        if (!componentDescriptor.subcomponentsFromModules().contains(subcomponentDescriptor)) {
          explicitBindingsBuilder.add(
              provisionBindingFactory.forSubcomponentBuilderMethod(
                  componentMethod.methodElement(),
                  componentDescriptor.componentDefinitionType()));
        }
      }

      ImmutableSet.Builder<MultibindingDeclaration> multibindingDeclarations =
          ImmutableSet.builder();
      ImmutableSet.Builder<SubcomponentDeclaration> subcomponentDeclarations =
          ImmutableSet.builder();

      // Collect transitive module bindings and multibinding declarations.
      for (ModuleDescriptor moduleDescriptor : componentDescriptor.transitiveModules()) {
        explicitBindingsBuilder.addAll(moduleDescriptor.bindings());
        multibindingDeclarations.addAll(moduleDescriptor.multibindingDeclarations());
        subcomponentDeclarations.addAll(moduleDescriptor.subcomponentDeclarations());
        delegatesBuilder.addAll(moduleDescriptor.delegateDeclarations());
        optionalsBuilder.addAll(moduleDescriptor.optionalDeclarations());
      }

      final Resolver requestResolver =
          new Resolver(
              parentResolver,
              componentDescriptor,
              indexByKey(explicitBindingsBuilder.build()),
              indexByKey(multibindingDeclarations.build()),
              indexByKey(subcomponentDeclarations.build()),
              indexByKey(delegatesBuilder.build()),
              indexByKey(optionalsBuilder.build()));
      for (ComponentMethodDescriptor componentMethod : componentDescriptor.componentMethods()) {
        Optional<DependencyRequest> componentMethodRequest = componentMethod.dependencyRequest();
        if (componentMethodRequest.isPresent()) {
          requestResolver.resolve(componentMethodRequest.get().bindingKey());
        }
      }

      // Resolve all bindings for subcomponents, creating subgraphs for all subcomponents that have
      // been detected during binding resolution. If a binding for a subcomponent is never resolved,
      // no BindingGraph will be created for it and no implementation will be generated. This is
      // done in a queue since resolving one subcomponent might resolve a key for a subcomponent
      // from a parent graph. This is done until no more new subcomponents are resolved.
      Set<ComponentDescriptor> resolvedSubcomponents = new HashSet<>();
      ImmutableSet.Builder<BindingGraph> subgraphs = ImmutableSet.builder();
      for (ComponentDescriptor subcomponent :
          Iterables.consumingIterable(requestResolver.subcomponentsToResolve)) {
        if (resolvedSubcomponents.add(subcomponent)) {
          subgraphs.add(create(Optional.of(requestResolver), subcomponent));
        }
      }

      for (ResolvedBindings resolvedBindings : requestResolver.getResolvedBindings().values()) {
        verify(
            resolvedBindings.owningComponent().equals(componentDescriptor),
            "%s is not owned by %s",
            resolvedBindings,
            componentDescriptor);
      }

      return new AutoValue_BindingGraph(
          componentDescriptor,
          requestResolver.getResolvedBindings(),
          subgraphs.build(),
          requestResolver.getOwnedModules());
    }

    private final class Resolver {
      final Optional<Resolver> parentResolver;
      final ComponentDescriptor componentDescriptor;
      final ImmutableSetMultimap<Key, ContributionBinding> explicitBindings;
      final ImmutableSet<ContributionBinding> explicitBindingsSet;
      final ImmutableSetMultimap<Key, ContributionBinding> explicitMultibindings;
      final ImmutableSetMultimap<Key, MultibindingDeclaration> multibindingDeclarations;
      final ImmutableSetMultimap<Key, SubcomponentDeclaration> subcomponentDeclarations;
      final ImmutableSetMultimap<Key, DelegateDeclaration> delegateDeclarations;
      final ImmutableSetMultimap<Key, OptionalBindingDeclaration> optionalBindingDeclarations;
      final ImmutableSetMultimap<Key, DelegateDeclaration> delegateMultibindingDeclarations;
      final Map<BindingKey, ResolvedBindings> resolvedBindings;
      final Deque<BindingKey> cycleStack = new ArrayDeque<>();
      final Cache<BindingKey, Boolean> dependsOnLocalMultibindingsCache =
          CacheBuilder.newBuilder().build();
      final Cache<Binding, Boolean> bindingDependsOnLocalMultibindingsCache =
          CacheBuilder.newBuilder().build();
      final Queue<ComponentDescriptor> subcomponentsToResolve = new ArrayDeque<>();

      Resolver(
          Optional<Resolver> parentResolver,
          ComponentDescriptor componentDescriptor,
          ImmutableSetMultimap<Key, ContributionBinding> explicitBindings,
          ImmutableSetMultimap<Key, MultibindingDeclaration> multibindingDeclarations,
          ImmutableSetMultimap<Key, SubcomponentDeclaration> subcomponentDeclarations,
          ImmutableSetMultimap<Key, DelegateDeclaration> delegateDeclarations,
          ImmutableSetMultimap<Key, OptionalBindingDeclaration> optionalBindingDeclarations) {
        this.parentResolver = checkNotNull(parentResolver);
        this.componentDescriptor = checkNotNull(componentDescriptor);
        this.explicitBindings = checkNotNull(explicitBindings);
        this.explicitBindingsSet = ImmutableSet.copyOf(explicitBindings.values());
        this.multibindingDeclarations = checkNotNull(multibindingDeclarations);
        this.subcomponentDeclarations = checkNotNull(subcomponentDeclarations);
        this.delegateDeclarations = checkNotNull(delegateDeclarations);
        this.optionalBindingDeclarations = checkNotNull(optionalBindingDeclarations);
        this.resolvedBindings = Maps.newLinkedHashMap();
        this.explicitMultibindings =
            multibindingContributionsByMultibindingKey(explicitBindingsSet);
        this.delegateMultibindingDeclarations =
            multibindingContributionsByMultibindingKey(delegateDeclarations.values());
        subcomponentsToResolve.addAll(componentDescriptor.subcomponentsFromEntryPoints());
      }

      /**
       * Returns the bindings for the given {@link BindingKey}.
       *
       * <p>For {@link BindingKey.Kind#CONTRIBUTION} requests, returns all of:
       *
       * <ul>
       * <li>All explicit bindings for:
       *     <ul>
       *     <li>the requested key
       *     <li>{@code Set<T>} if the requested key's type is {@code Set<Produced<T>>}
       *     <li>{@code Map<K, Provider<V>>} if the requested key's type is {@code Map<K,
       *         Producer<V>>}.
       *     </ul>
       *
       * <li>A synthetic binding that depends on {@code Map<K, Producer<V>>} if the requested key's
       *     type is {@code Map<K, V>} and there are some explicit bindings for {@code Map<K,
       *     Producer<V>>}.
       * <li>A synthetic binding that depends on {@code Map<K, Provider<V>>} if the requested key's
       *     type is {@code Map<K, V>} and there are some explicit bindings for {@code Map<K,
       *     Provider<V>>} but no explicit bindings for {@code Map<K, Producer<V>>}.
       * <li>An implicit {@link Inject @Inject}-annotated constructor binding if there is one and
       *     there are no explicit bindings or synthetic bindings.
       * </ul>
       *
       * <p>For {@link BindingKey.Kind#MEMBERS_INJECTION} requests, returns the {@link
       * MembersInjectionBinding} for the type.
       */
      ResolvedBindings lookUpBindings(BindingKey bindingKey) {
        Key requestKey = bindingKey.key();
        switch (bindingKey.kind()) {
          case CONTRIBUTION:
            Set<ContributionBinding> contributionBindings = new LinkedHashSet<>();
            ImmutableSet.Builder<ContributionBinding> multibindingContributionsBuilder =
                ImmutableSet.builder();
            ImmutableSet.Builder<MultibindingDeclaration> multibindingDeclarationsBuilder =
                ImmutableSet.builder();
            ImmutableSet.Builder<SubcomponentDeclaration> subcomponentDeclarationsBuilder =
                ImmutableSet.builder();
            ImmutableSet.Builder<OptionalBindingDeclaration> optionalBindingDeclarationsBuilder =
                ImmutableSet.builder();

            for (Key key : keysMatchingRequest(requestKey)) {
              contributionBindings.addAll(getExplicitBindings(key));
              multibindingContributionsBuilder.addAll(getExplicitMultibindings(key));
              multibindingDeclarationsBuilder.addAll(getMultibindingDeclarations(key));
              subcomponentDeclarationsBuilder.addAll(getSubcomponentDeclarations(key));
              optionalBindingDeclarationsBuilder.addAll(getOptionalBindingDeclarations(key));
            }

            ImmutableSet<ContributionBinding> multibindingContributions =
                multibindingContributionsBuilder.build();
            ImmutableSet<MultibindingDeclaration> multibindingDeclarations =
                multibindingDeclarationsBuilder.build();
            ImmutableSet<SubcomponentDeclaration> subcomponentDeclarations =
                subcomponentDeclarationsBuilder.build();
            ImmutableSet<OptionalBindingDeclaration> optionalBindingDeclarations =
                optionalBindingDeclarationsBuilder.build();

            contributionBindings.addAll(syntheticMapOfValuesBinding(requestKey).asSet());
            contributionBindings.addAll(
                syntheticMultibinding(
                        requestKey, multibindingContributions, multibindingDeclarations)
                    .asSet());
            Optional<ProvisionBinding> subcomponentBuilderBinding =
                syntheticSubcomponentBuilderBinding(subcomponentDeclarations);
            if (subcomponentBuilderBinding.isPresent()) {
              contributionBindings.add(subcomponentBuilderBinding.get());
              addSubcomponentToOwningResolver(subcomponentBuilderBinding.get());
            }
            contributionBindings.addAll(
                syntheticOptionalBinding(requestKey, optionalBindingDeclarations).asSet());

            /* If there are no bindings, add the implicit @Inject-constructed binding if there is
             * one. */
            if (contributionBindings.isEmpty()) {
              contributionBindings.addAll(
                  injectBindingRegistry.getOrFindProvisionBinding(requestKey).asSet());
            }

            return ResolvedBindings.forContributionBindings(
                bindingKey,
                componentDescriptor,
                indexBindingsByOwningComponent(
                    bindingKey, ImmutableSet.copyOf(contributionBindings)),
                multibindingDeclarations,
                subcomponentDeclarations,
                optionalBindingDeclarations);

          case MEMBERS_INJECTION:
            // no explicit deps for members injection, so just look it up
            Optional<MembersInjectionBinding> binding =
                injectBindingRegistry.getOrFindMembersInjectionBinding(requestKey);
            return binding.isPresent()
                ? ResolvedBindings.forMembersInjectionBinding(
                    bindingKey, componentDescriptor, binding.get())
                : ResolvedBindings.noBindings(bindingKey, componentDescriptor);

          default:
            throw new AssertionError();
        }
      }

      /**
       * When a binding is resolved for a {@link SubcomponentDeclaration}, adds corresponding
       * {@link ComponentDescriptor subcomponent} to a queue in the owning component's resolver.
       * The queue will be used to detect which subcomponents need to be resolved.
       */
      private void addSubcomponentToOwningResolver(ProvisionBinding subcomponentBuilderBinding) {
        checkArgument(subcomponentBuilderBinding.bindingKind().equals(Kind.SUBCOMPONENT_BUILDER));
        Resolver owningResolver = getOwningResolver(subcomponentBuilderBinding).get();

        TypeElement builderType = MoreTypes.asTypeElement(subcomponentBuilderBinding.key().type());
        owningResolver.subcomponentsToResolve.add(
            owningResolver.componentDescriptor.subcomponentsByBuilderType().get(builderType));
      }

      private Iterable<Key> keysMatchingRequest(Key requestKey) {
        return ImmutableSet.<Key>builder()
            .add(requestKey)
            .addAll(keyFactory.unwrapSetKey(requestKey, Produced.class).asSet())
            .addAll(keyFactory.rewrapMapKey(requestKey, Producer.class, Provider.class).asSet())
            .addAll(keyFactory.rewrapMapKey(requestKey, Provider.class, Producer.class).asSet())
            .build();
      }

      /**
       * If {@code key} is a {@code Map<K, V>} or {@code Map<K, Produced<V>>}, and there are any
       * multibinding contributions or declarations that apply to that map, returns a synthetic
       * binding for the {@code key} that depends on an {@linkplain #syntheticMultibinding(Key,
       * Iterable, Iterable) underlying synthetic multibinding}.
       *
       * <p>The returned binding has the same {@link BindingType} as the underlying synthetic
       * multibinding.
       */
      private Optional<ContributionBinding> syntheticMapOfValuesBinding(final Key key) {
        return syntheticMultibinding(
                key,
                multibindingContributionsForValueMap(key),
                multibindingDeclarationsForValueMap(key))
            .transform(
                syntheticMultibinding -> {
                  switch (syntheticMultibinding.bindingType()) {
                    case PROVISION:
                      return provisionBindingFactory.syntheticMapOfValuesBinding(key);

                    case PRODUCTION:
                      return productionBindingFactory.syntheticMapOfValuesOrProducedBinding(key);

                    default:
                      throw new VerifyException(syntheticMultibinding.toString());
                  }
                });
      }

      /**
       * If {@code requestKey} is for {@code Map<K, V>} or {@code Map<K, Produced<V>>}, returns all
       * multibinding contributions whose key is for {@code Map<K, Provider<V>>} or {@code Map<K,
       * Producer<V>>} with the same qualifier and {@code K} and {@code V}.
       */
      private FluentIterable<ContributionBinding> multibindingContributionsForValueMap(
          Key requestKey) {
        return keyFactory
            .implicitFrameworkMapKeys(requestKey)
            .transformAndConcat(this::getExplicitMultibindings);
      }

      /**
       * If {@code requestKey} is for {@code Map<K, V>} or {@code Map<K, Produced<V>>}, returns all
       * multibinding declarations whose key is for {@code Map<K, Provider<V>>} or {@code Map<K,
       * Producer<V>>} with the same qualifier and {@code K} and {@code V}.
       */
      private FluentIterable<MultibindingDeclaration> multibindingDeclarationsForValueMap(
          Key requestKey) {
        return keyFactory
            .implicitFrameworkMapKeys(requestKey)
            .transformAndConcat(this::getMultibindingDeclarations);
      }

      /**
       * Returns a synthetic binding that depends on individual multibinding contributions.
       *
       * <p>If there are no {@code multibindingContributions} or {@code multibindingDeclarations},
       * returns {@link Optional#absent()}.
       *
       * <p>If there are production {@code multibindingContributions} or the request is for any of
       * the following types, returns a {@link ProductionBinding}.
       *
       * <ul>
       * <li>{@code Set<Produced<T>>}
       * <li>{@code Map<K, Producer<V>>}
       * <li>{@code Map<K, Produced<V>>}
       * </ul>
       *
       * Otherwise, returns a {@link ProvisionBinding}.
       */
      private Optional<? extends ContributionBinding> syntheticMultibinding(
          Key key,
          Iterable<ContributionBinding> multibindingContributions,
          Iterable<MultibindingDeclaration> multibindingDeclarations) {
        if (isEmpty(multibindingContributions) && isEmpty(multibindingDeclarations)) {
          return Optional.absent();
        } else if (multibindingsRequireProduction(multibindingContributions, key)) {
          return Optional.of(
              productionBindingFactory.syntheticMultibinding(key, multibindingContributions));
        } else {
          return Optional.of(
              provisionBindingFactory.syntheticMultibinding(key, multibindingContributions));
        }
      }

      private boolean multibindingsRequireProduction(
          Iterable<ContributionBinding> multibindingContributions, Key requestKey) {
        if (MapType.isMap(requestKey)) {
          MapType mapType = MapType.from(requestKey);
          if (mapType.valuesAreTypeOf(Producer.class) || mapType.valuesAreTypeOf(Produced.class)) {
            return true;
          }
        } else if (SetType.isSet(requestKey)
            && SetType.from(requestKey).elementsAreTypeOf(Produced.class)) {
          return true;
        }
        return Iterables.any(multibindingContributions,
            hasBindingType -> hasBindingType.bindingType().equals(BindingType.PRODUCTION));
      }

      private Optional<ProvisionBinding> syntheticSubcomponentBuilderBinding(
          ImmutableSet<SubcomponentDeclaration> subcomponentDeclarations) {
        return subcomponentDeclarations.isEmpty()
            ? Optional.<ProvisionBinding>absent()
            : Optional.of(
            provisionBindingFactory.syntheticSubcomponentBuilder(subcomponentDeclarations));
      }

      /**
       * Returns a synthetic binding for {@code @Qualifier Optional<Type>} if there are any {@code
       * optionalBindingDeclarations}.
       *
       * <p>If there are no bindings for the underlying key (the key for dependency requests for
       * {@code Type}), returns a provision binding that always returns {@link Optional#absent()}.
       *
       * <p>If there are any production bindings for the underlying key, returns a production
       * binding. Otherwise returns a provision binding.
       */
      private Optional<? extends ContributionBinding> syntheticOptionalBinding(
          Key key, ImmutableSet<OptionalBindingDeclaration> optionalBindingDeclarations) {
        if (optionalBindingDeclarations.isEmpty()) {
          return Optional.absent();
        }
        ResolvedBindings underlyingKeyBindings =
            lookUpBindings(contribution(keyFactory.unwrapOptional(key).get()));
        if (underlyingKeyBindings.isEmpty()) {
          return Optional.of(provisionBindingFactory.syntheticAbsentBinding(key));
        } else if (underlyingKeyBindings.bindingTypes().contains(BindingType.PRODUCTION)) {
          return Optional.of(productionBindingFactory.syntheticPresentBinding(key));
        } else {
          return Optional.of(provisionBindingFactory.syntheticPresentBinding(key));
        }
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
       * Creates one (and only one) delegate binding for a delegate declaration, based on the
       * resolved bindings of the right-hand-side of a {@link dagger.Binds} method. If there are
       * duplicate bindings for the dependency key, there should still be only one binding for the
       * delegate key.
       */
      private ContributionBinding createDelegateBinding(DelegateDeclaration delegateDeclaration) {
        BindingKey delegateBindingKey = delegateDeclaration.delegateRequest().bindingKey();

        if (cycleStack.contains(delegateBindingKey)) {
          return provisionBindingFactory.missingDelegate(delegateDeclaration);
        }

        ResolvedBindings resolvedDelegate;
        try {
          cycleStack.push(delegateBindingKey);
          resolvedDelegate = lookUpBindings(delegateBindingKey);
        } finally {
          cycleStack.pop();
        }
        if (resolvedDelegate.contributionBindings().isEmpty()) {
          // This is guaranteed to result in a missing binding error, so it doesn't matter if the
          // binding is a Provision or Production, except if it is a @IntoMap method, in which
          // case the key will be of type Map<K, Provider<V>>, which will be "upgraded" into a
          // Map<K, Producer<V>> if it's requested in a ProductionComponent. This may result in a
          // strange error, that the RHS needs to be provided with an @Inject or @Provides
          // annotated method, but a user should be able to figure out if a @Produces annotation
          // is needed.
          // TODO(gak): revisit how we model missing delegates if/when we clean up how we model
          // binding declarations
          return provisionBindingFactory.missingDelegate(delegateDeclaration);
        }
        // It doesn't matter which of these is selected, since they will later on produce a
        // duplicate binding error.
        // TODO(ronshapiro): Once compile-testing has a CompilationResult, add a test which asserts
        // that a duplicate binding for the RHS does not result in a duplicate binding for the LHS.
        ContributionBinding explicitDelegate =
            resolvedDelegate.contributionBindings().iterator().next();
        switch (explicitDelegate.bindingType()) {
          case PRODUCTION:
            return productionBindingFactory.delegate(
                delegateDeclaration, (ProductionBinding) explicitDelegate);
          case PROVISION:
            return provisionBindingFactory.delegate(
                delegateDeclaration, (ProvisionBinding) explicitDelegate);
          default:
            throw new AssertionError("bindingType: " + explicitDelegate);
        }
      }

      private ImmutableSetMultimap<ComponentDescriptor, ContributionBinding>
          indexBindingsByOwningComponent(
              BindingKey bindingKey, Iterable<? extends ContributionBinding> bindings) {
        ImmutableSetMultimap.Builder<ComponentDescriptor, ContributionBinding> index =
            ImmutableSetMultimap.builder();
        for (ContributionBinding binding : bindings) {
          index.put(getOwningComponent(bindingKey, binding), binding);
        }
        return index.build();
      }

      /**
       * Returns the component that should contain the framework field for {@code binding}.
       *
       * <p>If {@code binding} is either not bound in an ancestor component or depends on
       * multibinding contributions in this component, returns this component.
       *
       * <p>Otherwise, resolves {@code request} in this component's parent in order to resolve any
       * multibinding contributions in the parent, and returns the parent-resolved {@link
       * ResolvedBindings#owningComponent(ContributionBinding)}.
       */
      private ComponentDescriptor getOwningComponent(
          BindingKey bindingKey, ContributionBinding binding) {
        if (isResolvedInParent(bindingKey, binding)
            && !new MultibindingDependencies().dependsOnLocalMultibindings(binding)) {
          ResolvedBindings parentResolvedBindings =
              parentResolver.get().resolvedBindings.get(bindingKey);
          return parentResolvedBindings.owningComponent(binding);
        } else {
          return componentDescriptor;
        }
      }

      /**
       * Returns {@code true} if {@code binding} is owned by an ancestor. If so, {@linkplain
       * #resolve resolves} the {@link BindingKey} in this component's parent. Don't resolve
       * directly in the owning component in case it depends on multibindings in any of its
       * descendants.
       */
      private boolean isResolvedInParent(BindingKey bindingKey, ContributionBinding binding) {
        Optional<Resolver> owningResolver = getOwningResolver(binding);
        if (owningResolver.isPresent() && !owningResolver.get().equals(this)) {
          parentResolver.get().resolve(bindingKey);
          return true;
        } else {
          return false;
        }
      }

      private Optional<Resolver> getOwningResolver(ContributionBinding binding) {
        if (binding.scope().isPresent() && binding.scope().get().equals(reusableScope(elements))) {
          for (Resolver requestResolver : getResolverLineage().reverse()) {
            // If a @Reusable binding was resolved in an ancestor, use that component.
            if (requestResolver.resolvedBindings.containsKey(
                BindingKey.contribution(binding.key()))) {
              return Optional.of(requestResolver);
            }
          }
          // If a @Reusable binding was not resolved in any ancestor, resolve it here.
          return Optional.absent();
        }

        for (Resolver requestResolver : getResolverLineage().reverse()) {
          if (requestResolver.explicitBindingsSet.contains(binding)
              || requestResolver.subcomponentDeclarations.containsKey(binding.key())) {
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
        return Optional.absent();
      }

      /** Returns the resolver lineage from parent to child. */
      private ImmutableList<Resolver> getResolverLineage() {
        List<Resolver> resolverList = Lists.newArrayList();
        for (Optional<Resolver> currentResolver = Optional.of(this);
            currentResolver.isPresent();
            currentResolver = currentResolver.get().parentResolver) {
          resolverList.add(currentResolver.get());
        }
        return ImmutableList.copyOf(Lists.reverse(resolverList));
      }

      /**
       * Returns the explicit {@link ContributionBinding}s that match the {@code requestKey} from
       * this and all ancestor resolvers.
       */
      private ImmutableSet<ContributionBinding> getExplicitBindings(Key requestKey) {
        ImmutableSet.Builder<ContributionBinding> bindings = ImmutableSet.builder();
        for (Resolver resolver : getResolverLineage()) {
          bindings.addAll(resolver.getLocalExplicitBindings(requestKey));
        }
        return bindings.build();
      }

      /**
       * Returns the explicit {@link ContributionBinding}s that match the {@code requestKey} from
       * this resolver.
       */
      private ImmutableSet<ContributionBinding> getLocalExplicitBindings(Key requestKey) {
        return new ImmutableSet.Builder<ContributionBinding>()
            .addAll(explicitBindings.get(requestKey))
            .addAll(
                createDelegateBindings(
                    delegateDeclarations.get(keyFactory.convertToDelegateKey(requestKey))))
            .build();
      }

      /**
       * Returns the explicit multibinding contributions that contribute to the map or set requested
       * by {@code requestKey} from this and all ancestor resolvers.
       */
      private ImmutableSet<ContributionBinding> getExplicitMultibindings(Key requestKey) {
        ImmutableSet.Builder<ContributionBinding> multibindings = ImmutableSet.builder();
        Key delegateDeclarationKey = keyFactory.convertToDelegateKey(requestKey);
        for (Resolver resolver : getResolverLineage()) {
          multibindings.addAll(resolver.explicitMultibindings.get(requestKey));
          if (!MapType.isMap(requestKey) || MapType.from(requestKey).valuesAreFrameworkType()) {
            // There are no @Binds @IntoMap delegate declarations for Map<K, V> requests. All
            // @IntoMap requests must be for Map<K, Framework<V>>.
            multibindings.addAll(
                createDelegateBindings(
                    resolver.delegateMultibindingDeclarations.get(delegateDeclarationKey)));
          }
        }
        return multibindings.build();
      }

      /**
       * Returns the {@link MultibindingDeclaration}s that match the {@code key} from this and all
       * ancestor resolvers.
       */
      private ImmutableSet<MultibindingDeclaration> getMultibindingDeclarations(Key key) {
        ImmutableSet.Builder<MultibindingDeclaration> multibindingDeclarations =
            ImmutableSet.builder();
        for (Resolver resolver : getResolverLineage()) {
          multibindingDeclarations.addAll(resolver.multibindingDeclarations.get(key));
        }
        return multibindingDeclarations.build();
      }

      /**
       * Returns the {@link SubcomponentDeclaration}s that match the {@code key} from this and all
       * ancestor resolvers.
       */
      private ImmutableSet<SubcomponentDeclaration> getSubcomponentDeclarations(Key key) {
        ImmutableSet.Builder<SubcomponentDeclaration> subcomponentDeclarations =
            ImmutableSet.builder();
        for (Resolver resolver : getResolverLineage()) {
          subcomponentDeclarations.addAll(resolver.subcomponentDeclarations.get(key));
        }
        return subcomponentDeclarations.build();
      }
      /**
       * Returns the {@link OptionalBindingDeclaration}s that match the {@code key} from this and
       * all ancestor resolvers.
       */
      private ImmutableSet<OptionalBindingDeclaration> getOptionalBindingDeclarations(Key key) {
        Optional<Key> unwrapped = keyFactory.unwrapOptional(key);
        if (!unwrapped.isPresent()) {
          return ImmutableSet.of();
        }
        ImmutableSet.Builder<OptionalBindingDeclaration> declarations = ImmutableSet.builder();
        for (Resolver resolver : getResolverLineage()) {
          declarations.addAll(resolver.optionalBindingDeclarations.get(unwrapped.get()));
        }
        return declarations.build();
      }

      private Optional<ResolvedBindings> getPreviouslyResolvedBindings(
          final BindingKey bindingKey) {
        Optional<ResolvedBindings> result = Optional.fromNullable(resolvedBindings.get(bindingKey));
        if (result.isPresent()) {
          return result;
        } else if (parentResolver.isPresent()) {
          return parentResolver.get().getPreviouslyResolvedBindings(bindingKey);
        } else {
          return Optional.absent();
        }
      }

      void resolve(BindingKey bindingKey) {
        // If we find a cycle, stop resolving. The original request will add it with all of the
        // other resolved deps.
        if (cycleStack.contains(bindingKey)) {
          return;
        }

        // If the binding was previously resolved in this (sub)component, don't resolve it again.
        if (resolvedBindings.containsKey(bindingKey)) {
          return;
        }

        /* If the binding was previously resolved in a supercomponent, then we may be able to avoid
         * resolving it here and just depend on the supercomponent resolution.
         *
         * 1. If it depends on multibindings with contributions from this subcomponent, then we have
         *    to resolve it in this subcomponent so that it sees the local contributions.
         *
         * 2. If there are any explicit bindings in this component, they may conflict with those in
         *    the supercomponent, so resolve them here so that conflicts can be caught.
         *
         * 3. If the previously resolved binding is an optional binding, and there are any explicit
         *    bindings for the underlying key in this component, resolve here so that absent
         *    bindings in the parent can be overridden by present bindings here.
         */
        if (getPreviouslyResolvedBindings(bindingKey).isPresent()) {
          /* Resolve in the parent in case there are multibinding contributions or conflicts in some
           * component between this one and the previously-resolved one. */
          parentResolver.get().resolve(bindingKey);
          if (!new MultibindingDependencies().dependsOnLocalMultibindings(bindingKey)
              && getLocalExplicitBindings(bindingKey.key()).isEmpty()
              && !hasLocallyPresentOptionalBinding(bindingKey)) {
            /* Cache the inherited parent component's bindings in case resolving at the parent found
             * bindings in some component between this one and the previously-resolved one. */
            ResolvedBindings inheritedBindings =
                getPreviouslyResolvedBindings(bindingKey).get().asInheritedIn(componentDescriptor);
            resolvedBindings.put(bindingKey, inheritedBindings);
            return;
          }
        }

        cycleStack.push(bindingKey);
        try {
          ResolvedBindings bindings = lookUpBindings(bindingKey);
          for (Binding binding : bindings.ownedBindings()) {
            for (DependencyRequest dependency : binding.implicitDependencies()) {
              resolve(dependency.bindingKey());
            }
          }
          resolvedBindings.put(bindingKey, bindings);
        } finally {
          cycleStack.pop();
        }
      }

      ImmutableMap<BindingKey, ResolvedBindings> getResolvedBindings() {
        ImmutableMap.Builder<BindingKey, ResolvedBindings> resolvedBindingsBuilder =
            ImmutableMap.builder();
        resolvedBindingsBuilder.putAll(resolvedBindings);
        if (parentResolver.isPresent()) {
          Collection<ResolvedBindings> bindingsResolvedInParent =
              Maps.difference(parentResolver.get().getResolvedBindings(), resolvedBindings)
                  .entriesOnlyOnLeft()
                  .values();
          for (ResolvedBindings resolvedInParent : bindingsResolvedInParent) {
            resolvedBindingsBuilder.put(
                resolvedInParent.bindingKey(),
                resolvedInParent.asInheritedIn(componentDescriptor));
          }
        }
        return resolvedBindingsBuilder.build();
      }

      ImmutableSet<ModuleDescriptor> getInheritedModules() {
        return parentResolver.isPresent()
            ? Sets.union(
                    parentResolver.get().getInheritedModules(),
                    parentResolver.get().componentDescriptor.transitiveModules())
                .immutableCopy()
            : ImmutableSet.<ModuleDescriptor>of();
      }

      ImmutableSet<ModuleDescriptor> getOwnedModules() {
        return Sets.difference(componentDescriptor.transitiveModules(), getInheritedModules())
            .immutableCopy();
      }

      /**
       * Returns {@code true} if {@code bindingKey} was previously resolved to an optional binding
       * for which there is an explicit present binding in this component.
       */
      private boolean hasLocallyPresentOptionalBinding(BindingKey bindingKey) {
        return Iterables.any(
            getPreviouslyResolvedBindings(bindingKey).get().contributionBindings(),
            binding ->
                binding.bindingKind().equals(SYNTHETIC_OPTIONAL_BINDING)
                    && !getLocalExplicitBindings(keyFactory.unwrapOptional(binding.key()).get())
                        .isEmpty());
      }

      private final class MultibindingDependencies {
        private final Set<Object> cycleChecker = new HashSet<>();

        /**
         * Returns {@code true} if {@code bindingKey} previously resolved to multibindings with
         * contributions declared within this component's modules, or if any of its unscoped
         * dependencies depend on such local multibindings.
         *
         * <p>We don't care about scoped dependencies because they will never depend on
         * multibindings with contributions from subcomponents.
         *
         * @throws IllegalArgumentException if {@link #getPreviouslyResolvedBindings(BindingKey)} is
         *     absent
         */
        boolean dependsOnLocalMultibindings(final BindingKey bindingKey) {
          checkArgument(
              getPreviouslyResolvedBindings(bindingKey).isPresent(),
              "no previously resolved bindings in %s for %s",
              Resolver.this,
              bindingKey);
          // Don't recur infinitely if there are valid cycles in the dependency graph.
          // http://b/23032377
          if (!cycleChecker.add(bindingKey)) {
            return false;
          }
          try {
            return dependsOnLocalMultibindingsCache.get(
                bindingKey,
                () -> {
                  ResolvedBindings previouslyResolvedBindings =
                      getPreviouslyResolvedBindings(bindingKey).get();
                  if (isMultibindingsWithLocalContributions(previouslyResolvedBindings)) {
                    return true;
                  }

                  for (Binding binding : previouslyResolvedBindings.bindings()) {
                    if (dependsOnLocalMultibindings(binding)) {
                      return true;
                    }
                  }
                  return false;
                });
          } catch (ExecutionException e) {
            throw new AssertionError(e);
          }
        }

        /**
         * Returns {@code true} if {@code binding} is unscoped (or has {@link Reusable @Reusable}
         * scope) and depends on multibindings with contributions declared within this component's
         * modules, or if any of its unscoped or {@link Reusable @Reusable} scoped dependencies
         * depend on such local multibindings.
         *
         * <p>We don't care about non-reusable scoped dependencies because they will never depend on
         * multibindings with contributions from subcomponents.
         */
        boolean dependsOnLocalMultibindings(final Binding binding) {
          if (!cycleChecker.add(binding)) {
            return false;
          }
          try {
            return bindingDependsOnLocalMultibindingsCache.get(
                binding,
                () -> {
                  if ((!binding.scope().isPresent()
                          || binding.scope().get().equals(reusableScope(elements)))
                      // TODO(beder): Figure out what happens with production subcomponents.
                      && !binding.bindingType().equals(BindingType.PRODUCTION)) {
                    for (DependencyRequest dependency : binding.implicitDependencies()) {
                      if (dependsOnLocalMultibindings(dependency.bindingKey())) {
                        return true;
                      }
                    }
                  }
                  return false;
                });
          } catch (ExecutionException e) {
            throw new AssertionError(e);
          }
        }

        private boolean isMultibindingsWithLocalContributions(ResolvedBindings resolvedBindings) {
          Key key = resolvedBindings.key();
          return resolvedBindings.contributionBindings()
              .stream()
              .map(ContributionBinding::bindingKind)
              .anyMatch(SYNTHETIC_MULTIBOUND_KINDS::contains)
              && !getExplicitMultibindings(key)
              .equals(parentResolver.get().getExplicitMultibindings(key));
        }
      }
    }

    /**
     * A multimap of those {@code declarations} that are multibinding contribution declarations,
     * indexed by the key of the set or map to which they contribute.
     */
    static <T extends HasKey>
        ImmutableSetMultimap<Key, T> multibindingContributionsByMultibindingKey(
            Iterable<T> declarations) {
      ImmutableSetMultimap.Builder<Key, T> builder = ImmutableSetMultimap.builder();
      for (T declaration : declarations) {
        if (declaration.key().multibindingContributionIdentifier().isPresent()) {
          builder.put(declaration.key().withoutMultibindingContributionIdentifier(), declaration);
        }
      }
      return builder.build();
    }
  }
}

/*
 * Copyright (C) 2014 Google, Inc.
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

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import dagger.Component;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.producers.ProductionComponent;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executor;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;

import static com.google.auto.common.MoreElements.getAnnotationMirror;
import static dagger.internal.codegen.ComponentDescriptor.isComponentContributionMethod;
import static dagger.internal.codegen.ComponentDescriptor.isComponentProductionMethod;
import static dagger.internal.codegen.ComponentDescriptor.Kind.PRODUCTION_COMPONENT;
import static dagger.internal.codegen.ConfigurationAnnotations.getComponentDependencies;
import static dagger.internal.codegen.MembersInjectionBinding.Strategy.DELEGATE;
import static dagger.internal.codegen.MembersInjectionBinding.Strategy.NO_OP;

/**
 * The canonical representation of a full-resolved graph.
 *
 * @author Gregory Kick
 */
@AutoValue
abstract class BindingGraph {
  abstract ComponentDescriptor componentDescriptor();
  abstract ImmutableMap<BindingKey, ResolvedBindings> resolvedBindings();
  abstract ImmutableMap<ExecutableElement, BindingGraph> subgraphs();

  /**
   * Returns the set of types necessary to implement the component, but are not part of the injected
   * graph.  This includes modules, component dependencies and an {@link Executor} in the case of
   * {@link ProductionComponent}.
   */
  ImmutableSet<TypeElement> componentRequirements() {
    return FluentIterable.from(componentDescriptor().transitiveModules())
        .transform(new Function<ModuleDescriptor, TypeElement>() {
          @Override public TypeElement apply(ModuleDescriptor input) {
            return input.moduleElement();
          }
        })
        .append(componentDescriptor().dependencies())
        .append(componentDescriptor().executorDependency().asSet())
        .toSet();
  }

  static final class Factory {
    private final Elements elements;
    private final InjectBindingRegistry injectBindingRegistry;
    private final Key.Factory keyFactory;
    private final DependencyRequest.Factory dependencyRequestFactory;
    private final ProvisionBinding.Factory provisionBindingFactory;
    private final ProductionBinding.Factory productionBindingFactory;

    Factory(Elements elements,
        InjectBindingRegistry injectBindingRegistry,
        Key.Factory keyFactory,
        DependencyRequest.Factory dependencyRequestFactory,
        ProvisionBinding.Factory provisionBindingFactory,
        ProductionBinding.Factory productionBindingFactory) {
      this.elements = elements;
      this.injectBindingRegistry = injectBindingRegistry;
      this.keyFactory = keyFactory;
      this.dependencyRequestFactory = dependencyRequestFactory;
      this.provisionBindingFactory = provisionBindingFactory;
      this.productionBindingFactory = productionBindingFactory;
    }

    BindingGraph create(ComponentDescriptor componentDescriptor) {
      return create(Optional.<RequestResolver>absent(), componentDescriptor);
    }

    private BindingGraph create(Optional<RequestResolver> parentResolver,
        ComponentDescriptor componentDescriptor) {
      ImmutableSet.Builder<ProvisionBinding> explicitProvisionBindingsBuilder =
          ImmutableSet.builder();
      ImmutableSet.Builder<ProductionBinding> explicitProductionBindingsBuilder =
          ImmutableSet.builder();

      // binding for the component itself
      TypeElement componentDefinitionType = componentDescriptor.componentDefinitionType();
      ProvisionBinding componentBinding =
          provisionBindingFactory.forComponent(componentDefinitionType);
      explicitProvisionBindingsBuilder.add(componentBinding);

      // Collect Component dependencies.
      Optional<AnnotationMirror> componentMirror =
          getAnnotationMirror(componentDefinitionType, Component.class)
              .or(getAnnotationMirror(componentDefinitionType, ProductionComponent.class));
      ImmutableSet<TypeElement> componentDependencyTypes = componentMirror.isPresent()
          ? MoreTypes.asTypeElements(getComponentDependencies(componentMirror.get()))
          : ImmutableSet.<TypeElement>of();
      for (TypeElement componentDependency : componentDependencyTypes) {
        explicitProvisionBindingsBuilder.add(
            provisionBindingFactory.forComponent(componentDependency));
        List<ExecutableElement> dependencyMethods =
            ElementFilter.methodsIn(elements.getAllMembers(componentDependency));
        for (ExecutableElement method : dependencyMethods) {
          // MembersInjection methods aren't "provided" explicitly, so ignore them.
          if (isComponentContributionMethod(elements, method)) {
            if (componentDescriptor.kind().equals(PRODUCTION_COMPONENT)
                && isComponentProductionMethod(elements, method)) {
              explicitProductionBindingsBuilder.add(
                  productionBindingFactory.forComponentMethod(method));
            } else {
              explicitProvisionBindingsBuilder.add(
                  provisionBindingFactory.forComponentMethod(method));
            }
          }
        }
      }

      // Collect transitive module bindings.
      for (ModuleDescriptor moduleDescriptor : componentDescriptor.transitiveModules()) {
        for (ContributionBinding binding : moduleDescriptor.bindings()) {
          if (binding instanceof ProvisionBinding) {
            explicitProvisionBindingsBuilder.add((ProvisionBinding) binding);
          }
          if (binding instanceof ProductionBinding) {
            explicitProductionBindingsBuilder.add((ProductionBinding) binding);
          }
        }
      }

      RequestResolver requestResolver = new RequestResolver(
          parentResolver,
          componentDescriptor.wrappedScope(),
          explicitBindingsByKey(explicitProvisionBindingsBuilder.build()),
          explicitBindingsByKey(explicitProductionBindingsBuilder.build()));
      for (ComponentMethodDescriptor componentMethod : componentDescriptor.componentMethods()) {
        Optional<DependencyRequest> componentMethodRequest = componentMethod.dependencyRequest();
        if (componentMethodRequest.isPresent()) {
          requestResolver.resolve(componentMethodRequest.get());
        }
      }

      ImmutableMap.Builder<ExecutableElement, BindingGraph> subgraphsBuilder =
          ImmutableMap.builder();
      for (Entry<ExecutableElement, ComponentDescriptor> subcomponentEntry :
          componentDescriptor.subcomponents().entrySet()) {
        subgraphsBuilder.put(subcomponentEntry.getKey(),
            create(Optional.of(requestResolver), subcomponentEntry.getValue()));
      }

      return new AutoValue_BindingGraph(
          componentDescriptor,
          requestResolver.getResolvedBindings(),
          subgraphsBuilder.build());
    }

    private <B extends ContributionBinding> ImmutableSetMultimap<Key, B> explicitBindingsByKey(
        Iterable<? extends B> bindings) {
      // Multimaps.index() doesn't do ImmutableSetMultimaps.
      ImmutableSetMultimap.Builder<Key, B> builder = ImmutableSetMultimap.builder();
      for (B binding : bindings) {
        builder.put(binding.key(), binding);
      }
      return builder.build();
    }

    private final class RequestResolver {
      final Optional<RequestResolver> parentResolver;
      final Optional<Equivalence.Wrapper<AnnotationMirror>> targetScope;
      final ImmutableSetMultimap<Key, ProvisionBinding> explicitProvisionBindings;
      final ImmutableSetMultimap<Key, ProductionBinding> explicitProductionBindings;
      final Map<BindingKey, ResolvedBindings> resolvedBindings;
      final Deque<BindingKey> cycleStack = new ArrayDeque<>();

      RequestResolver(Optional<RequestResolver> parentResolver,
          Optional<Equivalence.Wrapper<AnnotationMirror>> targetScope,
          ImmutableSetMultimap<Key, ProvisionBinding> explicitProvisionBindings,
          ImmutableSetMultimap<Key, ProductionBinding> explicitProductionBindings) {
        assert parentResolver != null;
        this.parentResolver = parentResolver;
        assert targetScope != null;
        this.targetScope = targetScope;
        assert explicitProvisionBindings != null;
        this.explicitProvisionBindings = explicitProvisionBindings;
        assert explicitProductionBindings != null;
        this.explicitProductionBindings = explicitProductionBindings;
        this.resolvedBindings = Maps.newLinkedHashMap();
      }

      /**
       *  Looks up the bindings associated with a given dependency request and returns them.  In the
       *  event that the binding is owned by a parent component it will trigger resolution in that
       *  component's resolver but will return an {@link Optional#absent} value.
       */
      ResolvedBindings lookUpBindings(DependencyRequest request) {
        BindingKey bindingKey = request.bindingKey();
        switch (bindingKey.kind()) {
          case CONTRIBUTION:
            // First, check for explicit keys (those from modules and components)
            ImmutableSet<ProvisionBinding> explicitProvisionBindingsForKey =
                getExplicitProvisionBindings(bindingKey.key());
            ImmutableSet<ProductionBinding> explicitProductionBindingsForKey =
                getExplicitProductionBindings(bindingKey.key());

            // If the key is Map<K, V>, get its implicit binding keys, which are either
            // Map<K, Provider<V>> or Map<K, Producer<V>>, and grab their explicit bindings.
            Optional<Key> mapProviderKey = keyFactory.implicitMapProviderKeyFrom(bindingKey.key());
            ImmutableSet<ProvisionBinding> explicitMapProvisionBindings = ImmutableSet.of();
            if (mapProviderKey.isPresent()) {
              explicitMapProvisionBindings = getExplicitProvisionBindings(mapProviderKey.get());
            }

            Optional<Key> mapProducerKey = keyFactory.implicitMapProducerKeyFrom(bindingKey.key());
            ImmutableSet<ProductionBinding> explicitMapProductionBindings = ImmutableSet.of();
            if (mapProducerKey.isPresent()) {
              explicitMapProductionBindings = getExplicitProductionBindings(mapProducerKey.get());
            }

            if (!explicitProvisionBindingsForKey.isEmpty()
                || !explicitProductionBindingsForKey.isEmpty()) {
              // we have some explicit binding for this key, so we collect all explicit implicit map
              // bindings that might conflict with this and let the validator sort it out
              ImmutableSet.Builder<ContributionBinding> ownedBindings = ImmutableSet.builder();
              ImmutableSet.Builder<ContributionBinding> inheritedBindings = ImmutableSet.builder();
              for (ProvisionBinding provisionBinding :
                  Sets.union(explicitProvisionBindingsForKey, explicitMapProvisionBindings)) {
                Optional<RequestResolver> owningResolver = getOwningResolver(provisionBinding);
                if (owningResolver.isPresent() && !owningResolver.get().equals(this)) {
                  owningResolver.get().resolve(request);
                  inheritedBindings.add(provisionBinding);
                } else {
                  ownedBindings.add(provisionBinding);
                }
              }
              return ResolvedBindings.create(bindingKey,
                  ownedBindings
                      .addAll(explicitProductionBindingsForKey)
                      .addAll(explicitMapProductionBindings)
                      .build(),
                  inheritedBindings.build());
            } else {
              if (!explicitMapProductionBindings.isEmpty()) {
                // if we have any explicit Map<K, Producer<V>> bindings, then this Map<K, V> binding
                // must be considered an implicit ProductionBinding
                DependencyRequest implicitRequest =
                    dependencyRequestFactory.forImplicitMapBinding(request, mapProducerKey.get());
                return ResolvedBindings.create(bindingKey,
                    productionBindingFactory.forImplicitMapBinding(request, implicitRequest));
              } else if (!explicitMapProvisionBindings.isEmpty()) {
                // if there are Map<K, Provider<V>> bindings, then it'll be an implicit
                // ProvisionBinding
                DependencyRequest implicitRequest =
                    dependencyRequestFactory.forImplicitMapBinding(request, mapProviderKey.get());
                return ResolvedBindings.create(bindingKey,
                    provisionBindingFactory.forImplicitMapBinding(request, implicitRequest));
              } else {
                // no explicit binding, look it up.
                Optional<ProvisionBinding> provisionBinding =
                    injectBindingRegistry.getOrFindProvisionBinding(bindingKey.key());
                if (provisionBinding.isPresent()) {
                  Optional<RequestResolver> owningResolver =
                      getOwningResolver(provisionBinding.get());
                  if (owningResolver.isPresent() && !owningResolver.get().equals(this)) {
                    owningResolver.get().resolve(request);
                    return ResolvedBindings.create(
                        bindingKey, ImmutableSet.<Binding>of(),
                        provisionBinding.asSet());
                  }
                }
                return ResolvedBindings.create(
                    bindingKey, provisionBinding.asSet(), ImmutableSet.<Binding>of());
              }
            }
          case MEMBERS_INJECTION:
            // no explicit deps for members injection, so just look it up
            return ResolvedBindings.create(
                bindingKey, rollUpMembersInjectionBindings(bindingKey.key()));
          default:
            throw new AssertionError();
        }
      }

      private MembersInjectionBinding rollUpMembersInjectionBindings(Key key) {
        MembersInjectionBinding membersInjectionBinding =
            injectBindingRegistry.getOrFindMembersInjectionBinding(key);

        if (membersInjectionBinding.injectionStrategy().equals(DELEGATE)) {
          MembersInjectionBinding parentBinding = rollUpMembersInjectionBindings(
              membersInjectionBinding.parentInjectorRequest().get().key());
          if (parentBinding.injectionStrategy().equals(NO_OP)) {
            return membersInjectionBinding.withoutParentInjectorRequest();
          }
        }

        return membersInjectionBinding;
      }

      private Optional<RequestResolver> getOwningResolver(ProvisionBinding provisionBinding) {
        Optional<Equivalence.Wrapper<AnnotationMirror>> bindingScope =
            provisionBinding.wrappedScope();
        for (RequestResolver requestResolver : getResolverLineage().reverse()) {
          if (requestResolver.explicitProvisionBindings.containsValue(provisionBinding)) {
             return Optional.of(requestResolver);
          }
        }
        // look for scope separately.  we do this for the case where @Singleton can appear twice
        // in the † compatibility mode
        for (RequestResolver requestResolver : getResolverLineage().reverse()) {
          if (bindingScope.isPresent() && bindingScope.equals(requestResolver.targetScope)) {
            return Optional.of(requestResolver);
          }
        }
        return Optional.absent();
      }

      /** Returns the resolver lineage from parent to child. */
      private ImmutableList<RequestResolver> getResolverLineage() {
        List<RequestResolver> resolverList = Lists.newArrayList();
        for (Optional<RequestResolver> currentResolver = Optional.of(this);
            currentResolver.isPresent();
            currentResolver = currentResolver.get().parentResolver) {
          resolverList.add(currentResolver.get());
        }
        return ImmutableList.copyOf(Lists.reverse(resolverList));
      }

      private ImmutableSet<ProvisionBinding> getExplicitProvisionBindings(Key requestKey) {
        ImmutableSet.Builder<ProvisionBinding> explicitBindingsForKey = ImmutableSet.builder();
        for (RequestResolver resolver : getResolverLineage()) {
          explicitBindingsForKey.addAll(
              resolver.explicitProvisionBindings.get(requestKey));
        }
        return explicitBindingsForKey.build();
      }

      private ImmutableSet<ProductionBinding> getExplicitProductionBindings(Key requestKey) {
        ImmutableSet.Builder<ProductionBinding> explicitBindingsForKey = ImmutableSet.builder();
        for (RequestResolver resolver : getResolverLineage()) {
          explicitBindingsForKey.addAll(
              resolver.explicitProductionBindings.get(requestKey));
        }
        return explicitBindingsForKey.build();
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

      void resolve(DependencyRequest request) {
        BindingKey bindingKey = request.bindingKey();

        Optional<ResolvedBindings> previouslyResolvedBinding =
            getPreviouslyResolvedBindings(bindingKey);
        if (previouslyResolvedBinding.isPresent()
            && !(bindingKey.kind().equals(BindingKey.Kind.CONTRIBUTION)
                && !previouslyResolvedBinding.get().contributionBindings().isEmpty()
                && ContributionBinding.bindingTypeFor(
                    previouslyResolvedBinding.get().contributionBindings()).isMultibinding())) {
          return;
        }

        if (cycleStack.contains(bindingKey)) {
          // We found a cycle. Don't add a resolved binding, since the original request will add it
          // with all of the other resolved deps
          return;
        }

        cycleStack.push(bindingKey);
        try {
          ResolvedBindings bindings = lookUpBindings(request);
          for (Binding binding : bindings.ownedBindings()) {
            for (DependencyRequest dependency : binding.implicitDependencies()) {
              resolve(dependency);
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
          for (ResolvedBindings resolvedInParent :
              parentResolver.get().getResolvedBindings().values()) {
            BindingKey bindingKey = resolvedInParent.bindingKey();
            if (!resolvedBindings.containsKey(bindingKey)) {
              if (resolvedInParent.ownedBindings().isEmpty()) {
                // reuse the instance if we can get away with it
                resolvedBindingsBuilder.put(bindingKey, resolvedInParent);
              } else {
                resolvedBindingsBuilder.put(bindingKey,
                    ResolvedBindings.create(
                        bindingKey, ImmutableSet.<Binding>of(), resolvedInParent.bindings()));
              }
            }
          }
        }
        return resolvedBindingsBuilder.build();
      }
    }
  }
}

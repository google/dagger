/*
 * Copyright (C) 2016 Google, Inc.
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

import com.google.auto.value.AutoValue;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Maps;
import java.util.Collection;
import javax.inject.Provider;
import javax.lang.model.element.Element;

import static com.google.common.collect.Iterables.getOnlyElement;

/**
 * The framework class and binding key for a resolved dependency of a binding. If a binding has
 * several dependencies for a key, then only one instance of this class will represent them all.
 *
 * <p>In the following example, the binding {@code provideFoo()} has two dependency requests:
 *
 * <ol>
 * <li>{@code Bar bar}
 * <li>{@code Provider<Bar> barProvider}
 * </ol>
 *
 * But they both can be satisfied with the same instance of {@code Provider<Bar>}. So one instance
 * of {@code FrameworkDependency} will be used for both. Its {@link #bindingKey()} will be for
 * {@code Bar}, and its {@link #frameworkClass()} will be {@link Provider}.
 *
 * <pre><code>
 *   {@literal @Provides} static Foo provideFoo(Bar bar, {@literal Provider<Bar>} barProvider) {
 *     return new Foo(…);
 *   }
 * </code></pre>
 */
@AutoValue
abstract class FrameworkDependency {

  /**
   * The fully-resolved binding key shared by all the dependency requests.
   */
  abstract BindingKey bindingKey();

  /**
   * The framework class to use for these requests.
   */
  abstract Class<?> frameworkClass();

  /**
   * The dependency requests that are all satisfied by one framework instance.
   */
  abstract ImmutableSet<DependencyRequest> dependencyRequests();

  /**
   * The framework dependencies of {@code binding}. There will be one element for each
   * different binding key in the <em>{@linkplain Binding#unresolved() unresolved}</em> version of
   * {@code binding}.
   *
   * <p>For example, given the following modules:
   * <pre><code>
   *   {@literal @Module} abstract class {@literal BaseModule<T>} {
   *     {@literal @Provides} Foo provideFoo(T t, String string) {
   *       return …;
   *     }
   *   }
   *
   *   {@literal @Module} class StringModule extends {@literal BaseModule<String>} {}
   * </code></pre>
   *
   * Both dependencies of {@code StringModule.provideFoo} have the same binding key:
   * {@code String}. But there are still two dependencies, because in the unresolved binding they
   * have different binding keys:
   *
   * <dl>
   * <dt>{@code T} <dd>{@code String t}
   * <dt>{@code String} <dd>{@code String string}
   * </dl>
   * 
   * <p>Note that the sets returned by this method when called on the same binding will be equal,
   * and their elements will be in the same order.
   */
  /* TODO(dpb): The stable-order postcondition is actually hard to verify in code for two equal
   * instances of Binding, because it really depends on the order of the binding's dependencies,
   * and two equal instances of Binding may have the same dependencies in a different order. */
  static ImmutableSet<FrameworkDependency> frameworkDependenciesForBinding(Binding binding) {
    DependencyRequestMapper dependencyRequestMapper =
        DependencyRequestMapper.forBindingType(binding.bindingType());
    ImmutableSet.Builder<FrameworkDependency> frameworkDependencies = ImmutableSet.builder();
    for (Collection<DependencyRequest> requests : groupByUnresolvedKey(binding)) {
      frameworkDependencies.add(
          new AutoValue_FrameworkDependency(
              getOnlyElement(
                  FluentIterable.from(requests)
                      .transform(DependencyRequest.BINDING_KEY_FUNCTION)
                      .toSet()),
              dependencyRequestMapper.getFrameworkClass(requests),
              ImmutableSet.copyOf(requests)));
    }
    return frameworkDependencies.build();
  }

  /** Indexes {@code dependencies} by their {@link #dependencyRequests()}. */
  static ImmutableMap<DependencyRequest, FrameworkDependency> indexByDependencyRequest(
      Iterable<FrameworkDependency> dependencies) {
    ImmutableMap.Builder<DependencyRequest, FrameworkDependency> frameworkDependencyMap =
        ImmutableMap.builder();
    for (FrameworkDependency dependency : dependencies) {
      for (DependencyRequest request : dependency.dependencyRequests()) {
        frameworkDependencyMap.put(request, dependency);
      }
    }
    return frameworkDependencyMap.build();
  }

  /**
   * Groups {@code binding}'s implicit dependencies by their binding key, using the dependency keys
   * from the {@link Binding#unresolved()} binding if it exists.
   */
  private static ImmutableList<Collection<DependencyRequest>> groupByUnresolvedKey(
      Binding binding) {
    // If the binding has no unresolved version, just group the dependencies by binding key.
    if (!binding.unresolved().isPresent()) {
      return groupByKey(binding, Functions.<DependencyRequest>identity());
    }

    // Group the unresolved dependencies, replacing each one with its resolved version by looking it
    // up by request element.
    final ImmutableMap<Element, DependencyRequest> resolvedDependencies =
        Maps.uniqueIndex(
            binding.implicitDependencies(),
            new Function<DependencyRequest, Element>() {
              @Override
              public Element apply(DependencyRequest dependencyRequest) {
                return dependencyRequest.requestElement();
              }
            });
    return groupByKey(
        binding.unresolved().get(),
        new Function<DependencyRequest, DependencyRequest>() {
          @Override
          public DependencyRequest apply(DependencyRequest unresolvedRequest) {
            return resolvedDependencies.get(unresolvedRequest.requestElement());
          }
        });
  }

  /**
   * Groups a binding's dependency requests by their binding key.
   *
   * @param transformer applied to each dependency before inserting into the group
   */
  private static ImmutableList<Collection<DependencyRequest>> groupByKey(
      Binding binding, Function<DependencyRequest, DependencyRequest> transformer) {
    ImmutableSetMultimap.Builder<BindingKey, DependencyRequest> dependenciesByKeyBuilder =
        ImmutableSetMultimap.builder();
    for (DependencyRequest dependency : binding.implicitDependencies()) {
      dependenciesByKeyBuilder.put(dependency.bindingKey(), transformer.apply(dependency));
    }
    return ImmutableList.copyOf(
        dependenciesByKeyBuilder
            .orderValuesBy(SourceFiles.DEPENDENCY_ORDERING)
            .build()
            .asMap()
            .values());
  }
}

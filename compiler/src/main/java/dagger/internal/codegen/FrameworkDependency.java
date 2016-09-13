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

import static com.google.common.collect.Iterables.getOnlyElement;

import com.google.auto.value.AutoValue;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import java.util.Collection;
import java.util.Iterator;
import javax.inject.Provider;

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

  /** The binding type of the framework dependency. */
  abstract BindingType bindingType();

  /** The framework class to use for these requests. */
  final Class<?> frameworkClass() {
    return bindingType().frameworkClass();
  }

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
    BindingTypeMapper bindingTypeMapper =
        BindingTypeMapper.forBindingType(binding.bindingType());
    ImmutableSet.Builder<FrameworkDependency> frameworkDependencies = ImmutableSet.builder();
    for (Collection<DependencyRequest> requests : groupByUnresolvedKey(binding)) {
      frameworkDependencies.add(
          new AutoValue_FrameworkDependency(
              getOnlyElement(
                  FluentIterable.from(requests).transform(DependencyRequest::bindingKey).toSet()),
              bindingTypeMapper.getBindingType(requests),
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
    ImmutableSetMultimap.Builder<BindingKey, DependencyRequest> dependenciesByKeyBuilder =
        ImmutableSetMultimap.builder();
    Iterator<DependencyRequest> dependencies = binding.implicitDependencies().iterator();
    Binding unresolved = binding.unresolved().isPresent() ? binding.unresolved().get() : binding;
    Iterator<DependencyRequest> unresolvedDependencies =
        unresolved.implicitDependencies().iterator();
    while (dependencies.hasNext()) {
      dependenciesByKeyBuilder.put(unresolvedDependencies.next().bindingKey(), dependencies.next());
    }
    return ImmutableList.copyOf(
        dependenciesByKeyBuilder
            .orderValuesBy(SourceFiles.DEPENDENCY_ORDERING)
            .build()
            .asMap()
            .values());
  }
}

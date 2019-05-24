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

import static com.google.common.base.Suppliers.memoize;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.DaggerStreams.toImmutableList;
import static java.util.stream.Collectors.toSet;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.auto.value.AutoValue;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.BindingKind;
import dagger.model.DependencyRequest;
import dagger.model.Key;
import dagger.model.Scope;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleTypeVisitor6;

/**
 * An abstract type for classes representing a Dagger binding. Particularly, contains the {@link
 * Element} that generated the binding and the {@link DependencyRequest} instances that are required
 * to satisfy the binding, but leaves the specifics of the <i>mechanism</i> of the binding to the
 * subtypes.
 */
abstract class Binding extends BindingDeclaration {

  /**
   * Returns {@code true} if using this binding requires an instance of the {@link
   * #contributingModule()}.
   */
  boolean requiresModuleInstance() {
    if (!bindingElement().isPresent() || !contributingModule().isPresent()) {
      return false;
    }
    Set<Modifier> modifiers = bindingElement().get().getModifiers();
    return !modifiers.contains(ABSTRACT) && !modifiers.contains(STATIC);
  }

  /**
   * Returns {@code true} if this binding may provide {@code null} instead of an instance of {@link
   * #key()}. Nullable bindings cannot be requested from {@linkplain DependencyRequest#isNullable()
   * non-nullable dependency requests}.
   */
  abstract boolean isNullable();

  /** The kind of binding this instance represents. */
  abstract BindingKind kind();

  /** The {@link BindingType} of this binding. */
  abstract BindingType bindingType();

  /** The {@link FrameworkType} of this binding. */
  final FrameworkType frameworkType() {
    return FrameworkType.forBindingType(bindingType());
  }

  /**
   * The explicit set of {@link DependencyRequest dependencies} required to satisfy this binding as
   * defined by the user-defined injection sites.
   */
  abstract ImmutableSet<DependencyRequest> explicitDependencies();

  /**
   * The set of {@link DependencyRequest dependencies} that are added by the framework rather than a
   * user-defined injection site. This returns an unmodifiable set.
   */
  // TODO(gak): this will eventually get changed to return a set of FrameworkDependency
  ImmutableSet<DependencyRequest> implicitDependencies() {
    return ImmutableSet.of();
  }

  private final Supplier<ImmutableSet<DependencyRequest>> dependencies =
      memoize(
          () -> {
            ImmutableSet<DependencyRequest> implicitDependencies = implicitDependencies();
            return ImmutableSet.copyOf(
                implicitDependencies.isEmpty()
                    ? explicitDependencies()
                    : Sets.union(implicitDependencies, explicitDependencies()));
          });

  /**
   * The set of {@link DependencyRequest dependencies} required to satisfy this binding. This is the
   * union of {@link #explicitDependencies()} and {@link #implicitDependencies()}. This returns an
   * unmodifiable set.
   */
  final ImmutableSet<DependencyRequest> dependencies() {
    return dependencies.get();
  }

  private final Supplier<ImmutableList<FrameworkDependency>> frameworkDependencies =
      memoize(
          () ->
              dependencyAssociations()
                  .stream()
                  .map(DependencyAssociation::frameworkDependency)
                  .collect(toImmutableList()));

  /**
   * The framework dependencies of {@code binding}. There will be one element for each different
   * binding key in the <em>{@linkplain Binding#unresolved() unresolved}</em> version of {@code
   * binding}.
   *
   * <p>For example, given the following modules:
   *
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
   * Both dependencies of {@code StringModule.provideFoo} have the same binding key: {@code String}.
   * But there are still two dependencies, because in the unresolved binding they have different
   * binding keys:
   *
   * <dl>
   *   <dt>{@code T}
   *   <dd>{@code String t}
   *   <dt>{@code String}
   *   <dd>{@code String string}
   * </dl>
   *
   * <p>Note that the sets returned by this method when called on the same binding will be equal,
   * and their elements will be in the same order.
   */
  /* TODO(dpb): The stable-order postcondition is actually hard to verify in code for two equal
   * instances of Binding, because it really depends on the order of the binding's dependencies,
   * and two equal instances of Binding may have the same dependencies in a different order. */
  final ImmutableList<FrameworkDependency> frameworkDependencies() {
    return frameworkDependencies.get();
  }

  /**
   * Associates a {@link FrameworkDependency} with the set of {@link DependencyRequest} instances
   * that correlate for a binding.
   */
  @AutoValue
  abstract static class DependencyAssociation {
    abstract FrameworkDependency frameworkDependency();

    abstract ImmutableSet<DependencyRequest> dependencyRequests();

    static DependencyAssociation create(
        FrameworkDependency frameworkDependency, Iterable<DependencyRequest> dependencyRequests) {
      return new AutoValue_Binding_DependencyAssociation(
          frameworkDependency, ImmutableSet.copyOf(dependencyRequests));
    }
  }

  private final Supplier<ImmutableList<DependencyAssociation>> dependencyAssociations =
      memoize(
          () -> {
            FrameworkTypeMapper frameworkTypeMapper =
                FrameworkTypeMapper.forBindingType(bindingType());
            ImmutableList.Builder<DependencyAssociation> list = ImmutableList.builder();
            for (Set<DependencyRequest> requests : groupByUnresolvedKey()) {
              list.add(
                  DependencyAssociation.create(
                      FrameworkDependency.create(
                          getOnlyElement(
                              requests.stream().map(DependencyRequest::key).collect(toSet())),
                          frameworkTypeMapper.getFrameworkType(requests)),
                      requests));
            }
            return list.build();
          });

  /**
   * Returns the same {@link FrameworkDependency} instances from {@link #frameworkDependencies}, but
   * with the set of {@link DependencyRequest} instances with which each is associated.
   *
   * <p>Ths method returns a list of {@link Map.Entry entries} rather than a {@link Map} or {@link
   * com.google.common.collect.Multimap} because any given {@link FrameworkDependency} may appear
   * multiple times if the {@linkplain Binding#unresolved() unresolved} binding requires it. If that
   * distinction is not important, the entries can be merged into a single mapping.
   */
  final ImmutableList<DependencyAssociation> dependencyAssociations() {
    return dependencyAssociations.get();
  }

  private final Supplier<ImmutableMap<DependencyRequest, FrameworkDependency>>
      frameworkDependenciesMap =
          memoize(
              () -> {
                ImmutableMap.Builder<DependencyRequest, FrameworkDependency> frameworkDependencies =
                    ImmutableMap.builder();
                for (DependencyAssociation dependencyAssociation : dependencyAssociations()) {
                  for (DependencyRequest dependencyRequest :
                      dependencyAssociation.dependencyRequests()) {
                    frameworkDependencies.put(
                        dependencyRequest, dependencyAssociation.frameworkDependency());
                  }
                }
                return frameworkDependencies.build();
              });

  /**
   * Returns the mapping from each {@linkplain #dependencies dependency} to its associated {@link
   * FrameworkDependency}.
   */
  final ImmutableMap<DependencyRequest, FrameworkDependency>
      dependenciesToFrameworkDependenciesMap() {
    return frameworkDependenciesMap.get();
  }

  /**
   * Groups {@code binding}'s implicit dependencies by their binding key, using the dependency keys
   * from the {@link Binding#unresolved()} binding if it exists.
   */
  private ImmutableList<Set<DependencyRequest>> groupByUnresolvedKey() {
    ImmutableSetMultimap.Builder<Key, DependencyRequest> dependenciesByKeyBuilder =
        ImmutableSetMultimap.builder();
    Iterator<DependencyRequest> dependencies = dependencies().iterator();
    Binding unresolved = unresolved().isPresent() ? unresolved().get() : this;
    Iterator<DependencyRequest> unresolvedDependencies = unresolved.dependencies().iterator();
    while (dependencies.hasNext()) {
      dependenciesByKeyBuilder.put(unresolvedDependencies.next().key(), dependencies.next());
    }
    return ImmutableList.copyOf(
        Multimaps.asMap(
                dependenciesByKeyBuilder.orderValuesBy(SourceFiles.DEPENDENCY_ORDERING).build())
            .values());
  }

  /**
   * If this binding's key's type parameters are different from those of the
   * {@link #bindingTypeElement()}, this is the binding for the {@link #bindingTypeElement()}'s
   * unresolved type.
   */
  abstract Optional<? extends Binding> unresolved();

  Optional<Scope> scope() {
    return Optional.empty();
  }

  // TODO(sameb): Remove the TypeElement parameter and pull it from the TypeMirror.
  static boolean hasNonDefaultTypeParameters(
      TypeElement element, TypeMirror type, DaggerTypes types) {
    // If the element has no type parameters, nothing can be wrong.
    if (element.getTypeParameters().isEmpty()) {
      return false;
    }

    List<TypeMirror> defaultTypes = Lists.newArrayList();
    for (TypeParameterElement parameter : element.getTypeParameters()) {
      defaultTypes.add(parameter.asType());
    }

    List<TypeMirror> actualTypes =
        type.accept(
            new SimpleTypeVisitor6<List<TypeMirror>, Void>() {
              @Override
              protected List<TypeMirror> defaultAction(TypeMirror e, Void p) {
                return ImmutableList.of();
              }

              @Override
              public List<TypeMirror> visitDeclared(DeclaredType t, Void p) {
                return ImmutableList.<TypeMirror>copyOf(t.getTypeArguments());
              }
            },
            null);

    // The actual type parameter size can be different if the user is using a raw type.
    if (defaultTypes.size() != actualTypes.size()) {
      return true;
    }

    for (int i = 0; i < defaultTypes.size(); i++) {
      if (!types.isSameType(defaultTypes.get(i), actualTypes.get(i))) {
        return true;
      }
    }
    return false;
  }
}

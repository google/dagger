/*
 * Copyright (C) 2014 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package dagger2.internal.codegen;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import dagger2.internal.DoubleCheckLazy;
import dagger2.internal.codegen.ContributionBinding.BindingType;
import dagger2.internal.codegen.writer.ClassName;
import dagger2.internal.codegen.writer.ParameterizedTypeName;
import dagger2.internal.codegen.writer.Snippet;
import dagger2.internal.codegen.writer.TypeName;
import dagger2.internal.codegen.writer.TypeNames;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

import static com.google.common.base.CaseFormat.UPPER_CAMEL;

/**
 * Utilities for generating files.
 *
 * @author Gregory Kick
 * @since 2.0
 */
class SourceFiles {
  /**
   * Sorts {@link DependencyRequest} instances in an order likely to reflect their logical
   * importance.
   */
  static final Ordering<DependencyRequest> DEPENDENCY_ORDERING = new Ordering<DependencyRequest>() {
    @Override
    public int compare(DependencyRequest left, DependencyRequest right) {
      return ComparisonChain.start()
      // put fields before parameters
          .compare(left.requestElement().getKind(), right.requestElement().getKind())
          // order by dependency kind
          .compare(left.kind(), right.kind())
          // then sort by name
          .compare(left.requestElement().getSimpleName().toString(),
              right.requestElement().getSimpleName().toString()).result();
    }
  };

  /**
   * A variant of {@link #indexDependenciesByKey} that maps from unresolved keys
   * to requests.  This is used when generating component's initialize()
   * methods (and in members injectors) in order to instantiate dependent
   * providers.  Consider a generic type of {@code Foo<T>} with a constructor
   * of {@code Foo(T t, T t1, A a, A a1)}.  That will be collapsed to a factory
   * taking a {@code Provider<T> tProvider, Provider<A> aProvider}. However,
   * if it was referenced as {@code Foo<A>}, we need to make sure we still
   * pass two providers.  Naively (if we just referenced by resolved BindingKey),
   * we would have passed a single {@code aProvider}.
   */
  // TODO(user): Refactor these indexing methods so that the binding itself knows what sort of
  // binding keys and framework classes that it needs.
  static ImmutableSetMultimap<BindingKey, DependencyRequest> indexDependenciesByUnresolvedKey(
      Types types, Iterable<? extends DependencyRequest> dependencies) {
    ImmutableSetMultimap.Builder<BindingKey, DependencyRequest> dependenciesByKeyBuilder =
        new ImmutableSetMultimap.Builder<BindingKey, DependencyRequest>()
            .orderValuesBy(DEPENDENCY_ORDERING);
    for (DependencyRequest dependency : dependencies) {
      BindingKey resolved = dependency.bindingKey();
      // To get the proper unresolved type, we have to extract the proper type from the
      // request type again (because we're looking at the actual element's type).
      TypeMirror unresolvedType =
          DependencyRequest.Factory.extractKindAndType(dependency.requestElement().asType()).type();
      BindingKey unresolved =
          BindingKey.create(resolved.kind(), resolved.key().withType(types, unresolvedType));
      dependenciesByKeyBuilder.put(unresolved, dependency);
    }
    return dependenciesByKeyBuilder.build();
  }

  /**
   * Allows dependency requests to be grouped by the key they're requesting.
   * This is used by factory generation in order to minimize the number of parameters
   * required in the case where a given key is requested more than once.  This expects
   * unresolved dependency requests, otherwise we may generate factories based on
   * a particular usage of a class as opposed to the generic types of the class.
   */
  static ImmutableSetMultimap<BindingKey, DependencyRequest> indexDependenciesByKey(
      Iterable<? extends DependencyRequest> dependencies) {
    ImmutableSetMultimap.Builder<BindingKey, DependencyRequest> dependenciesByKeyBuilder =
        new ImmutableSetMultimap.Builder<BindingKey, DependencyRequest>()
            .orderValuesBy(DEPENDENCY_ORDERING);
    for (DependencyRequest dependency : dependencies) {
      dependenciesByKeyBuilder.put(dependency.bindingKey(), dependency);
    }
    return dependenciesByKeyBuilder.build();
  }

  /**
   * This method generates names and keys for the framework classes necessary for all of the
   * bindings. It is responsible for the following:
   * <ul>
   * <li>Choosing a name that associates the binding with all of the dependency requests for this
   * type.
   * <li>Choosing a name that is <i>probably</i> associated with the type being bound.
   * <li>Ensuring that no two bindings end up with the same name.
   * </ul>
   *
   * @return Returns the mapping from {@link BindingKey} to field, sorted by the name of the field.
   */
  static ImmutableMap<BindingKey, FrameworkField> generateBindingFieldsForDependencies(
      DependencyRequestMapper dependencyRequestMapper,
      Iterable<? extends DependencyRequest> dependencies) {
    ImmutableSetMultimap<BindingKey, DependencyRequest> dependenciesByKey =
        indexDependenciesByKey(dependencies);
    Map<BindingKey, Collection<DependencyRequest>> dependenciesByKeyMap =
        dependenciesByKey.asMap();
    ImmutableMap.Builder<BindingKey, FrameworkField> bindingFields = ImmutableMap.builder();
    for (Entry<BindingKey, Collection<DependencyRequest>> entry
        : dependenciesByKeyMap.entrySet()) {
      BindingKey bindingKey = entry.getKey();
      Collection<DependencyRequest> requests = entry.getValue();
      Class<?> frameworkClass =
          dependencyRequestMapper.getFrameworkClass(requests.iterator().next());
      // collect together all of the names that we would want to call the provider
      ImmutableSet<String> dependencyNames =
          FluentIterable.from(requests).transform(new DependencyVariableNamer()).toSet();

      if (dependencyNames.size() == 1) {
        // if there's only one name, great! use it!
        String name = Iterables.getOnlyElement(dependencyNames);
        bindingFields.put(bindingKey,
            FrameworkField.createWithTypeFromKey(frameworkClass, bindingKey, name));
      } else {
        // in the event that a field is being used for a bunch of deps with different names,
        // add all the names together with "And"s in the middle. E.g.: stringAndS
        Iterator<String> namesIterator = dependencyNames.iterator();
        String first = namesIterator.next();
        StringBuilder compositeNameBuilder = new StringBuilder(first);
        while (namesIterator.hasNext()) {
          compositeNameBuilder.append("And").append(
              CaseFormat.LOWER_CAMEL.to(UPPER_CAMEL, namesIterator.next()));
        }
        bindingFields.put(bindingKey, FrameworkField.createWithTypeFromKey(
            frameworkClass, bindingKey, compositeNameBuilder.toString()));
      }
    }
    return bindingFields.build();
  }

  static Snippet frameworkTypeUsageStatement(Snippet frameworkTypeMemberSelect,
      DependencyRequest.Kind dependencyKind) {
    switch (dependencyKind) {
      case LAZY:
        return Snippet.format("%s.create(%s)", ClassName.fromClass(DoubleCheckLazy.class),
            frameworkTypeMemberSelect);
      case INSTANCE:
      case FUTURE:
        return Snippet.format("%s.get()", frameworkTypeMemberSelect);
      case PROVIDER:
      case PRODUCER:
      case MEMBERS_INJECTOR:
        return Snippet.format("%s", frameworkTypeMemberSelect);
      default:
        throw new AssertionError();
    }
  }

  static ClassName factoryNameForProvisionBinding(ProvisionBinding binding) {
    TypeElement enclosingTypeElement = binding.bindingTypeElement();
    ClassName enclosingClassName = ClassName.fromTypeElement(enclosingTypeElement);
    switch (binding.bindingKind()) {
      case INJECTION:
      case PROVISION:
        return enclosingClassName.topLevelClassName().peerNamed(
            enclosingClassName.classFileName() + "_" + factoryPrefix(binding) + "Factory");
      case SYNTHETIC_PROVISON:
        throw new IllegalArgumentException();
      default:
        throw new AssertionError();
    }
  }

  /**
   * Returns the factory name parameterized with the ProvisionBinding's parameters (if necessary).
   */
  static TypeName parameterizedFactoryNameForProvisionBinding(
      ProvisionBinding binding) {
    ClassName factoryName = factoryNameForProvisionBinding(binding);
    List<TypeName> parameters = ImmutableList.of();
    if (binding.bindingType().equals(BindingType.UNIQUE)) {
      switch(binding.bindingKind()) {
        case INJECTION:
          TypeName bindingName = TypeNames.forTypeMirror(binding.key().type());
          // If the binding is parameterized, parameterize the factory.
          if (bindingName instanceof ParameterizedTypeName) {
            parameters = ((ParameterizedTypeName) bindingName).parameters();
          }
          break;
        case PROVISION:
          // For provision bindings, we parameterize creation on the types of
          // the module, not the types of the binding.
          // Consider: Module<A, B, C> { @Provides List<B> provideB(B b) { .. }}
          // The binding is just parameterized on <B>, but we need all of <A, B, C>.
          if (!binding.bindingTypeElement().getTypeParameters().isEmpty()) {
            parameters = ((ParameterizedTypeName) TypeNames.forTypeMirror(
                binding.bindingTypeElement().asType())).parameters();
          }
          break;
        default: // fall through.
      }
    }
    return parameters.isEmpty() ? factoryName
        : ParameterizedTypeName.create(factoryName, parameters);
  }

  static ClassName factoryNameForProductionBinding(ProductionBinding binding) {
    TypeElement enclosingTypeElement = binding.bindingTypeElement();
    ClassName enclosingClassName = ClassName.fromTypeElement(enclosingTypeElement);
    switch (binding.bindingKind()) {
      case IMMEDIATE:
      case FUTURE_PRODUCTION:
        return enclosingClassName.topLevelClassName().peerNamed(
            enclosingClassName.classFileName() + "_" + factoryPrefix(binding) + "Factory");
      default:
        throw new AssertionError();
    }
  }

  /**
   * Returns the members injector's name parameterized with the binding's parameters (if necessary).
   */
  static TypeName parameterizedMembersInjectorNameForMembersInjectionBinding(
      MembersInjectionBinding binding) {
    ClassName factoryName = membersInjectorNameForMembersInjectionBinding(binding);
    TypeName bindingName = TypeNames.forTypeMirror(binding.key().type());
    // If the binding is parameterized, parameterize the MembersInjector.
    if (bindingName instanceof ParameterizedTypeName) {
      return ParameterizedTypeName.create(factoryName,
          ((ParameterizedTypeName) bindingName).parameters());
    }
    return factoryName;
  }

  static ClassName membersInjectorNameForMembersInjectionBinding(MembersInjectionBinding binding) {
    ClassName injectedClassName = ClassName.fromTypeElement(binding.bindingElement());
    return injectedClassName.topLevelClassName().peerNamed(
        injectedClassName.classFileName() + "_MembersInjector");
  }

  private static String factoryPrefix(ProvisionBinding binding) {
    switch (binding.bindingKind()) {
      case INJECTION:
        return "";
      case PROVISION:
        return CaseFormat.LOWER_CAMEL.to(UPPER_CAMEL,
            ((ExecutableElement) binding.bindingElement()).getSimpleName().toString());
      default:
        throw new IllegalArgumentException();
    }
  }

  private static String factoryPrefix(ProductionBinding binding) {
    switch (binding.bindingKind()) {
      case IMMEDIATE:
      case FUTURE_PRODUCTION:
        return CaseFormat.LOWER_CAMEL.to(UPPER_CAMEL,
            ((ExecutableElement) binding.bindingElement()).getSimpleName().toString());
      default:
        throw new IllegalArgumentException();
    }
  }

  private SourceFiles() {}
}

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

import com.google.common.base.CaseFormat;
import com.google.common.collect.BiMap;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.google.common.collect.SetMultimap;
import com.squareup.javawriter.JavaWriter;
import dagger.Lazy;
import dagger.MembersInjector;
import dagger.internal.DoubleCheckLazy;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import javax.inject.Provider;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementKindVisitor6;

import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static javax.lang.model.element.ElementKind.PACKAGE;

/**
 * Utilities for generating files.
 *
 * @author Gregory Kick
 * @since 2.0
 */
class SourceFiles {
  /**
   * Given a mapping from variable name to type, returns a list of tokens suitable for methods such
   * as {@link JavaWriter#beginMethod(String, String, java.util.Set, String...)}.
   */
  // TODO(gak): push this change upstream to obviate the need for this utility
  static ImmutableList<String> flattenVariableMap(Map<String, String> variableMap) {
    ImmutableList.Builder<String> tokenList = ImmutableList.builder();
    for (Entry<String, String> variableEntry : variableMap.entrySet()) {
      tokenList.add(variableEntry.getValue(), variableEntry.getKey());
    }
    return tokenList.build();
  }

  /**
   * Returns the sorted set of all classes required by the {@link MembersInjector} implementation
   * being generated.
   */
  static ImmutableSortedSet<ClassName> collectImportsFromDependencies(ClassName topLevelClassName,
      Iterable<? extends DependencyRequest> dependencies) {
    ImmutableSortedSet.Builder<ClassName> builder = ImmutableSortedSet.<ClassName>naturalOrder();
    ImmutableSet<String> packagesToSkip  =
        ImmutableSet.of("java.lang", topLevelClassName.packageName());
    for (DependencyRequest dependency : dependencies) {
      ImmutableSet<TypeElement> referencedTypes =
          MoreTypes.referencedTypes(dependency.key().type());
      switch (dependency.kind()) {
        case LAZY:
          builder.add(ClassName.fromClass(Lazy.class), ClassName.fromClass(DoubleCheckLazy.class));
          // fall through
        case INSTANCE:
        case PROVIDER:
          builder.add(ClassName.fromClass(Provider.class));
          break;
        default:
          throw new AssertionError();
      }
      for (TypeElement referencedType : referencedTypes) {
        ClassName className = ClassName.fromTypeElement(referencedType);
        // don't include classes in java.lang or the same package
        if (!packagesToSkip.contains(className.packageName())
            // or that are members of the same top-level class
            && !className.nameOfTopLevelClass().equals(topLevelClassName)) {
          builder.add(className);
        }
      }
    }
    return builder.build();
  }

  static ImmutableSortedSet<ClassName> originatingClassNames(Iterable<? extends Element> elements) {
    // Deeply nested types need to import their originating
    ImmutableSortedSet.Builder<ClassName> builder = ImmutableSortedSet.<ClassName>naturalOrder();
    for (Element element : elements) {
      switch (element.getKind()) {
        case METHOD:
        case CONSTRUCTOR:
        case FIELD:
          element = element.getEnclosingElement();
        case CLASS:
          Element enclosing = element.getEnclosingElement();
          if (!enclosing.getKind().equals(PACKAGE)) {
            builder.add(ClassName.fromTypeElement((TypeElement) element));
          }
          break;
        default:
          break;
      }
    }
    return builder.build();
  }

  /**
   * Sorts {@link DependencyRequest} instances in an order likely to reflect their logical
   * importance.
   */
  static final Ordering<DependencyRequest> DEPENDENCY_ORDERING =
      new Ordering<DependencyRequest>() {
        @Override public int compare(DependencyRequest left, DependencyRequest right) {
          return ComparisonChain.start()
              // put fields before parameters
              .compare(left.requestElement().getKind(), right.requestElement().getKind())
              // order by dependency kind
              .compare(left.kind(), right.kind())
              // then sort by name
              .compare(
                  left.requestElement().getSimpleName().toString(),
                  right.requestElement().getSimpleName().toString())
              .result();
        }
      };

  /**
   * This method generates names for the {@link Provider} references necessary for all of the
   * bindings. It is responsible for the following:
   * <ul>
   * <li>Choosing a name that associates the provider with all of the dependency requests for this
   * type.
   * <li>Choosing a name that is <i>probably</i> associated with the type being provided.
   * <li>Ensuring that no two providers end up with the same name.
   * </ul>
   *
   * @return Returns the mapping from {@link Key} to provider name sorted by the name of the
   * provider.
   */
  static ImmutableBiMap<Key, String> generateProviderNamesForDependencies(
      Iterable<? extends DependencyRequest> dependencies) {
    ImmutableSetMultimap.Builder<Key, DependencyRequest> dependenciesByKeyBuilder =
        new ImmutableSetMultimap.Builder<Key, DependencyRequest>()
            .orderValuesBy(DEPENDENCY_ORDERING);
    for (DependencyRequest dependency : dependencies) {
      dependenciesByKeyBuilder.put(dependency.key(), dependency);
    }
    ImmutableSetMultimap<Key, DependencyRequest> dependenciesByKey =
        dependenciesByKeyBuilder.build();
    Map<Key, Collection<DependencyRequest>> dependenciesByKeyMap = dependenciesByKey.asMap();
    BiMap<Key, String> providerNames = HashBiMap.create(dependenciesByKeyMap.size());
    for (Entry<Key, Collection<DependencyRequest>> entry : dependenciesByKeyMap.entrySet()) {
      // collect together all of the names that we would want to call the provider
      ImmutableSet<String> dependencyNames = FluentIterable.from(entry.getValue())
          .transform(new DependencyVariableNamer())
          .toSet();

      final String baseProviderName;
      if (dependencyNames.size() == 1) {
        // if there's only one name, great!  use it!
        String name = Iterables.getOnlyElement(dependencyNames);
        baseProviderName = name.endsWith("Provider") ? name : name + "Provider";
      } else {
        // in the event that a provider is being used for a bunch of deps with different names,
        // add all the names together with "And"s in the middle.  E.g.: stringAndS
        Iterator<String> namesIterator = dependencyNames.iterator();
        String first = namesIterator.next();
        StringBuilder compositeNameBuilder = new StringBuilder(first);
        while (namesIterator.hasNext()) {
          compositeNameBuilder.append("And")
              .append(CaseFormat.LOWER_CAMEL.to(UPPER_CAMEL, namesIterator.next()));
        }
        baseProviderName = compositeNameBuilder.append("Provider").toString();
      }

      // in the unlikely event that we have more that one provider with the exact same name,
      // just add numbers at the end until there is no collision
      String candidateName = baseProviderName;
      for (int candidateNum = 2; providerNames.containsValue(candidateName); candidateNum++) {
        candidateName = baseProviderName + candidateNum;
      }

      providerNames.put(entry.getKey(), candidateName);
    }
    // return the map so that it is sorted by name
    return ImmutableBiMap.copyOf(ImmutableSortedMap.copyOf(providerNames.inverse())).inverse();
  }

  // TODO(gak): this needs to suck less
  static ImmutableBiMap<Key, String> generateProviderNamesForBindings(
      SetMultimap<Key, ProvisionBinding> bindings) {
    BiMap<Key, String> providerNames = HashBiMap.create(bindings.size());
    for (Entry<Key, Collection<ProvisionBinding>> entry : bindings.asMap().entrySet()) {
      Collection<ProvisionBinding> bindingsForKey = entry.getValue();
      final String name;
      if (ProvisionBinding.isSetBindingCollection(bindingsForKey)) {
        name = new KeyVariableNamer().apply(entry.getKey()) + "Provider";
      } else {
        ProvisionBinding binding = Iterables.getOnlyElement(bindingsForKey);
        switch (binding.bindingKind()) {
          case INJECTION:
          case PROVISION:
            name = binding.bindingElement().accept(
                new ElementKindVisitor6<String, Void>() {
                  @Override
                  public String visitExecutableAsConstructor(ExecutableElement e, Void p) {
                    return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL,
                        e.getEnclosingElement().getSimpleName().toString());
                  }

                  @Override
                  public String visitExecutableAsMethod(ExecutableElement e, Void p) {
                    return e.getSimpleName().toString();
                  }
                }, null) + "Provider";
            break;
          default:
            throw new AssertionError();
        }
      }
      providerNames.put(entry.getKey(), name);
    }
    // return the map so that it is sorted by name
    return ImmutableBiMap.copyOf(ImmutableSortedMap.copyOf(providerNames.inverse())).inverse();
  }

  static String providerUsageStatement(String providerName,
      DependencyRequest.Kind dependencyKind) {
    switch (dependencyKind) {
      case LAZY:
        return String.format("%s.create(%s)",
            DoubleCheckLazy.class.getSimpleName(), providerName);
      case INSTANCE:
        return String.format("%s.get()", providerName);
      case PROVIDER:
        return String.format("%s", providerName);
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
        return enclosingClassName.peerNamed(
            enclosingClassName.simpleName() + "$$" + factoryPrefix(binding) + "Factory");
      default:
        throw new AssertionError();
    }
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

  private SourceFiles() {}
}

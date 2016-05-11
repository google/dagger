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
package dagger.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Provider;

import static dagger.internal.Collections.newLinkedHashSetWithExpectedSize;
import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableSet;

/**
 * A {@link Factory} implementation used to implement {@link Set} bindings. This factory always
 * returns a new {@link Set} instance for each call to {@link #get} (as required by {@link Factory})
 * whose elements are populated by subsequent calls to their {@link Provider#get} methods.
 *
 * @author Gregory Kick
 * @since 2.0
 */
public final class SetFactory<T> implements Factory<Set<T>> {
  /**
   * A message for NPEs that trigger on bad argument lists.
   */
  private static final String ARGUMENTS_MUST_BE_NON_NULL =
      "SetFactory.create() requires its arguments to be non-null";

  private static final Factory<Set<Object>> EMPTY_FACTORY =
      new Factory<Set<Object>>() {
        @Override
        public Set<Object> get() {
          return emptySet();
        }
      };

  @SuppressWarnings({"unchecked", "rawtypes"}) // safe covariant cast
  public static <T> Factory<Set<T>> create() {
    return (Factory) EMPTY_FACTORY;
  }

  /**
   * Returns the supplied factory.  If there's just one factory, there's no need to wrap it or its
   * result.
   */
  public static <T> Factory<Set<T>> create(Factory<Set<T>> factory) {
    assert factory != null : ARGUMENTS_MUST_BE_NON_NULL;
    return factory;
  }

  /**
   * Returns a new factory that creates {@link Set} instances that form the union of the given
   * {@link Provider} instances.  Callers must not modify the providers array after invoking this
   * method; no copy is made.
   */
  public static <T> Factory<Set<T>> create(
      @SuppressWarnings("unchecked") Provider<Set<T>>... providers) {
    assert providers != null : ARGUMENTS_MUST_BE_NON_NULL;

    List<Provider<Set<T>>> contributingProviders = Arrays.asList(providers);

    assert !contributingProviders.contains(null)
        : "Codegen error?  Null within provider list.";
    assert !hasDuplicates(contributingProviders)
        : "Codegen error?  Duplicates in the provider list";

    return new SetFactory<T>(contributingProviders);
  }

  /**
   * Returns true if at least one pair of items in (@code original) are equals.
   */
  private static boolean hasDuplicates(List<? extends Object> original) {
    Set<Object> asSet = new HashSet<Object>(original);
    return original.size() != asSet.size();
  }

  private final List<Provider<Set<T>>> contributingProviders;

  private SetFactory(List<Provider<Set<T>>> contributingProviders) {
    this.contributingProviders = contributingProviders;
  }

  /**
   * Returns a {@link Set} whose iteration order is that of the elements given by each of the
   * providers, which are invoked in the order given at creation.
   *
   * @throws NullPointerException if any of the delegate {@link Set} instances or elements therein
   *     are {@code null}
   */
  @Override
  public Set<T> get() {
    int size = 0;

    // Profiling revealed that this method was a CPU-consuming hotspot in some applications, so
    // these loops were changed to use c-style for.  Versus enhanced for-each loops, C-style for is
    // faster for ArrayLists, at least through Java 8.

    List<Set<T>> providedSets = new ArrayList<Set<T>>(contributingProviders.size());
    for (int i = 0, c = contributingProviders.size(); i < c; i++) {
      Provider<Set<T>> provider = contributingProviders.get(i);
      Set<T> providedSet = provider.get();
      if (providedSet == null) {
        throw new NullPointerException(provider + " returned null");
      }
      providedSets.add(providedSet);
      size += providedSet.size();
    }

    Set<T> result = newLinkedHashSetWithExpectedSize(size);
    for (int i = 0, c = providedSets.size(); i < c; i++) {
      for (T element : providedSets.get(i)) {
        if (element == null) {
          throw new NullPointerException("a null element was provided");
        }
        result.add(element);
      }
    }
    return unmodifiableSet(result);
  }
}

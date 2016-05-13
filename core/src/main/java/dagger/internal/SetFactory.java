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

import static dagger.internal.Collections.newHashSetWithExpectedSize;
import static dagger.internal.Preconditions.checkNotNull;
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

  public static <T> SetFactory.Builder<T> builder() {
    return new Builder<T>();
  }

  public static final class Builder<T> {
    private final List<Provider<T>> individualProviders = new ArrayList<Provider<T>>();
    private final List<Provider<Set<T>>> setProviders = new ArrayList<Provider<Set<T>>>();

    public Builder<T> addProvider(Provider<T> individualProvider) {
      assert individualProvider != null : "Codegen error? Null provider";
      individualProviders.add(individualProvider);
      return this;
    }

    public Builder<T> addSetProvider(Provider<Set<T>> multipleProvider) {
      assert multipleProvider != null : "Codegen error? Null provider";
      setProviders.add(multipleProvider);
      return this;
    }

    public SetFactory<T> build() {
      assert !hasDuplicates(individualProviders)
          : "Codegen error?  Duplicates in the provider list";
      assert !hasDuplicates(setProviders)
          : "Codegen error?  Duplicates in the provider list";

      return new SetFactory<T>(
          new ArrayList<Provider<T>>(individualProviders),
          new ArrayList<Provider<Set<T>>>(setProviders));
    }
  }

  /**
   * Returns true if at least one pair of items in (@code original) are equals.
   */
  private static boolean hasDuplicates(List<? extends Object> original) {
    Set<Object> asSet = new HashSet<Object>(original);
    return original.size() != asSet.size();
  }

  private final List<Provider<T>> individualProviders;
  private final List<Provider<Set<T>>> setProviders;

  private SetFactory(List<Provider<T>> individualProviders, List<Provider<Set<T>>> setProviders) {
    this.individualProviders = individualProviders;
    this.setProviders = setProviders;
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
    int size = individualProviders.size();
    // Profiling revealed that this method was a CPU-consuming hotspot in some applications, so
    // these loops were changed to use c-style for.  Versus enhanced for-each loops, C-style for is
    // faster for ArrayLists, at least through Java 8.

    List<Set<T>> providedSets = new ArrayList<Set<T>>(setProviders.size());
    for (int i = 0, c = setProviders.size(); i < c; i++) {
      Set<T> providedSet = setProviders.get(i).get();
      size += providedSet.size();
      providedSets.add(providedSet);
    }

    Set<T> providedValues = newHashSetWithExpectedSize(size);
    for (int i = 0, c = individualProviders.size(); i < c; i++) {
      providedValues.add(checkNotNull(individualProviders.get(i).get()));
    }
    for (int i = 0, c = providedSets.size(); i < c; i++) {
      for (T element : providedSets.get(i)) {
        providedValues.add(checkNotNull(element));
      }
    }

    return unmodifiableSet(providedValues);
  }
}

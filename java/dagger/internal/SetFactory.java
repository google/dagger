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

package dagger.internal;

import static dagger.internal.DaggerCollections.hasDuplicates;
import static dagger.internal.DaggerCollections.newHashSetWithExpectedSize;
import static dagger.internal.DaggerCollections.presizedList;
import static dagger.internal.Preconditions.checkNotNull;
import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.inject.Provider;

/**
 * A {@link Factory} implementation used to implement {@link Set} bindings. This factory always
 * returns a new {@link Set} instance for each call to {@link #get} (as required by {@link Factory})
 * whose elements are populated by subsequent calls to their {@link Provider#get} methods.
 */
public final class SetFactory<T> implements Factory<Set<T>> {
  private static final Factory<Set<Object>> EMPTY_FACTORY = InstanceFactory.create(emptySet());

  @SuppressWarnings({"unchecked", "rawtypes"}) // safe covariant cast
  public static <T> Factory<Set<T>> empty() {
    return (Factory) EMPTY_FACTORY;
  }

  /**
   * Constructs a new {@link Builder} for a {@link SetFactory} with {@code individualProviderSize}
   * individual {@code Provider<T>} and {@code collectionProviderSize} {@code
   * Provider<Collection<T>>} instances.
   */
  public static <T> Builder<T> builder(int individualProviderSize, int collectionProviderSize) {
    return new Builder<T>(individualProviderSize, collectionProviderSize);
  }

  /**
   * A builder to accumulate {@code Provider<T>} and {@code Provider<Collection<T>>} instances.
   * These are only intended to be single-use and from within generated code. Do <em>NOT</em> add
   * providers after calling {@link #build()}.
   */
  public static final class Builder<T> {
    private final List<Provider<T>> individualProviders;
    private final List<Provider<Collection<T>>> collectionProviders;

    private Builder(int individualProviderSize, int collectionProviderSize) {
      individualProviders = presizedList(individualProviderSize);
      collectionProviders = presizedList(collectionProviderSize);
    }

    @SuppressWarnings("unchecked")
    public Builder<T> addProvider(Provider<? extends T> individualProvider) {
      assert individualProvider != null : "Codegen error? Null provider";
      // TODO(ronshapiro): Store a List<? extends Provider<T>> and avoid the cast to Provider<T>
      individualProviders.add((Provider<T>) individualProvider);
      return this;
    }

    @SuppressWarnings("unchecked")
    public Builder<T> addCollectionProvider(
        Provider<? extends Collection<? extends T>> collectionProvider) {
      assert collectionProvider != null : "Codegen error? Null provider";
      collectionProviders.add((Provider<Collection<T>>) collectionProvider);
      return this;
    }

    public SetFactory<T> build() {
      assert !hasDuplicates(individualProviders)
          : "Codegen error?  Duplicates in the provider list";
      assert !hasDuplicates(collectionProviders)
          : "Codegen error?  Duplicates in the provider list";

      return new SetFactory<T>(individualProviders, collectionProviders);
    }
  }

  private final List<Provider<T>> individualProviders;
  private final List<Provider<Collection<T>>> collectionProviders;

  private SetFactory(
      List<Provider<T>> individualProviders, List<Provider<Collection<T>>> collectionProviders) {
    this.individualProviders = individualProviders;
    this.collectionProviders = collectionProviders;
  }

  /**
   * Returns a {@link Set} that contains the elements given by each of the providers.
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

    List<Collection<T>> providedCollections =
        new ArrayList<Collection<T>>(collectionProviders.size());
    for (int i = 0, c = collectionProviders.size(); i < c; i++) {
      Collection<T> providedCollection = collectionProviders.get(i).get();
      size += providedCollection.size();
      providedCollections.add(providedCollection);
    }

    Set<T> providedValues = newHashSetWithExpectedSize(size);
    for (int i = 0, c = individualProviders.size(); i < c; i++) {
      providedValues.add(checkNotNull(individualProviders.get(i).get()));
    }
    for (int i = 0, c = providedCollections.size(); i < c; i++) {
      for (T element : providedCollections.get(i)) {
        providedValues.add(checkNotNull(element));
      }
    }

    return unmodifiableSet(providedValues);
  }
}

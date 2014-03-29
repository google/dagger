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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import javax.inject.Provider;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * A simple repository for {@link Provider} {@link DeclaredType types} for a given {@link Key}. For
 * example, a key for {@code @Named("foo") Set<String>} would return the type representing
 * {@code Provider<Set<String>>}.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class ProviderTypeRepository {
  private final LoadingCache<Key, DeclaredType> providerTypeCache;

  ProviderTypeRepository(final Elements elements, final Types types) {
    checkNotNull(elements);
    checkNotNull(types);
    this.providerTypeCache = CacheBuilder.newBuilder()
        .concurrencyLevel(1)
        .softValues() // just to make sure we don't OOME the compiler
        .build(new CacheLoader<Key, DeclaredType>() {
          TypeElement providerTypeElement =
              elements.getTypeElement(Provider.class.getCanonicalName());

          @Override public DeclaredType load(Key key) {
            return types.getDeclaredType(providerTypeElement, key.type());
          }
        });
  }

  DeclaredType getProviderType(Key key) {
    checkNotNull(key);
    return providerTypeCache.getUnchecked(key);
  }
}

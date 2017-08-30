/*
 * Copyright (C) 2017 The Dagger Authors.
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

package dagger.functional;

import dagger.Component;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;
import dagger.multibindings.StringKey;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * Bindings that use {@code Lazy<T>} as the value in a multibound map. A regression was uncovered
 * when using {@code MapType.valuesAreFrameworkType()}, which treats {@link Lazy} as a framework
 * type and incorrectly suggested {@link dagger.internal.MapProviderFactory} for a {@code Map<K,
 * Lazy<V>>} instead of a plain {@link dagger.internal.MapFactory}. See b/65084589.
 */
class LazyMaps {
  @Module
  abstract static class TestModule {
    @Provides
    @Singleton
    static AtomicInteger provideAtomicInteger() {
      return new AtomicInteger();
    }

    @Provides
    static String provideString(AtomicInteger atomicInteger) {
      return "value-" + atomicInteger.incrementAndGet();
    }

    @Provides
    @IntoMap
    @StringKey("key")
    static Lazy<String> mapContribution(Lazy<String> lazy) {
      return lazy;
    }

    /* TODO(b/65118638) Replace once @Binds @IntoMap Lazy<T> methods work properly.
    @Binds
    @IntoMap
    @StringKey("binds-key")
    abstract Lazy<String> mapContributionAsBinds(Lazy<String> lazy);
    */
  }

  @Singleton
  @Component(modules = TestModule.class)
  interface TestComponent {
    Map<String, Lazy<String>> mapOfLazy();

    Map<String, Provider<Lazy<String>>> mapOfProviderOfLazy();

    Provider<Map<String, Lazy<String>>> providerForMapOfLazy();

    Provider<Map<String, Provider<Lazy<String>>>> providerForMapOfProviderOfLazy();
  }
}

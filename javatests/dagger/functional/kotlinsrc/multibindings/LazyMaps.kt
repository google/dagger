/*
 * Copyright (C) 2023 The Dagger Authors.
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

package dagger.functional.kotlinsrc.multibindings

import dagger.Binds
import dagger.Component
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Bindings that use `Lazy<T>` as the value in a multibound map. A regression was uncovered when
 * using `MapType.valuesAreFrameworkType()`, which treats [Lazy] as a framework type and incorrectly
 * suggested [dagger.internal.MapProviderFactory] for a `Map<K, Lazy<V>>` instead of a plain
 * [dagger.internal.MapFactory]. See b/65084589.
 */
class LazyMaps {
  @Module
  abstract class TestModule {

    @Module
    companion object {
      @Provides @Singleton fun provideAtomicInteger(): AtomicInteger = AtomicInteger()

      @Provides
      fun provideString(atomicInteger: AtomicInteger): String =
        "value-${atomicInteger.incrementAndGet()}"

      @Provides @IntoMap @StringKey("key") fun mapContribution(string: String): String = string
    }

    @Binds
    @IntoMap
    @StringKey("binds-key")
    abstract fun mapContributionAsBinds(string: String): String
  }

  @Singleton
  @Component(modules = [TestModule::class])
  interface TestComponent {
    fun mapOfLazy(): Map<String, Lazy<String>>

    fun mapOfProviderOfLazy(): Map<String, Provider<Lazy<String>>>

    fun providerForMapOfLazy(): Provider<Map<String, Lazy<String>>>

    fun providerForMapOfProviderOfLazy(): Provider<Map<String, Provider<Lazy<String>>>>
  }
}

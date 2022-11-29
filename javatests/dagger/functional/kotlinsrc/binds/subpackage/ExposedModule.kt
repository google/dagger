/*
 * Copyright (C) 2022 The Dagger Authors.
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

package dagger.functional.kotlinsrc.binds.subpackage

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.ElementsIntoSet
import dagger.multibindings.IntoSet
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.jvm.JvmWildcard

@Module
abstract class ExposedModule {
  @Binds internal abstract fun notExposed(notExposed: NotExposed): Exposed

  // TODO(b/260626101): @JvmWildcard is needed for KAPT to generate List<? extends Exposed>.
  @Binds
  internal abstract fun bindList(notExposedList: List<NotExposed>): List<@JvmWildcard Exposed>

  @Binds
  internal abstract fun bindExposedInjectsMembers(
    notExposedInjectsMembers: NotExposedInjectsMembers
  ): ExposedInjectsMembers

  @Binds
  @ElementsIntoSet
  internal abstract fun bindCollectionOfNotExposeds(
    collection: Collection<NotExposed>
  ): Set<NotExposed>

  companion object {
    @Provides
    @Singleton // force a rawtypes Provider
    internal fun notExposedList(): List<NotExposed> = listOf(NotExposed())

    @Provides
    internal fun provideNotExposedCollection(notExposed: NotExposed): Collection<NotExposed> {
      return listOf(notExposed) as Collection<NotExposed>
    }

    @Provides
    @IntoSet // This is needed to ensure a provider field gets created for providing the Collection.
    internal fun provideNotExposed(
      collectionProvider: Provider<Collection<NotExposed>>
    ): NotExposed {
      return collectionProvider.get().iterator().next()
    }

    @Provides
    internal fun provideString(setOfFoo: Set<NotExposed>): String = "not exposed"
  }
}

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

package dagger.functional.kotlinsrc.binds

import dagger.Component
import dagger.functional.kotlinsrc.binds.subpackage.Exposed
import dagger.functional.kotlinsrc.binds.subpackage.ExposedModule
import dagger.functional.kotlinsrc.binds.subpackage.UsesExposedInjectsMembers
import javax.inject.Provider
import javax.inject.Singleton

/**
 * This component tests cases where the right-hand-side of a [dagger.Binds] method is not accessible
 * from the component, but the left-hand-side is. If the right-hand-side is represented as a
 * Provider (e.g. because it is scoped), then the raw `Provider.get()` will return [ ], which must
 * be downcasted to the type accessible from the component. See `instanceRequiresCast()` in
 * `DelegateRequestRepresentation`.
 */
@Singleton
@Component(modules = [ExposedModule::class])
internal interface AccessesExposedComponent {
  fun exposed(): Exposed

  fun exposedProvider(): Provider<Exposed>

  // TODO(b/260626101): @JvmWildcard is needed for KAPT to generate List<? extends Exposed>.
  fun listOfExposed(): List<@JvmWildcard Exposed>

  // TODO(b/260626101): @JvmWildcard is needed for KAPT to generate List<? extends Exposed>.
  fun providerOfListOfExposed(): Provider<List<@JvmWildcard Exposed>>

  fun usesExposedInjectsMembers(): UsesExposedInjectsMembers

  /**
   * This provider needs a `Provider<ExposedInjectsMembers>`, which is bound to a
   * `Provider<NotExposedInjectsMembers>`. This method is here to make sure that the cast happens
   * appropriately.
   */
  fun usesExposedInjectsMembersProvider(): Provider<UsesExposedInjectsMembers>
}

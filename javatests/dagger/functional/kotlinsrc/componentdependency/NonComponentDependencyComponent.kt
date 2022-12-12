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

package dagger.functional.kotlinsrc.componentdependency

import dagger.Component
import dagger.functional.kotlinsrc.componentdependency.subpackage.OtherThing
import javax.inject.Inject

@Component(dependencies = [NonComponentDependencyComponent.ThingComponent::class])
interface NonComponentDependencyComponent {

  fun thingTwo(): ThingTwo

  class ThingTwo
  @Inject
  internal constructor(
    @Suppress("UNUSED_PARAMETER") thing: Thing,
    @Suppress("UNUSED_PARAMETER") nonComponentDependencyComponent: NonComponentDependencyComponent,
    @Suppress("UNUSED_PARAMETER") thingComponent: ThingComponent
  )

  // A non-component interface which this interface depends upon.
  interface ThingComponent {
    fun thing(): Thing
  }

  // The implementation for that interface.
  class ThingComponentImpl : ThingComponent {
    override fun thing(): Thing {
      return Thing(OtherThing(1))
    }
  }
}

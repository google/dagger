/*
 * Copyright (C) 2026 The Dagger Authors.
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

package dagger.functional.kotlinsrc.multipackage.a

import dagger.Module
import dagger.Provides
import dagger.Subcomponent
import javax.inject.Inject
import javax.inject.Qualifier

internal interface InaccessibleBound<T : InaccessibleBound<T>> {
  fun boundValue(): String
}

internal interface OtherInaccessibleBound {
  fun otherValue(): String
}

internal class InaccessibleBoundImpl @Inject constructor() :
  InaccessibleBound<InaccessibleBoundImpl>, OtherInaccessibleBound {
  override fun boundValue(): String = "bound"

  override fun otherValue(): String = "other"
}

// Intentionally combines constructor and member injection to test both _Factory and
// _MembersInjector generation on an internal generic class.
@Suppress("BadInject")
internal class InternalGenericInjectClass<T> @Inject constructor(private val constructorDep: T)
  where T : InaccessibleBound<T>, T : OtherInaccessibleBound {
  @Inject lateinit var fieldDep: T
  private lateinit var methodDep: T

  @Inject
  fun injectMethod(methodDep: T) {
    this.methodDep = methodDep
  }

  fun summary(): String =
    "${constructorDep.boundValue()}:${fieldDep.otherValue()}:${methodDep.boundValue()}"
}

@Qualifier @Retention(AnnotationRetention.RUNTIME) annotation class BoundedModuleValue

@Module
internal abstract class InternalGenericBoundedModule<T>
  where T : InaccessibleBound<T>, T : OtherInaccessibleBound {
  @Provides
  @BoundedModuleValue
  fun provideBoundedValue(t: T): String = "${t.boundValue()}:${t.otherValue()}"
}

@Module
internal class InternalConcreteBoundedModule : InternalGenericBoundedModule<InaccessibleBoundImpl>()

@Subcomponent(modules = [InternalConcreteBoundedModule::class])
interface BoundedSubcomponent {
  @BoundedModuleValue fun boundedModuleValue(): String
}

class UsesInaccessibleBoundedGenerics
@Inject
internal constructor(private val genericInject: InternalGenericInjectClass<InaccessibleBoundImpl>) {
  fun summary(): String = genericInject.summary()
}

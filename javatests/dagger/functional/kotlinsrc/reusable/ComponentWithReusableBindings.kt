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

package dagger.functional.kotlinsrc.reusable

import dagger.Component
import dagger.Module
import dagger.Provides
import dagger.Reusable
import dagger.Subcomponent
import javax.inject.Provider
import javax.inject.Qualifier

@Component(modules = [ComponentWithReusableBindings.ReusableBindingsModule::class])
interface ComponentWithReusableBindings {
  @Qualifier annotation class InParent

  @Qualifier annotation class InChildren

  @InParent fun reusableInParent(): Any
  fun childOne(): ChildOne
  fun childTwo(): ChildTwo

  // b/77150738
  fun primitive(): Int

  // b/77150738: This is used as a regression test for fastInit mode's switching providers. In
  // particular, it occurs when a @Provides method returns the boxed type but the component method
  // returns the unboxed type, and the instance is requested from a SwitchingProvider.
  fun unboxedPrimitive(): Boolean

  // b/77150738
  fun booleanProvider(): Provider<Boolean>

  @Subcomponent
  interface ChildOne {
    @InParent fun reusableInParent(): Any

    @InChildren fun reusableInChild(): Any
  }

  @Subcomponent
  interface ChildTwo {
    @InParent fun reusableInParent(): Any

    @InChildren fun reusableInChild(): Any
  }

  @Module
  object ReusableBindingsModule {
    @Provides
    @Reusable
    @InParent
    fun inParent(): Any {
      return Any()
    }

    @Provides
    @Reusable
    @InChildren
    fun inChildren(): Any {
      return Any()
    }

    // b/77150738
    @Provides @Reusable fun primitive(): Int = 0

    // b/77150738
    @Provides @Reusable fun boxedPrimitive(): Boolean = false
  }
}

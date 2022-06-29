/*
 * Copyright (C) 2019 The Dagger Authors.
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

package dagger.functional.kotlin

import dagger.Component
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import javax.inject.Named

@Component(
  modules = [
    TestKotlinObjectModule::class,
    TestModuleForNesting.TestNestedKotlinObjectModule::class
  ]
)
interface TestKotlinComponentWithObjectModule {
  fun getDataA(): TestDataA
  @Named("nested-data-a")
  fun getDataAFromNestedModule(): TestDataA
  fun getDataB(): TestDataB
  fun getSetOfDataA(): Set<TestDataA>
  fun getPrimitiveType(): Boolean
}

@Module
object TestKotlinObjectModule {
  @Provides
  fun provideDataA() = TestDataA("test")

  @Provides
  fun providePrimitiveType(): Boolean = true

  @Provides
  @JvmStatic
  fun provideDataB() = TestDataB("test")

  @Provides
  @IntoSet
  fun provideIntoMapDataA() = TestDataA("set-test")
}

class TestModuleForNesting {
  @Module
  object TestNestedKotlinObjectModule {
    @Provides
    @Named("nested-data-a")
    fun provideDataA() = TestDataA("test")
  }
}

@Module
private object NonPublicObjectModule {
  @Provides
  fun provideInt() = 42
}

data class TestDataA(val data: String)
data class TestDataB(val data: String)

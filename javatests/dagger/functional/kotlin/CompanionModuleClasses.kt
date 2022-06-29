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

package dagger.functional.kotlin

import dagger.Binds
import dagger.Component
import dagger.Module
import dagger.Provides
import javax.inject.Inject
import javax.inject.Named

@Component(
  modules = [
    TestKotlinModuleWithCompanion::class,
    TestKotlinModuleWithNamedCompanion::class,
    TestKotlinAbstractModuleWithCompanion::class,
    TestKotlinWorkaroundModuleWithCompanion::class,
    TestKotlinModuleWithPrivateCompanion::class
  ]
)
interface TestKotlinComponentWithCompanionModule {
  fun getDataA(): TestDataA
  fun getDataB(): TestDataB
  fun getBoolean(): Boolean
  fun getStringType(): String
  @Named("Cat")
  fun getCatNamedStringType(): String
  @Named("Dog")
  fun getDogNamedStringType(): String

  fun getInterface(): TestInterface
  fun getLong(): Long
  fun getDouble(): Double
  fun getInteger(): Int
}

@Module
class TestKotlinModuleWithCompanion {
  @Provides
  fun provideDataA() = TestDataA("test")

  companion object {
    @Provides
    fun provideDataB() = TestDataB("test")

    @Provides
    fun provideBoolean(): Boolean = true
  }
}

@Module
class TestKotlinModuleWithNamedCompanion {

  @Provides
  @Named("Cat")
  fun provideNamedString() = "Cat"

  companion object Foo {
    @Provides
    fun provideStringType(): String = ""
  }
}

@Module
abstract class TestKotlinAbstractModuleWithCompanion {

  @Binds
  abstract fun bindInterface(injectable: TestInjectable): TestInterface

  companion object {
    @Provides
    fun provideLong() = 4L
  }
}

@Module
class TestKotlinWorkaroundModuleWithCompanion {

  @Provides
  fun provideDouble() = 1.0

  @Module
  companion object {
    @Provides
    @JvmStatic
    fun provideInteger() = 2
  }
}

@Module
class TestKotlinModuleWithPrivateCompanion {

  @Provides
  @Named("Dog")
  fun getNamedStringType() = "Dog"

  private companion object {
    fun randomFunction() = ""
  }
}

data class TestDataA(val data: String)
data class TestDataB(val data: String)

interface TestInterface
class TestInjectable @Inject constructor() : TestInterface


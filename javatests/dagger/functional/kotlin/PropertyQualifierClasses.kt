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
import dagger.functional.kotlin.processor.TriggerGeneratedTypeProcessor
import javax.inject.Inject
import javax.inject.Qualifier

@Component(modules = [TestKotlinModuleWithQualifier::class])
interface TestKotlinComponentWithQualifier {
  fun inject(testInjectedClassWithQualifier: TestMemberInjectedClassWithQualifier)
  fun inject(fooWithInjectedQualifier: FooWithInjectedQualifier)
}

@Module
class TestKotlinModuleWithQualifier {
  @Provides
  @JavaTestQualifier
  fun provideJavaDataA() = TestDataA("test")

  @Provides
  @JavaTestQualifier
  fun provideJavaDataB() = TestDataB("test")

  @Provides
  @JavaTestQualifierWithTarget
  fun provideJavaWithTargetDataA() = TestDataA("test")

  @Provides
  @KotlinTestQualifier
  fun provideKotlinDataA() = TestDataA("test")

  @Provides
  @JavaTestQualifier
  fun provideString() = "qualified string"
}

class TestConstructionInjectedClassWithQualifier @Inject constructor(
  @JavaTestQualifier val data: TestDataA
)

@TriggerGeneratedTypeProcessor
class TestMemberInjectedClassWithQualifier {
  @Inject
  @JavaTestQualifier
  lateinit var javaDataA: TestDataA

  @Inject
  @field:JavaTestQualifier
  lateinit var javaDataB: TestDataB

  @Inject
  @JavaTestQualifierWithTarget
  lateinit var javaWithTargetDataA: TestDataA

  @Inject
  @JavaTestQualifier
  lateinit var kotlinDataA: TestDataA

  @Inject
  lateinit var dataWithConstructionInjection: TestConstructionInjectedClassWithQualifier

  val noBackingFieldProperty: Int
    get() = 0

  val delegatedProperty by lazy { "" }

  val generatedTypeProperty = dagger.functional.kotlin.GeneratedType()

  val generatedTypeDelegatedProperty by lazy { dagger.functional.kotlin.GeneratedType() }
}

data class TestDataA(val data: String)
data class TestDataB(val data: String)

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class KotlinTestQualifier

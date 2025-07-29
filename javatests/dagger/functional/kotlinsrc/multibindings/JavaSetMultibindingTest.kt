/*
 * Copyright (C) 2025 The Dagger Authors.
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

import com.google.common.truth.Truth.assertThat
import dagger.Component
import dagger.Module
import dagger.Provides
import dagger.multibindings.ElementsIntoSet
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import javax.inject.Inject
import javax.inject.Qualifier
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") // This is intentional for this test.
@RunWith(JUnit4::class)
class JavaSetMultibindingTest {
  @Qualifier annotation class ProvidesJavaSet
  @Qualifier annotation class ProvidesKotlinSet

  @Component(modules = [TestModule::class])
  interface TestComponent {
    @ProvidesJavaSet
    fun getJavaSetAsKotlin(): Set<String>

    @ProvidesJavaSet
    fun getJavaSetAsJava(): java.util.Set<String>

    @ProvidesKotlinSet
    fun getKotlinSetAsKotlin(): Set<String>

    @ProvidesKotlinSet
    fun getKotlinSetAsJava(): java.util.Set<String>

    fun getUsage(): Usage
  }

  @Module
  interface TestModule {
    @Multibinds
    @ProvidesJavaSet
    fun provideJavaSet(): java.util.Set<String>

    @Multibinds
    @ProvidesKotlinSet
    fun provideKotlinSet(): Set<String>

    companion object {
      @Provides
      @IntoSet
      @ProvidesJavaSet
      fun provideIntoJavaSet(): String {
        return "provideIntoJavaSet"
      }

      @Suppress("UNCHECKED_CAST")
      @Provides
      @ElementsIntoSet
      @ProvidesJavaSet
      fun provideElementsIntoJavaSetAsJava(): java.util.Set<String> {
        return setOf("provideElementsIntoJavaSetAsJava") as java.util.Set<String>
      }

      @Provides
      @ElementsIntoSet
      @ProvidesJavaSet
      fun provideElementsIntoJavaSetAsKotlin(): Set<String> {
        return setOf("provideElementsIntoJavaSetAsKotlin")
      }

      @Provides
      @IntoSet
      @ProvidesKotlinSet
      fun provideIntoKotlinSet(): String {
        return "provideIntoKotlinSet"
      }

      @Suppress("UNCHECKED_CAST")
      @Provides
      @ElementsIntoSet
      @ProvidesKotlinSet
      fun provideElementsIntoKotlinSetAsJava(): java.util.Set<String> {
        return setOf("provideElementsIntoKotlinSetAsJava") as java.util.Set<String>
      }

      @Provides
      @ElementsIntoSet
      @ProvidesKotlinSet
      fun provideElementsIntoKotlinSetAsKotlin(): Set<String> {
        return setOf("provideElementsIntoKotlinSetAsKotlin")
      }
    }
  }

  class Usage @Inject constructor(
    @ProvidesJavaSet val javaSetAsJava: java.util.Set<String>,
    @ProvidesJavaSet val javaSetAsKotlin: Set<String>,
    @ProvidesKotlinSet val kotlinSetAsJava: java.util.Set<String>,
    @ProvidesKotlinSet val kotlinSetAsKotlin: Set<String>,
  )

  val testComponent: TestComponent = DaggerJavaSetMultibindingTest_TestComponent.create()

  @Test
  fun testJavaSetAsJava() {
    assertThat(testComponent.getJavaSetAsJava()).containsExactlyElementsIn(JAVA_SET)
    assertThat(testComponent.getJavaSetAsKotlin()).containsExactlyElementsIn(JAVA_SET)
    assertThat(testComponent.getUsage().javaSetAsJava).containsExactlyElementsIn(JAVA_SET)
    assertThat(testComponent.getUsage().javaSetAsKotlin).containsExactlyElementsIn(JAVA_SET)
  }

  @Test
  fun testKotlinSet() {
    assertThat(testComponent.getKotlinSetAsJava()).containsExactlyElementsIn(KOTLIN_SET)
    assertThat(testComponent.getKotlinSetAsKotlin()).containsExactlyElementsIn(KOTLIN_SET)
    assertThat(testComponent.getUsage().kotlinSetAsJava).containsExactlyElementsIn(KOTLIN_SET)
    assertThat(testComponent.getUsage().kotlinSetAsKotlin).containsExactlyElementsIn(KOTLIN_SET)
  }

  companion object {
    val JAVA_SET = setOf(
      "provideIntoJavaSet",
      "provideElementsIntoJavaSetAsJava",
      "provideElementsIntoJavaSetAsKotlin",
    )
    val KOTLIN_SET = setOf(
      "provideIntoKotlinSet", 
      "provideElementsIntoKotlinSetAsJava",
      "provideElementsIntoKotlinSetAsKotlin",
    )
  }
}

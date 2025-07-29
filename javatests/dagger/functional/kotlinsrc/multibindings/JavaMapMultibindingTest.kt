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
import dagger.multibindings.IntoMap
import dagger.multibindings.Multibinds
import dagger.multibindings.StringKey
import javax.inject.Inject
import javax.inject.Qualifier
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") // This is intentional for this test.
@RunWith(JUnit4::class)
class JavaMapMultibindingTest {
  @Qualifier annotation class ProvidesJavaMap
  @Qualifier annotation class ProvidesKotlinMap

  @Component(modules = [TestModule::class])
  interface TestComponent {
    @ProvidesJavaMap
    fun getJavaMapAsKotlin(): Map<String, String>

    @ProvidesJavaMap
    fun getJavaMapAsJava(): java.util.Map<String, String>

    @ProvidesKotlinMap
    fun getKotlinMapAsKotlin(): Map<String, String>

    @ProvidesKotlinMap
    fun getKotlinMapAsJava(): java.util.Map<String, String>

    fun getUsage(): Usage
  }

  @Module
  interface TestModule {
    @Multibinds
    @ProvidesJavaMap
    fun provideJavaMap(): java.util.Map<String, String>

    @Multibinds
    @ProvidesKotlinMap
    fun provideKotlinMap(): Map<String, String>

    companion object {
      @Provides
      @ProvidesJavaMap
      @IntoMap
      @StringKey("provideIntoJavaMap-key")
      fun provideIntoJavaMap(): String {
        return "provideIntoJavaMap-value"
      }

      @Provides
      @ProvidesKotlinMap
      @IntoMap
      @StringKey("provideIntoKotlinMap-key")
      fun provideIntoKotlinMap(): String {
        return "provideIntoKotlinMap-value"
      }
    }
  }

  class Usage @Inject constructor(
    @ProvidesJavaMap val javaMapAsJava: java.util.Map<String, String>,
    @ProvidesJavaMap val javaMapAsKotlin: Map<String, String>,
    @ProvidesKotlinMap val kotlinMapAsJava: java.util.Map<String, String>,
    @ProvidesKotlinMap val kotlinMapAsKotlin: Map<String, String>,
  )

  val testComponent: TestComponent = DaggerJavaMapMultibindingTest_TestComponent.create()

  @Test
  fun testJavaMapAsJava() {
    assertThat(testComponent.getJavaMapAsJava().toKotlin()).containsExactlyEntriesIn(JAVA_MAP)
    assertThat(testComponent.getJavaMapAsKotlin()).containsExactlyEntriesIn(JAVA_MAP)
    assertThat(testComponent.getUsage().javaMapAsJava.toKotlin()).containsExactlyEntriesIn(JAVA_MAP)
    assertThat(testComponent.getUsage().javaMapAsKotlin).containsExactlyEntriesIn(JAVA_MAP)
  }

  @Test
  fun testKotlinMap() {
    assertThat(testComponent.getKotlinMapAsJava().toKotlin()).containsExactlyEntriesIn(KOTLIN_MAP)
    assertThat(testComponent.getKotlinMapAsKotlin()).containsExactlyEntriesIn(KOTLIN_MAP)
    assertThat(testComponent.getUsage().kotlinMapAsJava.toKotlin())
      .containsExactlyEntriesIn(KOTLIN_MAP)
    assertThat(testComponent.getUsage().kotlinMapAsKotlin).containsExactlyEntriesIn(KOTLIN_MAP)
  }

  companion object {
    val JAVA_MAP = mapOf("provideIntoJavaMap-key" to "provideIntoJavaMap-value")
    val KOTLIN_MAP = mapOf("provideIntoKotlinMap-key" to "provideIntoKotlinMap-value")

    @Suppress("UNCHECKED_CAST")
    fun java.util.Map<String, String>.toKotlin(): Map<String, String> {
      return this as Map<String, String>
    }
  }
}

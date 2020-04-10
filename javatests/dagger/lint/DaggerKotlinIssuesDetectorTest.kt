/*
 * Copyright (C) 2020 The Dagger Authors.
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
package dagger.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@Suppress("UnstableApiUsage")
@RunWith(JUnit4::class)
class DaggerKotlinIssuesDetectorTest : LintDetectorTest() {

  private companion object {
    private val javaxInjectStubs = kotlin(
        """
        package javax.inject
    
        annotation class Inject
        annotation class Qualifier
      """
    ).indented()

    private val daggerStubs = kotlin(
        """
        package dagger
    
        annotation class Provides
        annotation class Module
      """
    ).indented()
  }

  override fun getDetector(): Detector = DaggerKotlinIssuesDetector()

  override fun getIssues(): List<Issue> = DaggerKotlinIssuesDetector.issues

  @Test
  fun simpleSmokeTestForQualifiersAndProviders() {
    lint()
        .allowMissingSdk()
        .files(
            javaxInjectStubs,
            daggerStubs,
            kotlin(
                """
                  package foo
                  import javax.inject.Inject
                  import javax.inject.Qualifier
                  import kotlin.jvm.JvmStatic
                  import dagger.Provides
                  import dagger.Module
                  
                  @Qualifier
                  annotation class MyQualifier
                                
                  class InjectedTest {
                    // This should fail because of `:field`
                    @Inject
                    @field:MyQualifier
                    lateinit var prop: String

                    // This is fine!
                    @Inject
                    @MyQualifier
                    lateinit var prop2: String
                  }
                  
                  @Module
                  object ObjectModule {
                    // This should fail because it uses `@JvmStatic`
                    @JvmStatic
                    @Provides
                    fun provideFoo(): String {
                    
                    }

                    // This is fine!
                    @Provides
                    fun provideBar(): String {
                    
                    }
                  }
                  
                  @Module
                  class ClassModule {
                    companion object {
                      // This should fail because the companion object is part of ClassModule, so this is unnecessary.
                      @JvmStatic
                      @Provides
                      fun provideBaz(): String {
                      
                      }
                    }
                  }
                  
                  @Module
                  class ClassModule2 {
                    // This should fail because the companion object is part of ClassModule
                    @Module
                    companion object {
                      @Provides
                      fun provideBaz(): String {
                      
                      }
                    }
                  }
                  
                  // This is correct as of Dagger 2.26!
                  @Module
                  class ClassModule3 {
                    companion object {
                      @Provides
                      fun provideBaz(): String {
                      
                      }
                    }
                  }
                  
                  class ClassModule4 {
                    // This is should fail because this should be extracted to a standalone object.
                    @Module
                    companion object {
                      @Provides
                      fun provideBaz(): String {
                      
                      }
                    }
                  }
                """
            ).indented()
        )
        .allowCompilationErrors(false)
        .run()
        .expect(
            """
              src/foo/MyQualifier.kt:14: Error: Redundant 'field:' used for Dagger qualifier annotation. [FieldSiteTargetOnQualifierAnnotation]
                @field:MyQualifier
                ~~~~~~~~~~~~~~~~~~
              src/foo/MyQualifier.kt:26: Error: @JvmStatic used for @Provides function in an object class [JvmStaticProvidesInObjectDetector]
                @JvmStatic
                ~~~~~~~~~~
              src/foo/MyQualifier.kt:43: Error: @JvmStatic used for @Provides function in an object class [JvmStaticProvidesInObjectDetector]
                  @JvmStatic
                  ~~~~~~~~~~
              src/foo/MyQualifier.kt:53: Error: Module companion objects should not be annotated with @Module. [ModuleCompanionObjects]
                // This should fail because the companion object is part of ClassModule
                ^
              src/foo/MyQualifier.kt:75: Error: Module companion objects should not be annotated with @Module. [ModuleCompanionObjects]
                // This is should fail because this should be extracted to a standalone object.
                ^
              5 errors, 0 warnings
            """.trimIndent()
        )
        .expectFixDiffs(
            """
              Fix for src/foo/MyQualifier.kt line 14: Remove 'field:':
              @@ -14 +14
              -   @field:MyQualifier
              +   @MyQualifier
              Fix for src/foo/MyQualifier.kt line 26: Remove @JvmStatic:
              @@ -26 +26
              -   @JvmStatic
              +  
              Fix for src/foo/MyQualifier.kt line 43: Remove @JvmStatic:
              @@ -43 +43
              -     @JvmStatic
              +    
              Fix for src/foo/MyQualifier.kt line 53: Remove @Module:
              @@ -54 +54
              -   @Module
              +  
              Fix for src/foo/MyQualifier.kt line 75: Remove @Module:
              @@ -76 +76
              -   @Module
              +  
            """.trimIndent()
        )
  }
}
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
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Test

@Suppress("UnstableApiUsage")
@RunWith(JUnit4::class)
class DoNotCallProvidersDetectorTest : LintDetectorTest() {

  private companion object {
    private val javaxAnnotation = kotlin(
        """
        package javax.annotation
    
        annotation class Generated(val message: String)
      """
    ).indented()
    private val daggerStubs = kotlin(
        """
        package dagger
    
        annotation class Binds
        annotation class Provides
        annotation class Module
      """
    ).indented()

    private val daggerProducerStubs = kotlin(
        """
        package dagger.producers
    
        annotation class Produces
      """
    ).indented()
  }

  override fun getDetector() = DoNotCallProvidersDetector()
  override fun getIssues() = listOf(DoNotCallProvidersDetector.ISSUE)

  @Test
  fun kotlin() {
    lint()
        .allowMissingSdk()
        .files(
            javaxAnnotation,
            daggerStubs,
            daggerProducerStubs,
            kotlin(
                """
                  package foo
                  import dagger.Binds
                  import dagger.Module
                  import dagger.Provides
                  import dagger.producers.Produces
                  import javax.annotation.Generated
                  
                  @Module
                  abstract class MyModule {
                    
                    @Binds fun binds1(input: String): Comparable<String>
                    @Binds fun String.binds2(): Comparable<String>
                    
                    fun badCode() {
                      binds1("this is bad")
                      "this is bad".binds2()
                      provider()
                      producer()
                    }
                    
                    companion object {
                      @Provides
                      fun provider(): String {
                        return ""
                      }
                      @Produces
                      fun producer(): String {
                        return ""
                      }
                    }
                  }
                  
                  @Generated("Totes generated code")
                  abstract class GeneratedCode {
                    fun doStuff() {
                      moduleInstance().binds1("this is technically fine but would never happen in dagger")
                      MyModule.provider()
                      MyModule.producer()
                    }
                    
                    abstract fun moduleInstance(): MyModule
                  }
                """
            ).indented()
        )
        .allowCompilationErrors(false)
        .run()
        .expect(
            """
              src/foo/MyModule.kt:15: Error: Dagger provider methods should not be called directly by user code. [DoNotCallProviders]
                  binds1("this is bad")
                  ~~~~~~~~~~~~~~~~~~~~~
              src/foo/MyModule.kt:16: Error: Dagger provider methods should not be called directly by user code. [DoNotCallProviders]
                  "this is bad".binds2()
                   ~~~~~~~~~~~~~~~~~~~~~
              src/foo/MyModule.kt:17: Error: Dagger provider methods should not be called directly by user code. [DoNotCallProviders]
                  provider()
                  ~~~~~~~~~~
              src/foo/MyModule.kt:18: Error: Dagger provider methods should not be called directly by user code. [DoNotCallProviders]
                  producer()
                  ~~~~~~~~~~
              4 errors, 0 warnings
            """.trimIndent()
        )
  }

  @Test
  fun java() {
    lint()
        .allowMissingSdk()
        .files(
            javaxAnnotation,
            daggerStubs,
            daggerProducerStubs,
            java(
                """
                  package foo;
                  import dagger.Binds;
                  import dagger.Module;
                  import dagger.Provides;
                  import dagger.producers.Produces;
                  import javax.annotation.Generated;
                  
                  class Holder {
                    @Module
                    abstract class MyModule {
                      
                      @Binds Comparable<String> binds1(String input);
                      
                      void badCode() {
                        binds1("this is bad");
                        provider();
                        producer();
                      }
                      
                      @Provides
                      static String provider() {
                        return "";
                      }
                      @Produces
                      static String producer() {
                        return "";
                      }
                    }
                    
                    @Generated("Totes generated code")
                    abstract class GeneratedCode {
                      void doStuff() {
                        moduleInstance().binds1("this is technically fine but would never happen in dagger");
                        MyModule.provider();
                        MyModule.producer();
                      }
                      
                      abstract MyModule moduleInstance();
                    }
                  }
                """
            ).indented()
        )
        .allowCompilationErrors(false)
        .run()
        .expect(
            """
              src/foo/Holder.java:15: Error: Dagger provider methods should not be called directly by user code. [DoNotCallProviders]
                    binds1("this is bad");
                    ~~~~~~~~~~~~~~~~~~~~~
              src/foo/Holder.java:16: Error: Dagger provider methods should not be called directly by user code. [DoNotCallProviders]
                    provider();
                    ~~~~~~~~~~
              src/foo/Holder.java:17: Error: Dagger provider methods should not be called directly by user code. [DoNotCallProviders]
                    producer();
                    ~~~~~~~~~~
              3 errors, 0 warnings
            """.trimIndent()
        )
  }
}

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

package dagger.hilt.android.processor.internal.viewmodel

import androidx.room.compiler.processing.ExperimentalProcessingApi
import androidx.room.compiler.processing.util.Source
import dagger.hilt.android.testing.compile.HiltCompilerTests
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(ExperimentalProcessingApi::class)
@RunWith(JUnit4::class)
class ViewModelProcessorTest {
  @Test
  fun validViewModel() {
    val myViewModel =
      Source.java(
        "dagger.hilt.android.test.MyViewModel",
        """
        package dagger.hilt.android.test;

        import androidx.lifecycle.ViewModel;
        import dagger.hilt.android.lifecycle.HiltViewModel;
        import javax.inject.Inject;

        @HiltViewModel
        class MyViewModel extends ViewModel {
            @Inject MyViewModel() { }
        }
        """
          .trimIndent()
      )
    HiltCompilerTests.hiltCompiler(myViewModel)
      .withAdditionalJavacProcessors(ViewModelProcessor())
      .withAdditionalKspProcessors(KspViewModelProcessor.Provider())
      .compile { subject -> subject.hasErrorCount(0) }
  }

  @Test
  fun verifyEnclosingElementExtendsViewModel() {
    val myViewModel =
      Source.java(
        "dagger.hilt.android.test.MyViewModel",
        """
        package dagger.hilt.android.test;

        import dagger.hilt.android.lifecycle.HiltViewModel;
        import javax.inject.Inject;

        @HiltViewModel
        class MyViewModel {
            @Inject
            MyViewModel() { }
        }
        """
          .trimIndent()
      )

    HiltCompilerTests.hiltCompiler(myViewModel)
      .withAdditionalJavacProcessors(ViewModelProcessor())
      .withAdditionalKspProcessors(KspViewModelProcessor.Provider())
      .compile { subject ->
        subject.compilationDidFail()
        subject.hasErrorCount(1)
        subject.hasErrorContainingMatch(
          "@HiltViewModel is only supported on types that subclass androidx.lifecycle.ViewModel."
        )
      }
  }

  @Test
  fun verifySingleAnnotatedConstructor() {
    val myViewModel =
      Source.java(
        "dagger.hilt.android.test.MyViewModel",
        """
        package dagger.hilt.android.test;

        import androidx.lifecycle.ViewModel;
        import dagger.hilt.android.lifecycle.HiltViewModel;
        import javax.inject.Inject;

        @HiltViewModel
        class MyViewModel extends ViewModel {
            @Inject
            MyViewModel() { }

            @Inject
            MyViewModel(String s) { }
        }
        """
          .trimIndent()
      )

    HiltCompilerTests.hiltCompiler(myViewModel)
      .withAdditionalJavacProcessors(ViewModelProcessor())
      .withAdditionalKspProcessors(KspViewModelProcessor.Provider())
      .compile { subject ->
        subject.compilationDidFail()
        subject.hasErrorCount(2)
        subject.hasErrorContaining(
          "Type dagger.hilt.android.test.MyViewModel may only contain one injected constructor. Found: [@Inject dagger.hilt.android.test.MyViewModel(), @Inject dagger.hilt.android.test.MyViewModel(String)]"
        )
        subject.hasErrorContaining(
          "@HiltViewModel annotated class should contain exactly one @Inject annotated constructor."
        )
      }
  }

  @Test
  fun verifyNonPrivateConstructor() {
    val myViewModel =
      Source.java(
        "dagger.hilt.android.test.MyViewModel",
        """
        package dagger.hilt.android.test;

        import androidx.lifecycle.ViewModel;
        import dagger.hilt.android.lifecycle.HiltViewModel;
        import javax.inject.Inject;

        @HiltViewModel
        class MyViewModel extends ViewModel {
            @Inject
            private MyViewModel() { }
        }
        """
          .trimIndent()
      )

    HiltCompilerTests.hiltCompiler(myViewModel)
      .withAdditionalJavacProcessors(ViewModelProcessor())
      .withAdditionalKspProcessors(KspViewModelProcessor.Provider())
      .compile { subject ->
        subject.compilationDidFail()
        subject.hasErrorCount(2)
        subject.hasErrorContaining("Dagger does not support injection into private constructors")
        subject.hasErrorContaining("@Inject annotated constructors must not be private.")
      }
  }

  @Test
  fun verifyInnerClassIsStatic() {
    val myViewModel =
      Source.java(
        "dagger.hilt.android.test.Outer",
        """
        package dagger.hilt.android.test;

        import androidx.lifecycle.ViewModel;
        import dagger.hilt.android.lifecycle.HiltViewModel;
        import javax.inject.Inject;

        class Outer {
            @HiltViewModel
            class MyViewModel extends ViewModel {
                @Inject
                MyViewModel() { }
            }
        }
        """
          .trimIndent()
      )

    HiltCompilerTests.hiltCompiler(myViewModel)
      .withAdditionalJavacProcessors(ViewModelProcessor())
      .withAdditionalKspProcessors(KspViewModelProcessor.Provider())
      .compile { subject ->
        subject.compilationDidFail()
        subject.hasErrorCount(2)
        subject.hasErrorContaining(
          "@Inject constructors are invalid on inner classes. Did you mean to make the class static?"
        )
        subject.hasErrorContaining(
          "@HiltViewModel may only be used on inner classes if they are static."
        )
      }
  }

  @Test
  fun verifyNoScopeAnnotation() {
    val myViewModel =
      Source.java(
        "dagger.hilt.android.test.MyViewModel",
        """
        package dagger.hilt.android.test;

        import androidx.lifecycle.ViewModel;
        import dagger.hilt.android.lifecycle.HiltViewModel;
        import javax.inject.Inject;
        import javax.inject.Singleton;

        @Singleton
        @HiltViewModel
        class MyViewModel extends ViewModel {
            @Inject MyViewModel() { }
        }
        """
          .trimIndent()
      )

    HiltCompilerTests.hiltCompiler(myViewModel)
      .withAdditionalJavacProcessors(ViewModelProcessor())
      .withAdditionalKspProcessors(KspViewModelProcessor.Provider())
      .compile { subject ->
        subject.compilationDidFail()
        subject.hasErrorCount(1)
        subject.hasErrorContainingMatch(
          "@HiltViewModel classes should not be scoped. Found: @javax.inject.Singleton"
        )
      }
  }
}

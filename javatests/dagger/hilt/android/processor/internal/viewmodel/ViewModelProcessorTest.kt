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

import com.google.testing.compile.CompilationSubject.assertThat
import com.google.testing.compile.Compiler
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ViewModelProcessorTest {

  private fun compiler(): Compiler = Compiler.javac().withProcessors(ViewModelProcessor())

  @Test
  fun validViewModel() {
    val myViewModel = """
        package dagger.hilt.android.test;

        import androidx.lifecycle.ViewModel;
        import dagger.hilt.android.lifecycle.HiltViewModel;
        import javax.inject.Inject;

        @HiltViewModel
        class MyViewModel extends ViewModel {
            @Inject MyViewModel() { }
        }
        """.toJFO("dagger.hilt.android.test.MyViewModel")

    val compilation = compiler().compile(myViewModel)
    assertThat(compilation).succeeded()
  }

  @Test
  fun verifyEnclosingElementExtendsViewModel() {
    val myViewModel = """
        package dagger.hilt.android.test;

        import dagger.hilt.android.lifecycle.HiltViewModel;
        import javax.inject.Inject;

        @HiltViewModel
        class MyViewModel {
            @Inject
            MyViewModel() { }
        }
        """.toJFO("dagger.hilt.android.test.MyViewModel")

    val compilation = compiler().compile(myViewModel)
    assertThat(compilation).apply {
      failed()
      hadErrorCount(1)
      hadErrorContainingMatch(
        "@HiltViewModel is only supported on types that subclass androidx.lifecycle.ViewModel."
      )
    }
  }

  @Test
  fun verifySingleAnnotatedConstructor() {
    val myViewModel = """
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
        """.toJFO("dagger.hilt.android.test.MyViewModel")

    val compilation = compiler().compile(myViewModel)
    assertThat(compilation).apply {
      failed()
      hadErrorCount(1)
      hadErrorContainingMatch(
        "@HiltViewModel annotated class should contain exactly one @Inject annotated constructor."
      )
    }
  }

  @Test
  fun verifyNonPrivateConstructor() {
    val myViewModel = """
        package dagger.hilt.android.test;

        import androidx.lifecycle.ViewModel;
        import dagger.hilt.android.lifecycle.HiltViewModel;
        import javax.inject.Inject;

        @HiltViewModel
        class MyViewModel extends ViewModel {
            @Inject
            private MyViewModel() { }
        }
        """.toJFO("dagger.hilt.android.test.MyViewModel")

    val compilation = compiler().compile(myViewModel)
    assertThat(compilation).apply {
      failed()
      hadErrorCount(1)
      hadErrorContainingMatch(
        "@Inject annotated constructors must not be " +
          "private."
      )
    }
  }

  @Test
  fun verifyInnerClassIsStatic() {
    val myViewModel = """
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
        """.toJFO("dagger.hilt.android.test.Outer")

    val compilation = compiler().compile(myViewModel)
    assertThat(compilation).apply {
      failed()
      hadErrorCount(1)
      hadErrorContainingMatch(
        "@HiltViewModel may only be used on inner classes if they are static."
      )
    }
  }

  @Test
  fun verifyNoScopeAnnotation() {
    val myViewModel = """
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
        """.toJFO("dagger.hilt.android.test.MyViewModel")

    val compilation = compiler().compile(myViewModel)
    assertThat(compilation).apply {
      failed()
      hadErrorCount(1)
      hadErrorContainingMatch(
        "@HiltViewModel classes should not be scoped. Found: @javax.inject.Singleton"
      )
    }
  }
}

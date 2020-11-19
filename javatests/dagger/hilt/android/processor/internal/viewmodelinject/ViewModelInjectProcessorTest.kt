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

package dagger.hilt.android.processor.internal.viewmodelinject

import com.google.testing.compile.CompilationSubject.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ViewModelInjectProcessorTest {

  @Test
  fun validViewModel() {
    val myViewModel = """
        package dagger.hilt.android.test;

        import androidx.lifecycle.ViewModel;
        import dagger.hilt.android.lifecycle.ViewModelInject;

        class MyViewModel extends ViewModel {
            @ViewModelInject
            MyViewModel() { }
        }
        """.toJFO("dagger.hilt.android.test.MyViewModel")

    val compilation = compiler().compile(myViewModel)
    assertThat(compilation).succeeded()
  }

  @Test
  fun verifyEnclosingElementExtendsViewModel() {
    val myViewModel = """
        package dagger.hilt.android.test;

        import dagger.hilt.android.lifecycle.ViewModelInject;

        class MyViewModel {
            @ViewModelInject
            MyViewModel() { }
        }
        """.toJFO("dagger.hilt.android.test.MyViewModel")

    val compilation = compiler().compile(myViewModel)
    assertThat(compilation).apply {
      failed()
      hadErrorCount(1)
      hadErrorContainingMatch(
        "@ViewModelInject is only supported on types that subclass " +
          "androidx.lifecycle.ViewModel."
      )
    }
  }

  @Test
  fun verifySingleAnnotatedConstructor() {
    val myViewModel = """
        package dagger.hilt.android.test;

        import androidx.lifecycle.ViewModel;
        import dagger.hilt.android.lifecycle.ViewModelInject;

        class MyViewModel extends ViewModel {
            @ViewModelInject
            MyViewModel() { }

            @ViewModelInject
            MyViewModel(String s) { }
        }
        """.toJFO("dagger.hilt.android.test.MyViewModel")

    val compilation = compiler().compile(myViewModel)
    assertThat(compilation).apply {
      failed()
      hadErrorCount(1)
      hadErrorContainingMatch("Multiple @ViewModelInject annotated constructors found.")
    }
  }

  @Test
  fun verifyNonPrivateConstructor() {
    val myViewModel = """
        package dagger.hilt.android.test;

        import androidx.lifecycle.ViewModel;
        import dagger.hilt.android.lifecycle.ViewModelInject;

        class MyViewModel extends ViewModel {
            @ViewModelInject
            private MyViewModel() { }
        }
        """.toJFO("dagger.hilt.android.test.MyViewModel")

    val compilation = compiler().compile(myViewModel)
    assertThat(compilation).apply {
      failed()
      hadErrorCount(1)
      hadErrorContainingMatch(
        "@ViewModelInject annotated constructors must not be " +
          "private."
      )
    }
  }

  @Test
  fun verifyInnerClassIsStatic() {
    val myViewModel = """
        package dagger.hilt.android.test;

        import androidx.lifecycle.ViewModel;
        import dagger.hilt.android.lifecycle.ViewModelInject;

        class Outer {
            class MyViewModel extends ViewModel {
                @ViewModelInject
                MyViewModel() { }
            }
        }
        """.toJFO("dagger.hilt.android.test.Outer")

    val compilation = compiler().compile(myViewModel)
    assertThat(compilation).apply {
      failed()
      hadErrorCount(1)
      hadErrorContainingMatch(
        "@ViewModelInject may only be used on inner classes " +
          "if they are static."
      )
    }
  }

  @Test
  fun verifyAtMostOneSavedStateHandleArg() {
    val myViewModel = """
        package dagger.hilt.android.test;

        import androidx.lifecycle.ViewModel;
        import androidx.lifecycle.SavedStateHandle;
        import dagger.hilt.android.lifecycle.ViewModelInject;

        class MyViewModel extends ViewModel {
            @ViewModelInject
            MyViewModel(SavedStateHandle savedState1, SavedStateHandle savedState2) { }
        }
        """.toJFO("dagger.hilt.android.test.MyViewModel")

    val compilation = compiler().compile(myViewModel)
    assertThat(compilation).apply {
      failed()
      hadErrorCount(1)
      hadErrorContainingMatch(
        "Expected zero or one constructor argument of type " +
          "androidx.lifecycle.SavedStateHandle, found 2"
      )
    }
  }
}

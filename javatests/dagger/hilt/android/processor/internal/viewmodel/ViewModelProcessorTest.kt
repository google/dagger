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
import com.google.common.collect.ImmutableMap
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
  fun verifyNoAssistedInjectViewModels() {
    val myViewModel =
      Source.java(
        "dagger.hilt.android.test.MyViewModel",
        """
        package dagger.hilt.android.test;

        import dagger.assisted.AssistedInject;
        import dagger.assisted.Assisted;
        import androidx.lifecycle.ViewModel;
        import dagger.hilt.android.lifecycle.HiltViewModel;
        import javax.inject.Inject;

        @HiltViewModel
        class MyViewModel extends ViewModel {
            @AssistedInject
            MyViewModel(String s, @Assisted int i) { }
        }
        """
          .trimIndent()
      )

    HiltCompilerTests.hiltCompiler(myViewModel)
      .withAdditionalJavacProcessors(ViewModelProcessor())
      .withAdditionalKspProcessors(KspViewModelProcessor.Provider())
      .withProcessorOptions(ImmutableMap.of("dagger.hilt.enableAssistedInjectViewModels", "false"))
      .compile { subject ->
        subject.compilationDidFail()
        subject.hasErrorCount(1)
        subject.hasErrorContaining(
          "ViewModel constructor should be annotated with @Inject instead of @AssistedInject."
        )
      }
  }

  @Test
  fun verifyHasInjectConstructor() {
    val myViewModel =
      Source.java(
        "dagger.hilt.android.test.MyViewModel",
        """
        package dagger.hilt.android.test;

        import androidx.lifecycle.ViewModel;
        import dagger.hilt.android.lifecycle.HiltViewModel;

        @HiltViewModel
        class MyViewModel extends ViewModel {
            MyViewModel(String s) { }
        }
        """
          .trimIndent()
      )

    HiltCompilerTests.hiltCompiler(myViewModel)
      .withAdditionalJavacProcessors(ViewModelProcessor())
      .withAdditionalKspProcessors(KspViewModelProcessor.Provider())
      .withProcessorOptions(ImmutableMap.of("dagger.hilt.enableAssistedInjectViewModels", "false"))
      .compile { subject ->
        subject.compilationDidFail()
        subject.hasErrorCount(1)
        subject.hasErrorContaining(
          "@HiltViewModel annotated class should contain exactly one @Inject annotated constructor."
        )
      }
  }

  @Test
  fun verifyJakartaInjectConstructorSucceeds() {
    val myViewModel =
      Source.java(
        "dagger.hilt.android.test.MyViewModel",
        """
        package dagger.hilt.android.test;

        import androidx.lifecycle.ViewModel;
        import dagger.hilt.android.lifecycle.HiltViewModel;
        import jakarta.inject.Inject;

        @HiltViewModel
        class MyViewModel extends ViewModel {
            @Inject
            MyViewModel(String s) { }
        }
        """
          .trimIndent()
      )

    HiltCompilerTests.hiltCompiler(myViewModel)
      .withAdditionalJavacProcessors(ViewModelProcessor())
      .withAdditionalKspProcessors(KspViewModelProcessor.Provider())
      .withProcessorOptions(ImmutableMap.of("dagger.hilt.enableAssistedInjectViewModels", "false"))
      .compile { subject ->
        subject.hasErrorCount(0)
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

    listOf(false, true).forEach { enableAssistedInjectViewModels ->
      HiltCompilerTests.hiltCompiler(myViewModel)
        .withAdditionalJavacProcessors(ViewModelProcessor())
        .withAdditionalKspProcessors(KspViewModelProcessor.Provider())
        .withProcessorOptions(
          ImmutableMap.of(
            "dagger.hilt.enableAssistedInjectViewModels",
            enableAssistedInjectViewModels.toString()
          )
        )
        .compile { subject ->
          subject.compilationDidFail()
          subject.hasErrorCount(2)
          subject.hasErrorContaining(
            "Type dagger.hilt.android.test.MyViewModel may only contain one injected constructor. Found: [@Inject dagger.hilt.android.test.MyViewModel(), @Inject dagger.hilt.android.test.MyViewModel(String)]"
          )
          subject.hasErrorContaining(
            if (enableAssistedInjectViewModels) {
              "@HiltViewModel annotated class should contain exactly one @Inject or @AssistedInject annotated constructor."
            } else {
              "@HiltViewModel annotated class should contain exactly one @Inject annotated constructor."
            }
          )
        }
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

    listOf(false, true).forEach { enableAssistedInjectViewModels ->
      HiltCompilerTests.hiltCompiler(myViewModel)
        .withAdditionalJavacProcessors(ViewModelProcessor())
        .withAdditionalKspProcessors(KspViewModelProcessor.Provider())
        .withProcessorOptions(
          ImmutableMap.of(
            "dagger.hilt.enableAssistedInjectViewModels",
            enableAssistedInjectViewModels.toString()
          )
        )
        .compile { subject ->
          subject.compilationDidFail()
          subject.hasErrorCount(2)
          subject.hasErrorContaining("Dagger does not support injection into private constructors")
          subject.hasErrorContaining(
            if (enableAssistedInjectViewModels) {
              "@Inject or @AssistedInject annotated constructors must not be private."
            } else {
              "@Inject annotated constructors must not be private."
            }
          )
        }
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

  @Test
  fun verifyAssistedFlagIsEnabled() {
    val myViewModel =
      Source.java(
        "dagger.hilt.android.test.MyViewModel",
        """
        package dagger.hilt.android.test;

        import dagger.assisted.Assisted;
        import dagger.assisted.AssistedInject;
        import androidx.lifecycle.ViewModel;
        import dagger.hilt.android.lifecycle.HiltViewModel;

        @HiltViewModel(assistedFactory = MyFactory.class)
        class MyViewModel extends ViewModel {
            @AssistedInject
            MyViewModel(String s, @Assisted int i) { }
        }
        """
          .trimIndent()
      )
    val myFactory =
      Source.java(
        "dagger.hilt.android.test.MyFactory",
        """
        package dagger.hilt.android.test;
        import dagger.assisted.AssistedFactory;
        @AssistedFactory
        interface MyFactory {
            MyViewModel create(int i);
        }
        """
      )

    HiltCompilerTests.hiltCompiler(myViewModel, myFactory)
      .withAdditionalJavacProcessors(ViewModelProcessor())
      .withAdditionalKspProcessors(KspViewModelProcessor.Provider())
      .withProcessorOptions(ImmutableMap.of("dagger.hilt.enableAssistedInjectViewModels", "false"))
      .compile { subject ->
        subject.compilationDidFail()
        subject.hasErrorCount(1)
        subject.hasErrorContaining(
          "Specified assisted factory dagger.hilt.android.test.MyFactory for dagger.hilt.android.test.MyViewModel in @HiltViewModel but compiler option 'enableAssistedInjectViewModels' was not enabled."
        )
      }
  }

  @Test
  fun verifyAssistedFactoryHasMethod() {
    val myViewModel =
      Source.java(
        "dagger.hilt.android.test.MyViewModel",
        """
        package dagger.hilt.android.test;

        import dagger.assisted.Assisted;
        import dagger.assisted.AssistedInject;
        import androidx.lifecycle.ViewModel;
        import dagger.hilt.android.lifecycle.HiltViewModel;

        @HiltViewModel(assistedFactory = MyFactory.class)
        class MyViewModel extends ViewModel {
            @AssistedInject
            MyViewModel(String s, @Assisted int i) { }
        }
        """
          .trimIndent()
      )
    val myFactory =
      Source.java(
        "dagger.hilt.android.test.MyFactory",
        """
        package dagger.hilt.android.test;
        import dagger.assisted.AssistedFactory;
        @AssistedFactory
        interface MyFactory {}
        """
      )

    HiltCompilerTests.hiltCompiler(myViewModel, myFactory)
      .withAdditionalJavacProcessors(ViewModelProcessor())
      .withAdditionalKspProcessors(KspViewModelProcessor.Provider())
      .withProcessorOptions(ImmutableMap.of("dagger.hilt.enableAssistedInjectViewModels", "true"))
      .compile { subject ->
        subject.compilationDidFail()
        subject.hasErrorCount(2)
        subject.hasErrorContaining(
          "The @AssistedFactory-annotated type is missing an abstract, non-default method whose return type matches the assisted injection type."
        )
        subject.hasErrorContaining(
          "Cannot find assisted factory method in dagger.hilt.android.test.MyFactory."
        )
      }
  }

  @Test
  fun verifyAssistedFactoryHasOnlyOneMethod() {
    val myViewModel =
      Source.java(
        "dagger.hilt.android.test.MyViewModel",
        """
        package dagger.hilt.android.test;

        import dagger.assisted.Assisted;
        import dagger.assisted.AssistedInject;
        import androidx.lifecycle.ViewModel;
        import dagger.hilt.android.lifecycle.HiltViewModel;

        @HiltViewModel(assistedFactory = MyFactory.class)
        class MyViewModel extends ViewModel {
            @AssistedInject
            MyViewModel(String s, @Assisted int i) { }
        }
        """
          .trimIndent()
      )
    val myFactory =
      Source.java(
        "dagger.hilt.android.test.MyFactory",
        """
        package dagger.hilt.android.test;
        import dagger.assisted.AssistedFactory;
        @AssistedFactory
        interface MyFactory {
            MyViewModel create(int i);
            String createString(int i);
            Integer createInteger(int i);
        }
        """
      )

    HiltCompilerTests.hiltCompiler(myViewModel, myFactory)
      .withAdditionalJavacProcessors(ViewModelProcessor())
      .withAdditionalKspProcessors(KspViewModelProcessor.Provider())
      .withProcessorOptions(ImmutableMap.of("dagger.hilt.enableAssistedInjectViewModels", "true"))
      .compile { subject ->
        subject.compilationDidFail()
        subject.hasErrorCount(4)
        subject.hasErrorContaining(
          "The @AssistedFactory-annotated type should contain a single abstract, non-default method but found multiple: [dagger.hilt.android.test.MyFactory.create(int), dagger.hilt.android.test.MyFactory.createString(int), dagger.hilt.android.test.MyFactory.createInteger(int)]"
        )
        subject.hasErrorContaining(
          "Invalid return type: java.lang.String. An assisted factory's abstract method must return a type with an @AssistedInject-annotated constructor."
        )
        subject.hasErrorContaining(
          "Invalid return type: java.lang.Integer. An assisted factory's abstract method must return a type with an @AssistedInject-annotated constructor."
        )
        subject.hasErrorContaining(
          "Cannot find assisted factory method in dagger.hilt.android.test.MyFactory."
        )
      }
  }

  @Test
  fun verifyAssistedFactoryIsAnnotatedWithAssistedFactory() {
    val myViewModel =
      Source.java(
        "dagger.hilt.android.test.MyViewModel",
        """
        package dagger.hilt.android.test;

        import dagger.assisted.Assisted;
        import dagger.assisted.AssistedInject;
        import androidx.lifecycle.ViewModel;
        import dagger.hilt.android.lifecycle.HiltViewModel;

        @HiltViewModel(assistedFactory = Integer.class)
        class MyViewModel extends ViewModel {
            @AssistedInject
            MyViewModel(String s, @Assisted int i) { }
        }
        """
          .trimIndent()
      )

    HiltCompilerTests.hiltCompiler(myViewModel)
      .withAdditionalJavacProcessors(ViewModelProcessor())
      .withAdditionalKspProcessors(KspViewModelProcessor.Provider())
      .withProcessorOptions(ImmutableMap.of("dagger.hilt.enableAssistedInjectViewModels", "true"))
      .compile { subject ->
        subject.compilationDidFail()
        subject.hasErrorCount(1)
        subject.hasErrorContaining(
          "Class java.lang.Integer is not annotated with @AssistedFactory."
        )
      }
  }

  @Test
  fun verifyFactoryMethodHasCorrectReturnType() {
    val myViewModel =
      Source.java(
        "dagger.hilt.android.test.MyViewModel",
        """
        package dagger.hilt.android.test;

        import dagger.assisted.Assisted;
        import dagger.assisted.AssistedInject;
        import androidx.lifecycle.ViewModel;
        import dagger.hilt.android.lifecycle.HiltViewModel;

        @HiltViewModel(assistedFactory = MyFactory.class)
        class MyViewModel extends ViewModel {
            @AssistedInject
            MyViewModel(String s, @Assisted int i) { }
        }
        """
          .trimIndent()
      )
    val myFactory =
      Source.java(
        "dagger.hilt.android.test.MyFactory",
        """
        package dagger.hilt.android.test;
        import dagger.assisted.AssistedFactory;
        @AssistedFactory
        interface MyFactory {
            String create(int i);
        }
        """
      )

    HiltCompilerTests.hiltCompiler(myViewModel, myFactory)
      .withAdditionalJavacProcessors(ViewModelProcessor())
      .withAdditionalKspProcessors(KspViewModelProcessor.Provider())
      .withProcessorOptions(ImmutableMap.of("dagger.hilt.enableAssistedInjectViewModels", "true"))
      .compile { subject ->
        subject.compilationDidFail()
        subject.hasErrorCount(2)
        subject.hasErrorContaining(
          "Invalid return type: java.lang.String. An assisted factory's abstract method must return a type with an @AssistedInject-annotated constructor."
        )
        subject.hasErrorContaining(
          "Class dagger.hilt.android.test.MyFactory must have a factory method that returns a dagger.hilt.android.test.MyViewModel. Found java.lang.String."
        )
      }
  }

  @Test
  fun verifyAssistedFactoryIsSpecified() {
    val myViewModel =
      Source.java(
        "dagger.hilt.android.test.MyViewModel",
        """
        package dagger.hilt.android.test;

        import dagger.assisted.Assisted;
        import dagger.assisted.AssistedInject;
        import androidx.lifecycle.ViewModel;
        import dagger.hilt.android.lifecycle.HiltViewModel;

        @HiltViewModel
        class MyViewModel extends ViewModel {
            @AssistedInject
            MyViewModel(String s, @Assisted int i) { }
        }
        """
          .trimIndent()
      )
    val myFactory =
      Source.java(
        "dagger.hilt.android.test.MyFactory",
        """
        package dagger.hilt.android.test;
        import dagger.assisted.AssistedFactory;
        @AssistedFactory
        interface MyFactory {
            MyViewModel create(int i);
        }
        """
      )

    HiltCompilerTests.hiltCompiler(myViewModel, myFactory)
      .withAdditionalJavacProcessors(ViewModelProcessor())
      .withAdditionalKspProcessors(KspViewModelProcessor.Provider())
      .withProcessorOptions(ImmutableMap.of("dagger.hilt.enableAssistedInjectViewModels", "true"))
      .compile { subject ->
        subject.compilationDidFail()
        subject.hasErrorCount(1)
        subject.hasErrorContaining(
          "dagger.hilt.android.test.MyViewModel must have a valid assisted factory specified in @HiltViewModel when used with assisted injection. Found java.lang.Object."
        )
      }
  }

  @Test
  fun verifyConstructorHasRightInjectAnnotation() {
    val myViewModel =
      Source.java(
        "dagger.hilt.android.test.MyViewModel",
        """
        package dagger.hilt.android.test;

        import dagger.assisted.Assisted;
        import dagger.assisted.AssistedInject;
        import androidx.lifecycle.ViewModel;
        import dagger.hilt.android.lifecycle.HiltViewModel;
        import javax.inject.Inject;

        @HiltViewModel(assistedFactory = MyFactory.class)
        class MyViewModel extends ViewModel {
            @Inject
            MyViewModel(String s, int i) { }
        }
        """
          .trimIndent()
      )
    val myFactory =
      Source.java(
        "dagger.hilt.android.test.MyFactory",
        """
        package dagger.hilt.android.test;
        import dagger.assisted.AssistedFactory;
        @AssistedFactory
        interface MyFactory {
            MyViewModel create(int i);
        }
        """
      )

    HiltCompilerTests.hiltCompiler(myViewModel, myFactory)
      .withAdditionalJavacProcessors(ViewModelProcessor())
      .withAdditionalKspProcessors(KspViewModelProcessor.Provider())
      .withProcessorOptions(ImmutableMap.of("dagger.hilt.enableAssistedInjectViewModels", "true"))
      .compile { subject ->
        subject.compilationDidFail()
        subject.hasErrorCount(2)
        subject.hasErrorContaining(
          "Invalid return type: dagger.hilt.android.test.MyViewModel. An assisted factory's abstract method must return a type with an @AssistedInject-annotated constructor."
        )
        subject.hasErrorContaining(
          "Found assisted factory dagger.hilt.android.test.MyFactory in @HiltViewModel but the constructor was annotated with @Inject instead of @AssistedInject."
        )
      }
  }
}

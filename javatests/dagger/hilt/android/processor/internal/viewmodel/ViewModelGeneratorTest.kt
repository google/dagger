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
class ViewModelGeneratorTest {

  private fun compiler(): Compiler = Compiler.javac().withProcessors(ViewModelProcessor())

  @Test
  fun verifyModule_noArg() {
    val myViewModel = """
        package dagger.hilt.android.test;

        import dagger.hilt.android.lifecycle.HiltViewModel;
        import androidx.lifecycle.ViewModel;
        import javax.inject.Inject;

        @HiltViewModel
        class MyViewModel extends ViewModel {
            @Inject
            MyViewModel() { }
        }
        """.toJFO("dagger.hilt.android.test.MyViewModel")

    val expected = """
        package dagger.hilt.android.test;

        import androidx.lifecycle.ViewModel;
        import dagger.Binds;
        import dagger.Module;
        import dagger.Provides;
        import dagger.hilt.InstallIn;
        import dagger.hilt.android.components.ActivityRetainedComponent;
        import dagger.hilt.android.components.ViewModelComponent;
        import dagger.hilt.android.internal.lifecycle.HiltViewModelMap;
        import dagger.hilt.codegen.OriginatingElement;
        import dagger.multibindings.IntoMap;
        import dagger.multibindings.IntoSet;
        import dagger.multibindings.StringKey;
        import java.lang.String;
        import $GENERATED_TYPE

        $GENERATED_ANNOTATION
        @OriginatingElement(
            topLevelClass = MyViewModel.class
        )
        public final class MyViewModel_HiltModules {
          private MyViewModel_HiltModules() {
          }

          @Module
          @InstallIn(ViewModelComponent.class)
          public static abstract class BindsModule {
            private BindsModule() {}

            @Binds
            @IntoMap
            @StringKey("dagger.hilt.android.test.MyViewModel")
            @HiltViewModelMap
            public abstract ViewModel binds(MyViewModel vm);
          }

          @Module
          @InstallIn(ActivityRetainedComponent.class)
          public static final class KeyModule {
            private KeyModule() {
            }

            @Provides
            @IntoSet
            @HiltViewModelMap.KeySet
            public static String provide() {
              return "dagger.hilt.android.test.MyViewModel";
            }
          }
        }
        """.toJFO("dagger.hilt.android.test.MyViewModel_HiltModule")

    val compilation = compiler()
      .compile(myViewModel)
    assertThat(compilation).apply {
      succeeded()
      generatedSourceFile("dagger.hilt.android.test.MyViewModel_HiltModules")
        .hasSourceEquivalentTo(expected)
    }
  }

  @Test
  fun verifyModule_savedStateOnlyArg() {
    val myViewModel = """
        package dagger.hilt.android.test;

        import dagger.hilt.android.lifecycle.HiltViewModel;
        import androidx.lifecycle.ViewModel;
        import androidx.lifecycle.SavedStateHandle;
        import javax.inject.Inject;

        @HiltViewModel
        class MyViewModel extends ViewModel {
            @Inject
            MyViewModel(SavedStateHandle savedState) { }
        }
        """.toJFO("dagger.hilt.android.test.MyViewModel")

    val expected = """
        package dagger.hilt.android.test;

        import androidx.lifecycle.ViewModel;
        import dagger.Binds;
        import dagger.Module;
        import dagger.Provides;
        import dagger.hilt.InstallIn;
        import dagger.hilt.android.components.ActivityRetainedComponent;
        import dagger.hilt.android.components.ViewModelComponent;
        import dagger.hilt.android.internal.lifecycle.HiltViewModelMap;
        import dagger.hilt.codegen.OriginatingElement;
        import dagger.multibindings.IntoMap;
        import dagger.multibindings.IntoSet;
        import dagger.multibindings.StringKey;
        import java.lang.String;
        import $GENERATED_TYPE

        $GENERATED_ANNOTATION
        @OriginatingElement(
            topLevelClass = MyViewModel.class
        )
        public final class MyViewModel_HiltModules {
          private MyViewModel_HiltModules() {
          }

          @Module
          @InstallIn(ViewModelComponent.class)
          public static abstract class BindsModule {
            private BindsModule() {}

            @Binds
            @IntoMap
            @StringKey("dagger.hilt.android.test.MyViewModel")
            @HiltViewModelMap
            public abstract ViewModel binds(MyViewModel vm);
          }

          @Module
          @InstallIn(ActivityRetainedComponent.class)
          public static final class KeyModule {
            private KeyModule() {
            }

            @Provides
            @IntoSet
            @HiltViewModelMap.KeySet
            public static String provide() {
              return "dagger.hilt.android.test.MyViewModel";
            }
          }
        }
        """.toJFO("dagger.hilt.android.test.MyViewModel_HiltModule")

    val compilation = compiler()
      .compile(myViewModel)
    assertThat(compilation).apply {
      succeeded()
      generatedSourceFile("dagger.hilt.android.test.MyViewModel_HiltModules")
        .hasSourceEquivalentTo(expected)
    }
  }

  @Test
  fun verifyModule_mixedArgs() {
    val foo = """
        package dagger.hilt.android.test;

        public class Foo { }
        """.toJFO("dagger.hilt.android.test.Foo")

    val myViewModel = """
        package dagger.hilt.android.test;

        import dagger.hilt.android.lifecycle.HiltViewModel;
        import androidx.lifecycle.ViewModel;
        import androidx.lifecycle.SavedStateHandle;
        import java.lang.String;
        import javax.inject.Inject;

        @HiltViewModel
        class MyViewModel extends ViewModel {
            @Inject
            MyViewModel(String s, Foo f, SavedStateHandle savedState, long l) { }
        }
        """.toJFO("dagger.hilt.android.test.MyViewModel")

    val expected = """
        package dagger.hilt.android.test;

        import androidx.lifecycle.ViewModel;
        import dagger.Binds;
        import dagger.Module;
        import dagger.Provides;
        import dagger.hilt.InstallIn;
        import dagger.hilt.android.components.ActivityRetainedComponent;
        import dagger.hilt.android.components.ViewModelComponent;
        import dagger.hilt.android.internal.lifecycle.HiltViewModelMap;
        import dagger.hilt.codegen.OriginatingElement;
        import dagger.multibindings.IntoMap;
        import dagger.multibindings.IntoSet;
        import dagger.multibindings.StringKey;
        import java.lang.String;
        import $GENERATED_TYPE

        $GENERATED_ANNOTATION
        @OriginatingElement(
            topLevelClass = MyViewModel.class
        )
        public final class MyViewModel_HiltModules {
          private MyViewModel_HiltModules() {
          }

          @Module
          @InstallIn(ViewModelComponent.class)
          public static abstract class BindsModule {
            private BindsModule() {}

            @Binds
            @IntoMap
            @StringKey("dagger.hilt.android.test.MyViewModel")
            @HiltViewModelMap
            public abstract ViewModel binds(MyViewModel vm);
          }

          @Module
          @InstallIn(ActivityRetainedComponent.class)
          public static final class KeyModule {
            private KeyModule() {
            }

            @Provides
            @IntoSet
            @HiltViewModelMap.KeySet
            public static String provide() {
              return "dagger.hilt.android.test.MyViewModel";
            }
          }
        }
        """.toJFO("dagger.hilt.android.test.MyViewModel_HiltModule")

    val compilation = compiler()
      .compile(foo, myViewModel)
    assertThat(compilation).apply {
      succeeded()
      generatedSourceFile("dagger.hilt.android.test.MyViewModel_HiltModules")
        .hasSourceEquivalentTo(expected)
    }
  }

  @Test
  fun verifyModule_mixedAndProviderArgs() {
    val foo = """
        package dagger.hilt.android.test;

        public class Foo { }
        """.toJFO("dagger.hilt.android.test.Foo")

    val myViewModel = """
        package dagger.hilt.android.test;

        import dagger.hilt.android.lifecycle.HiltViewModel;
        import androidx.lifecycle.ViewModel;
        import androidx.lifecycle.SavedStateHandle;
        import java.lang.String;
        import javax.inject.Inject;
        import javax.inject.Provider;

        @HiltViewModel
        class MyViewModel extends ViewModel {
            @Inject
            MyViewModel(String s, Provider<Foo> f, SavedStateHandle savedState) { }
        }
        """.toJFO("dagger.hilt.android.test.MyViewModel")

    val expected = """
        package dagger.hilt.android.test;

        import androidx.lifecycle.ViewModel;
        import dagger.Binds;
        import dagger.Module;
        import dagger.Provides;
        import dagger.hilt.InstallIn;
        import dagger.hilt.android.components.ActivityRetainedComponent;
        import dagger.hilt.android.components.ViewModelComponent;
        import dagger.hilt.android.internal.lifecycle.HiltViewModelMap;
        import dagger.hilt.codegen.OriginatingElement;
        import dagger.multibindings.IntoMap;
        import dagger.multibindings.IntoSet;
        import dagger.multibindings.StringKey;
        import java.lang.String;
        import $GENERATED_TYPE;

        $GENERATED_ANNOTATION
        @OriginatingElement(
            topLevelClass = MyViewModel.class
        )
        public final class MyViewModel_HiltModules {
          private MyViewModel_HiltModules() {
          }

          @Module
          @InstallIn(ViewModelComponent.class)
          public static abstract class BindsModule {
            private BindsModule() {}

            @Binds
            @IntoMap
            @StringKey("dagger.hilt.android.test.MyViewModel")
            @HiltViewModelMap
            public abstract ViewModel binds(MyViewModel vm);
          }

          @Module
          @InstallIn(ActivityRetainedComponent.class)
          public static final class KeyModule {
            private KeyModule() {
            }

            @Provides
            @IntoSet
            @HiltViewModelMap.KeySet
            public static String provide() {
              return "dagger.hilt.android.test.MyViewModel";
            }
          }
        }
        """.toJFO("dagger.hilt.android.test.MyViewModel_HiltModules")

    val compilation = compiler()
      .compile(foo, myViewModel)
    assertThat(compilation).apply {
      succeeded()
      generatedSourceFile("dagger.hilt.android.test.MyViewModel_HiltModules")
        .hasSourceEquivalentTo(expected)
    }
  }

  @Test
  fun verifyModule_qualifiedArgs() {
    val myQualifier = """
        package dagger.hilt.android.test;

        import javax.inject.Qualifier;

        @Qualifier
        public @interface MyQualifier { }
        """.toJFO("dagger.hilt.android.test.MyQualifier")

    val myViewModel = """
        package dagger.hilt.android.test;

        import dagger.hilt.android.lifecycle.HiltViewModel;
        import androidx.lifecycle.ViewModel;
        import androidx.lifecycle.SavedStateHandle;
        import java.lang.Long;
        import java.lang.String;
        import javax.inject.Inject;
        import javax.inject.Named;
        import javax.inject.Provider;

        @HiltViewModel
        class MyViewModel extends ViewModel {
            @Inject
            MyViewModel(@Named("TheString") String s, @MyQualifier Provider<Long> l,
                    SavedStateHandle savedState) {
            }
        }
        """.toJFO("dagger.hilt.android.test.MyViewModel")

    val expected = """
        package dagger.hilt.android.test;

        import androidx.lifecycle.ViewModel;
        import dagger.Binds;
        import dagger.Module;
        import dagger.Provides;
        import dagger.hilt.InstallIn;
        import dagger.hilt.android.components.ActivityRetainedComponent;
        import dagger.hilt.android.components.ViewModelComponent;
        import dagger.hilt.android.internal.lifecycle.HiltViewModelMap;
        import dagger.hilt.codegen.OriginatingElement;
        import dagger.multibindings.IntoMap;
        import dagger.multibindings.IntoSet;
        import dagger.multibindings.StringKey;
        import java.lang.String;
        import $GENERATED_TYPE;

        $GENERATED_ANNOTATION
        @OriginatingElement(
            topLevelClass = MyViewModel.class
        )
        public final class MyViewModel_HiltModules {
          private MyViewModel_HiltModules() {
          }

          @Module
          @InstallIn(ViewModelComponent.class)
          public static abstract class BindsModule {
            private BindsModule() {}

            @Binds
            @IntoMap
            @StringKey("dagger.hilt.android.test.MyViewModel")
            @HiltViewModelMap
            public abstract ViewModel binds(MyViewModel vm);
          }

          @Module
          @InstallIn(ActivityRetainedComponent.class)
          public static final class KeyModule {
            private KeyModule() {
            }

            @Provides
            @IntoSet
            @HiltViewModelMap.KeySet
            public static String provide() {
              return "dagger.hilt.android.test.MyViewModel";
            }
          }
        }
        """.toJFO("dagger.hilt.android.test.MyViewModel_HiltModules")

    val compilation = compiler()
      .compile(myQualifier, myViewModel)
    assertThat(compilation).apply {
      succeeded()
      generatedSourceFile("dagger.hilt.android.test.MyViewModel_HiltModules")
        .hasSourceEquivalentTo(expected)
    }
  }

  @Test
  fun verifyInnerClass() {
    val viewModel = """
        package dagger.hilt.android.test;

        import dagger.hilt.android.lifecycle.HiltViewModel;
        import androidx.lifecycle.ViewModel;
        import javax.inject.Inject;

        class Outer {
            @HiltViewModel
            static class InnerViewModel extends ViewModel {
                @Inject
                InnerViewModel() { }
            }
        }
        """.toJFO("dagger.hilt.android.test.Outer")

    val expectedModule = """
        package dagger.hilt.android.test;

        import androidx.lifecycle.ViewModel;
        import dagger.Binds;
        import dagger.Module;
        import dagger.Provides;
        import dagger.hilt.InstallIn;
        import dagger.hilt.android.components.ActivityRetainedComponent;
        import dagger.hilt.android.components.ViewModelComponent;
        import dagger.hilt.android.internal.lifecycle.HiltViewModelMap;
        import dagger.hilt.codegen.OriginatingElement;
        import dagger.multibindings.IntoMap;
        import dagger.multibindings.IntoSet;
        import dagger.multibindings.StringKey;
        import java.lang.String;
        import $GENERATED_TYPE

        $GENERATED_ANNOTATION
        @OriginatingElement(
            topLevelClass = Outer.class
        )
        public final class Outer_InnerViewModel_HiltModules {
          private Outer_InnerViewModel_HiltModules() {
          }

          @Module
          @InstallIn(ViewModelComponent.class)
          public static abstract class BindsModule {
            private BindsModule() {}

            @Binds
            @IntoMap
            @StringKey("dagger.hilt.android.test.Outer${'$'}InnerViewModel")
            @HiltViewModelMap
            public abstract ViewModel binds(Outer.InnerViewModel vm);
          }

          @Module
          @InstallIn(ActivityRetainedComponent.class)
          public static final class KeyModule {
            private KeyModule() {
            }

            @Provides
            @IntoSet
            @HiltViewModelMap.KeySet
            public static String provide() {
              return "dagger.hilt.android.test.Outer${'$'}InnerViewModel";
            }
          }
        }
        """.toJFO("dagger.hilt.android.test.Outer_InnerViewModel_HiltModules")

    val compilation = compiler()
      .compile(viewModel)
    assertThat(compilation).apply {
      succeeded()
      generatedSourceFile("dagger.hilt.android.test.Outer_InnerViewModel_HiltModules")
        .hasSourceEquivalentTo(expectedModule)
    }
  }
}

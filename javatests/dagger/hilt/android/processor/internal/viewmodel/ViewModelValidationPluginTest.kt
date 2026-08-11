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

import androidx.room3.compiler.processing.ExperimentalProcessingApi
import androidx.room3.compiler.processing.util.Source
import com.google.common.collect.ImmutableList
import dagger.hilt.android.testing.compile.HiltCompilerTests
import dagger.internal.codegen.ComponentProcessor
import dagger.internal.codegen.KspComponentProcessor
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@ExperimentalProcessingApi
@RunWith(JUnit4::class)
class ViewModelValidationPluginTest {

  private fun testCompiler(vararg sources: Source): HiltCompilerTests.HiltCompiler =
    HiltCompilerTests.hiltCompiler(ImmutableList.copyOf(sources))
      .withAdditionalJavacProcessors(
        ComponentProcessor.withTestPlugins(ViewModelValidationPlugin()),
        ViewModelProcessor()
      )
      .withAdditionalKspProcessors(
        KspComponentProcessor.Provider.withTestPlugins(ViewModelValidationPlugin()),
        KspViewModelProcessor.Provider()
      )

  private val hiltAndroidApp =
    """
      package test;

      import android.app.Application;
      import dagger.hilt.android.HiltAndroidApp;

      @HiltAndroidApp(Application.class)
      public class TestApplication extends Hilt_TestApplication {}
      """
      .toJFO("test.TestApplication")

  @Test
  fun injectViewModelIsProhibited() {
    val hiltActivity =
      """
      package test;

      import androidx.fragment.app.FragmentActivity;
      import dagger.hilt.android.AndroidEntryPoint;
      import javax.inject.Inject;

      @AndroidEntryPoint(FragmentActivity.class)
      public class TestActivity extends Hilt_TestActivity {
        @Inject Foo foo;
      }
      """
        .toJFO("test.TestActivity")
    val hiltViewModel =
      """
        package test;

        import androidx.lifecycle.ViewModel;
        import dagger.hilt.android.lifecycle.HiltViewModel;
        import javax.inject.Inject;

        @HiltViewModel
        class MyViewModel extends ViewModel {
            @Inject MyViewModel() { }
        }
        """
        .toJFO("test.MyViewModel")
    val foo =
      """
        package test;

        import javax.inject.Inject;

        final class Foo {
            @Inject Foo(MyViewModel viewModel) {}
        }
    """
        .toJFO("test.Foo")

    testCompiler(foo, hiltViewModel, hiltAndroidApp, hiltActivity).compile() { subject ->
      subject.compilationDidFail()
      subject.hasErrorCount(1)
      subject.hasErrorContaining("Injection of an @HiltViewModel class is prohibited")
    }
  }

  @Test
  fun fieldInjectedViewModelIsProhibited() {
    val hiltActivity =
      """
      package test;

      import androidx.fragment.app.FragmentActivity;
      import dagger.hilt.android.AndroidEntryPoint;
      import javax.inject.Inject;

      @AndroidEntryPoint(FragmentActivity.class)
      public class TestActivity extends Hilt_TestActivity {
        @Inject MyViewModel viewModel;
      }
      """
        .toJFO("test.TestActivity")
    val hiltViewModel =
      """
        package test;

        import androidx.lifecycle.ViewModel;
        import dagger.hilt.android.lifecycle.HiltViewModel;
        import javax.inject.Inject;

        @HiltViewModel
        class MyViewModel extends ViewModel {
            @Inject MyViewModel() { }
        }
        """
        .toJFO("test.MyViewModel")

    testCompiler(hiltViewModel, hiltAndroidApp, hiltActivity).compile() { subject ->
      subject.compilationDidFail()
      subject.hasErrorCount(1)
      subject.hasErrorContaining("Injection of an @HiltViewModel class is prohibited")
    }
  }

  @Test
  fun injectViewModelFromViewModelComponentIsProhibited() {
    // Use an @HiltViewModel that injects a Foo to get the binding inside the ViewModelComponent
    val hiltViewModel =
      """
        package test;

        import androidx.lifecycle.ViewModel;
        import dagger.hilt.android.lifecycle.HiltViewModel;
        import javax.inject.Inject;

        @HiltViewModel
        class MyViewModel extends ViewModel {
            @Inject MyViewModel(Foo foo) { }
        }
        """
        .toJFO("test.MyViewModel")

    val foo =
      """
        package test;

        import javax.inject.Inject;
        import javax.inject.Provider;

        final class Foo {
            @Inject Foo(Provider<MyViewModel> viewModelProvider) {}
        }
    """
        .toJFO("test.Foo")

    testCompiler(foo, hiltViewModel, hiltAndroidApp).compile() { subject ->
      subject.compilationDidFail()
      subject.hasErrorCount(1)
      subject.hasErrorContaining("Injection of an @HiltViewModel class is prohibited")
    }
  }

  @Test
  fun injectOverriddenViewModelBindingIsAllowed() {
    val hiltActivity =
      """
      package test;

      import androidx.fragment.app.FragmentActivity;
      import dagger.hilt.android.AndroidEntryPoint;
      import javax.inject.Inject;

      @AndroidEntryPoint(FragmentActivity.class)
      public class TestActivity extends Hilt_TestActivity {
        @Inject Foo foo;
      }
      """
        .toJFO("test.TestActivity")
    val hiltViewModel =
      """
        package test;

        import androidx.lifecycle.ViewModel;
        import dagger.hilt.android.lifecycle.HiltViewModel;
        import javax.inject.Inject;

        @HiltViewModel
        class MyViewModel extends ViewModel {
            @Inject MyViewModel() { }
        }
        """
        .toJFO("test.MyViewModel")
    val foo =
      """
        package test;

        import javax.inject.Inject;

        final class Foo {
            @Inject Foo(MyViewModel viewModel) {}
        }
    """
        .toJFO("test.Foo")
    val activityModule =
      """
        package test;

        import dagger.Module;
        import dagger.Provides;
        import dagger.hilt.InstallIn;
        import dagger.hilt.android.components.ActivityComponent;

        @InstallIn(ActivityComponent.class)
        @Module
        public final class ActivityModule {
          @Provides static MyViewModel provideMyViewModel() {
            // Normally you'd expect this to use a ViewModelProvider or something but
            // since this test is just testing the binding graph, for simplicity just return
            // null.
            return null;
          }
        }
    """
        .toJFO("test.ActivityModule")

    testCompiler(foo, activityModule, hiltViewModel, hiltAndroidApp, hiltActivity).compile() {
      subject ->
      subject.hasErrorCount(0)
    }
  }

  @Test
  fun injectQualifiedViewModelBindingIsAllowed() {
    val hiltActivity =
      """
      package test;

      import androidx.fragment.app.FragmentActivity;
      import dagger.hilt.android.AndroidEntryPoint;
      import javax.inject.Inject;

      @AndroidEntryPoint(FragmentActivity.class)
      public class TestActivity extends Hilt_TestActivity {
        @Inject Foo foo;
      }
      """
        .toJFO("test.TestActivity")
    val hiltViewModel =
      """
        package test;

        import androidx.lifecycle.ViewModel;
        import dagger.hilt.android.lifecycle.HiltViewModel;
        import javax.inject.Inject;

        @HiltViewModel
        class MyViewModel extends ViewModel {
            @Inject MyViewModel() { }
        }
        """
        .toJFO("test.MyViewModel")
    val foo =
      """
        package test;

        import javax.inject.Inject;

        final class Foo {
            @Inject Foo(@ActivityModule.MyQualifier MyViewModel viewModel) {}
        }
    """
        .toJFO("test.Foo")
    val activityModule =
      """
        package test;

        import dagger.Module;
        import dagger.Provides;
        import dagger.hilt.InstallIn;
        import dagger.hilt.android.components.ActivityComponent;
        import javax.inject.Qualifier;

        @InstallIn(ActivityComponent.class)
        @Module
        public final class ActivityModule {
          @Qualifier
          public @interface MyQualifier {}

          @Provides
          @MyQualifier
          static MyViewModel provideMyViewModel() {
            // Normally you'd expect this to use a ViewModelProvider or something but
            // since this test is just testing the binding graph, for simplicity just return
            // null.
            return null;
          }
        }
    """
        .toJFO("test.ActivityModule")

    testCompiler(foo, activityModule, hiltViewModel, hiltAndroidApp, hiltActivity).compile() {
      subject ->
      subject.hasErrorCount(0)
    }
  }

  // Regression test for not handling array types properly
  @Test
  fun correctlyAllowsOtherBindings() {
    val hiltActivity =
      """
      package test;

      import androidx.fragment.app.FragmentActivity;
      import dagger.hilt.android.AndroidEntryPoint;
      import javax.inject.Inject;

      @AndroidEntryPoint(FragmentActivity.class)
      public class TestActivity extends Hilt_TestActivity {
        @Inject Foo foo;
      }
      """
        .toJFO("test.TestActivity")
    val hiltViewModel =
      """
        package test;

        import androidx.lifecycle.ViewModel;
        import dagger.hilt.android.lifecycle.HiltViewModel;
        import javax.inject.Inject;

        @HiltViewModel
        class MyViewModel extends ViewModel {
            @Inject MyViewModel() { }
        }
        """
        .toJFO("test.MyViewModel")
    val foo =
      """
        package test;

        import javax.inject.Inject;

        final class Foo {
            @Inject Foo(Long[] longArray) {}
        }
    """
        .toJFO("test.Foo")
    val activityModule =
      """
        package test;

        import dagger.Module;
        import dagger.Provides;
        import dagger.hilt.InstallIn;
        import dagger.hilt.android.components.ActivityComponent;

        @InstallIn(ActivityComponent.class)
        @Module
        public final class ActivityModule {
          @Provides
          static Long[] provideLongArray() {
            return null;
          }
        }
    """
        .toJFO("test.ActivityModule")

    testCompiler(foo, activityModule, hiltViewModel, hiltAndroidApp, hiltActivity).compile() {
      subject ->
      subject.hasErrorCount(0)
    }
  }
}

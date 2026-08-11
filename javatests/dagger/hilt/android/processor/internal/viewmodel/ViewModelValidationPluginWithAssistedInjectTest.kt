/*
 * Copyright (C) 2023 The Dagger Authors.
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
import com.google.common.collect.ImmutableMap
import dagger.hilt.android.testing.compile.HiltCompilerTests
import dagger.internal.codegen.ComponentProcessor
import dagger.internal.codegen.KspComponentProcessor
import dagger.testing.compile.CompilerTests.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@ExperimentalProcessingApi
@RunWith(JUnit4::class)
class ViewModelValidationPluginWithAssistedInjectTest {

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
      .withProcessorOptions(ImmutableMap.of("dagger.hilt.enableAssistedInjectViewModels", "true"))

  private val hiltAndroidApp =
    """
      package dagger.hilt.android.test;

      import android.app.Application;
      import dagger.hilt.android.HiltAndroidApp;

      @HiltAndroidApp(Application.class)
      public class TestApplication extends Hilt_TestApplication {}
      """
      .toJFO("dagger.hilt.android.test.TestApplication")

  @Test
  fun injectViewModelAssistedFactoryProhibited() {
    val hiltActivity =
      """
      package dagger.hilt.android.test;

      import androidx.fragment.app.FragmentActivity;
      import dagger.hilt.android.AndroidEntryPoint;
      import javax.inject.Inject;

      @AndroidEntryPoint(FragmentActivity.class)
      public class TestActivity extends Hilt_TestActivity {
        @Inject Foo foo;
      }
      """
        .toJFO("dagger.hilt.android.test.TestActivity")
    val hiltViewModel =
      """
      package dagger.hilt.android.test;

      import androidx.lifecycle.ViewModel;
      import dagger.assisted.Assisted;
      import dagger.assisted.AssistedFactory;
      import dagger.assisted.AssistedInject;
      import dagger.hilt.android.lifecycle.HiltViewModel;
      import javax.inject.Inject;

      @HiltViewModel(assistedFactory = MyViewModel.Factory.class)
      class MyViewModel extends ViewModel {
          @AssistedInject MyViewModel(@Assisted String s) { }
          @AssistedFactory interface Factory {
              MyViewModel create(String s);
          }
      }
      """
        .toJFO("dagger.hilt.android.test.MyViewModel")
    val foo =
      """
      package dagger.hilt.android.test;

      import javax.inject.Inject;

      final class Foo {
          @Inject Foo(MyViewModel.Factory factory) {}
      }
      """
        .toJFO("dagger.hilt.android.test.Foo")

    testCompiler(foo, hiltViewModel, hiltAndroidApp, hiltActivity).compile { subject ->
      subject.compilationDidFail()
      subject.hasErrorCount(1)
      subject.hasErrorContaining(
        "Injection of an assisted factory for Hilt ViewModel is prohibited since it can not be " +
          "used to create a ViewModel instance correctly.\n" +
          "Access the ViewModel via the Android APIs (e.g. ViewModelProvider) instead.\n" +
          "Injected factory: dagger.hilt.android.test.MyViewModel.Factory"
      )
    }
  }

  @Test
  fun fieldInjectViewModelAssistedFactoryProhibited() {
    val hiltActivity =
      """
      package dagger.hilt.android.test;

      import androidx.fragment.app.FragmentActivity;
      import dagger.hilt.android.AndroidEntryPoint;
      import javax.inject.Inject;

      @AndroidEntryPoint(FragmentActivity.class)
      public class TestActivity extends Hilt_TestActivity {
        @Inject MyViewModel.Factory factory;
      }
      """
        .toJFO("dagger.hilt.android.test.TestActivity")
    val hiltViewModel =
      """
      package dagger.hilt.android.test;

      import androidx.lifecycle.ViewModel;
      import dagger.assisted.Assisted;
      import dagger.assisted.AssistedFactory;
      import dagger.assisted.AssistedInject;
      import dagger.hilt.android.lifecycle.HiltViewModel;
      import javax.inject.Inject;

      @HiltViewModel(assistedFactory = MyViewModel.Factory.class)
      class MyViewModel extends ViewModel {
          @AssistedInject MyViewModel(@Assisted String s) { }
          @AssistedFactory interface Factory {
              MyViewModel create(String s);
          }
      }
      """
        .toJFO("dagger.hilt.android.test.MyViewModel")

    testCompiler(hiltViewModel, hiltAndroidApp, hiltActivity).compile { subject ->
      subject.compilationDidFail()
      subject.hasErrorCount(1)
      subject.hasErrorContaining(
        "Injection of an assisted factory for Hilt ViewModel is prohibited since it can not be " +
          "used to create a ViewModel instance correctly.\n" +
          "Access the ViewModel via the Android APIs (e.g. ViewModelProvider) instead.\n" +
          "Injected factory: dagger.hilt.android.test.MyViewModel.Factory"
      )
    }
  }

  @Test
  fun injectGenericViewModelAssistedFactoryProhibited() {
    val hiltActivity =
      """
      package dagger.hilt.android.test;

      import androidx.fragment.app.FragmentActivity;
      import dagger.hilt.android.AndroidEntryPoint;
      import javax.inject.Inject;

      @AndroidEntryPoint(FragmentActivity.class)
      public class TestActivity extends Hilt_TestActivity {
        @Inject MyViewModel.Factory factory;
      }
      """
        .toJFO("dagger.hilt.android.test.TestActivity")
    val hiltViewModel =
      """
      package dagger.hilt.android.test;

      import androidx.lifecycle.ViewModel;
      import dagger.assisted.Assisted;
      import dagger.assisted.AssistedFactory;
      import dagger.assisted.AssistedInject;
      import dagger.hilt.android.lifecycle.HiltViewModel;
      import javax.inject.Inject;

      @HiltViewModel(assistedFactory = MyViewModel.Factory.class)
      class MyViewModel extends ViewModel {
          @AssistedInject MyViewModel(@Assisted String s) { }
          interface SingleAssistedFactory<A, T> {
              T create(A a);
          }
          @AssistedFactory interface Factory extends SingleAssistedFactory<String, MyViewModel> {}
      }
      """
        .toJFO("dagger.hilt.android.test.MyViewModel")

    testCompiler(hiltViewModel, hiltAndroidApp, hiltActivity).compile { subject ->
      subject.compilationDidFail()
      subject.hasErrorCount(1)
      subject.hasErrorContaining(
        "Injection of an assisted factory for Hilt ViewModel is prohibited since it can not be " +
          "used to create a ViewModel instance correctly.\n" +
          "Access the ViewModel via the Android APIs (e.g. ViewModelProvider) instead.\n" +
          "Injected factory: dagger.hilt.android.test.MyViewModel.Factory"
      )
    }
  }
}

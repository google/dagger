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

package dagger.hilt.android;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.os.Build;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.annotation.Nullable;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.lifecycle.HiltViewModelExtensions;
import dagger.hilt.android.scopes.ViewModelScoped;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
// Robolectric requires Java9 to run API 29 and above, so use API 28 instead
@Config(sdk = Build.VERSION_CODES.P, application = HiltTestApplication.class)
public class ViewModelAssistedTest {

  @Rule public final HiltAndroidRule rule = new HiltAndroidRule(this);

  @Test
  public void testConfigChange() {
    try (ActivityScenario<TestConfigChangeActivity> scenario =
        ActivityScenario.launch(TestConfigChangeActivity.class)) {
      scenario.onActivity(
          activity -> {
            assertThat(activity.vm.one.bar).isNotNull();
            assertThat(activity.vm.one.bar).isSameInstanceAs(activity.vm.two.bar);
            assertThat(activity.vm.s).isEqualTo("foo");
          });
      scenario.recreate();
      scenario.onActivity(
          activity -> {
            // Check that we still get the same ViewModel instance after config change and the
            // passed assisted arg has no effect anymore.
            assertThat(activity.vm.s).isEqualTo("foo");
          });
    }
  }

  @Test
  public void testKeyedViewModels() {
    try (ActivityScenario<TestKeyedViewModelActivity> scenario =
        ActivityScenario.launch(TestKeyedViewModelActivity.class)) {
      scenario.onActivity(
          activity -> {
            assertThat(activity.vm1.s).isEqualTo("foo");
            assertThat(activity.vm2.s).isEqualTo("bar");
          });
    }
  }

  @Test
  public void testNoCreationCallbacks() {
    Exception exception =
        assertThrows(
            IllegalStateException.class,
            () -> ActivityScenario.launch(TestNoCreationCallbacksActivity.class).close());
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Found @HiltViewModel-annotated class"
                + " dagger.hilt.android.ViewModelAssistedTest$MyViewModel"
                + " using @AssistedInject but no creation callback was provided"
                + " in CreationExtras.");
  }

  @Test
  public void testNoFactory() {
    Exception exception =
        assertThrows(
            RuntimeException.class,
            () -> ActivityScenario.launch(TestNoFactoryActivity.class).close());
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Found creation callback but class"
                + " dagger.hilt.android.ViewModelAssistedTest$MyInjectedViewModel does not have an"
                + " assisted factory specified in @HiltViewModel.");
  }

  @Test
  public void testFragmentArgs() {
    try (ActivityScenario<TestFragmentArgsActivity> scenario =
        ActivityScenario.launch(TestFragmentArgsActivity.class)) {
      scenario.onActivity(
          activity -> {
            TestFragment fragment =
                (TestFragment) activity.getSupportFragmentManager().findFragmentByTag("tag");
            assertThat(fragment.vm.handle.<String>get("key")).isEqualTo("foobar");
          });
    }
  }

  @Test
  public void testIncompatibleFactories() {
    Exception exception =
        assertThrows(
            ClassCastException.class,
            () -> ActivityScenario.launch(TestIncompatibleFactoriesActivity.class).close());
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "class dagger.hilt.android.ViewModelAssistedTest_MyViewModel_Factory_Impl cannot be"
                + " cast to class"
                + " dagger.hilt.android.ViewModelAssistedTest$MyViewModel$AnotherFactory");
  }

  @AndroidEntryPoint(FragmentActivity.class)
  public static class TestConfigChangeActivity
      extends Hilt_ViewModelAssistedTest_TestConfigChangeActivity {

    MyViewModel vm;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      if (savedInstanceState == null) {
        vm =
            new ViewModelProvider(
                    getViewModelStore(),
                    getDefaultViewModelProviderFactory(),
                    HiltViewModelExtensions.withCreationCallback(
                        getDefaultViewModelCreationExtras(),
                        (MyViewModel.Factory factory) -> factory.create("foo")))
                .get(MyViewModel.class);
      } else {
        vm =
            new ViewModelProvider(
                    getViewModelStore(),
                    getDefaultViewModelProviderFactory(),
                    HiltViewModelExtensions.withCreationCallback(
                        getDefaultViewModelCreationExtras(),
                        (MyViewModel.Factory factory) -> factory.create("bar")))
                .get(MyViewModel.class);
      }
    }
  }

  @AndroidEntryPoint(FragmentActivity.class)
  public static class TestKeyedViewModelActivity
      extends Hilt_ViewModelAssistedTest_TestKeyedViewModelActivity {

    MyViewModel vm1;
    MyViewModel vm2;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      vm1 =
          new ViewModelProvider(
                  getViewModelStore(),
                  getDefaultViewModelProviderFactory(),
                  HiltViewModelExtensions.withCreationCallback(
                      getDefaultViewModelCreationExtras(),
                      (MyViewModel.Factory factory) -> factory.create("foo")))
              .get("a", MyViewModel.class);

      vm2 =
          new ViewModelProvider(
                  getViewModelStore(),
                  getDefaultViewModelProviderFactory(),
                  HiltViewModelExtensions.withCreationCallback(
                      getDefaultViewModelCreationExtras(),
                      (MyViewModel.Factory factory) -> factory.create("bar")))
              .get("b", MyViewModel.class);
    }
  }

  @AndroidEntryPoint(FragmentActivity.class)
  public static class TestNoCreationCallbacksActivity
      extends Hilt_ViewModelAssistedTest_TestNoCreationCallbacksActivity {

    MyViewModel vm;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      vm = new ViewModelProvider(this).get(MyViewModel.class);
    }
  }

  @AndroidEntryPoint(FragmentActivity.class)
  public static class TestNoFactoryActivity
      extends Hilt_ViewModelAssistedTest_TestNoFactoryActivity {

    MyInjectedViewModel vm;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);

      vm =
          new ViewModelProvider(
                  getViewModelStore(),
                  getDefaultViewModelProviderFactory(),
                  HiltViewModelExtensions.withCreationCallback(
                      getDefaultViewModelCreationExtras(),
                      (MyViewModel.Factory factory) -> factory.create("bar")))
              .get(MyInjectedViewModel.class);
    }
  }

  @AndroidEntryPoint(FragmentActivity.class)
  public static class TestFragmentArgsActivity
      extends Hilt_ViewModelAssistedTest_TestFragmentArgsActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      if (savedInstanceState == null) {
        Fragment f =
            getSupportFragmentManager()
                .getFragmentFactory()
                .instantiate(TestFragment.class.getClassLoader(), TestFragment.class.getName());
        Bundle b = new Bundle();
        b.putString("key", "foobar");
        f.setArguments(b);
        getSupportFragmentManager().beginTransaction().add(0, f, "tag").commitNow();
      }
    }
  }

  @AndroidEntryPoint(FragmentActivity.class)
  public static class TestIncompatibleFactoriesActivity
      extends Hilt_ViewModelAssistedTest_TestIncompatibleFactoriesActivity {

    MyViewModel vm;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      vm =
          new ViewModelProvider(
                  getViewModelStore(),
                  getDefaultViewModelProviderFactory(),
                  HiltViewModelExtensions.withCreationCallback(
                      getDefaultViewModelCreationExtras(),
                      (MyViewModel.AnotherFactory factory) -> factory.create("foo")))
              .get(MyViewModel.class);
    }
  }

  @AndroidEntryPoint(Fragment.class)
  public static class TestFragment extends Hilt_ViewModelAssistedTest_TestFragment {

    MyViewModel vm;

    @Override
    public void onCreate(@Nullable Bundle bundle) {
      super.onCreate(bundle);
      vm =
          new ViewModelProvider(
                  getViewModelStore(),
                  getDefaultViewModelProviderFactory(),
                  HiltViewModelExtensions.withCreationCallback(
                      getDefaultViewModelCreationExtras(),
                      (MyViewModel.Factory factory) -> factory.create("foo")))
              .get(MyViewModel.class);
    }
  }

  @HiltViewModel(assistedFactory = MyViewModel.Factory.class)
  static class MyViewModel extends ViewModel {

    final DependsOnBarOne one;
    final DependsOnBarTwo two;
    final SavedStateHandle handle;
    final String s;
    boolean cleared = false;

    @AssistedInject
    MyViewModel(
        DependsOnBarOne one,
        DependsOnBarTwo two,
        ViewModelLifecycle lifecycle,
        SavedStateHandle handle,
        @Assisted String s) {
      this.one = one;
      this.two = two;
      this.s = s;
      this.handle = handle;
      lifecycle.addOnClearedListener(() -> cleared = true);
    }

    @AssistedFactory
    interface Factory {
      MyViewModel create(String s);
    }

    @AssistedFactory
    interface AnotherFactory {
      MyViewModel create(String s);
    }
  }

  @HiltViewModel
  static class MyInjectedViewModel extends ViewModel {

    final DependsOnBarOne one;
    final DependsOnBarTwo two;
    final SavedStateHandle handle;
    boolean cleared = false;

    @Inject
    MyInjectedViewModel(
        DependsOnBarOne one,
        DependsOnBarTwo two,
        ViewModelLifecycle lifecycle,
        SavedStateHandle handle) {
      this.one = one;
      this.two = two;
      this.handle = handle;
      lifecycle.addOnClearedListener(() -> cleared = true);
    }
  }

  @ViewModelScoped
  static class Bar {
    @Inject
    Bar() {}
  }

  static class DependsOnBarOne {
    final Bar bar;

    @Inject
    DependsOnBarOne(Bar bar) {
      this.bar = bar;
    }
  }

  static class DependsOnBarTwo {
    final Bar bar;

    @Inject
    DependsOnBarTwo(Bar bar) {
      this.bar = bar;
    }
  }
}

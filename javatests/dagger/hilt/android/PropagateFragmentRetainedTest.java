/*
 * Copyright (C) 2025 The Dagger Authors.
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

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.FragmentRetainedComponent;
import dagger.hilt.android.scopes.FragmentRetainedScoped;
import dagger.hilt.android.scopes.FragmentScoped;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import javax.inject.Inject;
import javax.inject.Qualifier;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
@Config(application = HiltTestApplication.class)
public class PropagateFragmentRetainedTest {

  @Rule public final HiltAndroidRule rule = new HiltAndroidRule(this);

  @Test
  public void propagatedFragmentRetainedIsAccessibleAndIsRetained() {
    try (ActivityScenario<TestActivity> scenario = ActivityScenario.launch(TestActivity.class)) {
      scenario.onActivity(
          activity -> {
            TestFragment fragment = new TestFragment();
            activity
                .getSupportFragmentManager()
                .beginTransaction()
                .add(fragment, "test")
                .commitNow();
            assertThat(fragment.fragmentFoo.testValue).isEmpty();
            assertThat(fragment.fragmentFoo.fragmentRetainedFoo.testValue).isEqualTo("");
            fragment.fragmentFoo.testValue = "beforeRecreation";
            fragment.fragmentFoo.fragmentRetainedFoo.testValue = "beforeRecreation";
          });
      scenario.recreate();
      scenario.onActivity(
          activity -> {
            TestFragment fragment =
                (TestFragment) activity.getSupportFragmentManager().findFragmentByTag("test");
            // The non-retained value should have be reset since it is a new object
            assertThat(fragment.fragmentFoo.testValue).isEmpty();
            // The retained value should have been kept
            assertThat(fragment.fragmentFoo.fragmentRetainedFoo.testValue)
                .isEqualTo("beforeRecreation");
          });
    }
  }

  @Test
  public void secondFragmentHasDifferentFragmentRetainedObject() {
    try (ActivityScenario<TestActivity> scenario = ActivityScenario.launch(TestActivity.class)) {
      scenario.onActivity(
          activity -> {
            TestFragment fragment = new TestFragment();
            activity.getSupportFragmentManager().beginTransaction().add(fragment, "test1")
                .commitNow();
            TestFragment fragment2 = new TestFragment();
            activity.getSupportFragmentManager().beginTransaction().add(fragment2, "test2")
                .commitNow();
            assertThat(fragment.fragmentFoo.fragmentRetainedFoo).isNotSameInstanceAs(
                fragment2.fragmentFoo.fragmentRetainedFoo);
          });
    }
  }

  @Test
  public void multipleBindingsWithSimilarSimpleNames() {
    try (ActivityScenario<TestActivity> scenario = ActivityScenario.launch(TestActivity.class)) {
      scenario.onActivity(
          activity -> {
            TestFragment fragment = new TestFragment();
            activity.getSupportFragmentManager().beginTransaction().add(fragment, "test")
                .commitNow();
            assertThat(fragment.bar.value).isEqualTo("bar");
            assertThat(fragment.duplicateBar.value).isEqualTo("dup bar");
          });
    }
  }

  @Test
  public void moduleBinds() {
    try (ActivityScenario<TestActivity> scenario = ActivityScenario.launch(TestActivity.class)) {
      scenario.onActivity(
          activity -> {
            TestFragment fragment = new TestFragment();
            activity.getSupportFragmentManager().beginTransaction().add(fragment, "test")
                .commitNow();
            assertThat(fragment.baz).isInstanceOf(BazImpl.class);
          });
    }
  }

  @Test
  public void qualifiedBinding() {
    try (ActivityScenario<TestActivity> scenario = ActivityScenario.launch(TestActivity.class)) {
      scenario.onActivity(
          activity -> {
            TestFragment fragment = new TestFragment();
            activity.getSupportFragmentManager().beginTransaction().add(fragment, "test")
                .commitNow();
            assertThat(fragment.qualifiedBar.value).isEqualTo("qualified bar");
          });
    }
  }

  @PropagateFragmentRetained
  @FragmentRetainedScoped
  public static final class FragmentRetainedFoo {
    String testValue = "";

    @Inject FragmentRetainedFoo() {}
  }

  // Not used in a test, but the existence tests that propagated unused bindings do not cause issues
  @PropagateFragmentRetained
  @FragmentRetainedScoped
  public static final class UnusedFragmentRetained {
    @Inject UnusedFragmentRetained(Object missingBinding) {}
  }

  @FragmentRetainedScoped
  public static final class UnpropagatedFragmentRetained {
    @Inject UnpropagatedFragmentRetained() {}
  }

  @FragmentScoped
  public static final class FragmentFoo {
    final FragmentRetainedFoo fragmentRetainedFoo;
    String testValue = "";

    @Inject
    FragmentFoo(
        FragmentRetainedFoo fragmentRetainedFoo) {
      this.fragmentRetainedFoo = fragmentRetainedFoo;
    }
  }

  @AndroidEntryPoint(FragmentActivity.class)
  public static final class TestActivity
      extends Hilt_PropagateFragmentRetainedTest_TestActivity {
  }

  @AndroidEntryPoint(Fragment.class)
  public static final class TestFragment
      extends Hilt_PropagateFragmentRetainedTest_TestFragment {
    @Inject FragmentFoo fragmentFoo;
    @Inject Bar bar;
    @Inject Duplicate.Bar duplicateBar;
    @Inject Baz baz;
    @Inject @TestQualifier Bar qualifiedBar;
  }

  // Two classes with the same simple name
  public static final class Bar {
    final String value;

    Bar(String value) {
      this.value = value;
    }
  }

  public static final class Duplicate {
    public static final class Bar {
      final String value;

      Bar(String value) {
        this.value = value;
      }
    }
  }

  public interface Baz {}

  public static final class BazImpl implements Baz {
    @Inject BazImpl() {}
  }

  @Qualifier
  public @interface TestQualifier {}

  @Module
  @InstallIn(FragmentRetainedComponent.class)
  public abstract static class FragmentRetainedModule {
    @Provides
    @FragmentRetainedScoped
    @PropagateFragmentRetained
    static Bar provideBar() {
      return new Bar("bar");
    }

    @Provides
    @FragmentRetainedScoped
    @PropagateFragmentRetained
    static Duplicate.Bar provideDuplicateBar() {
      return new Duplicate.Bar("dup bar");
    }

    // It isn't really great practice to scope the @Binds, but you need full graph knowledge to
    // know that the impl is scoped, so we require the extra scope anyway.
    @Binds
    @FragmentRetainedScoped
    @PropagateFragmentRetained
    abstract Baz bindBaz(BazImpl impl);

    @Provides
    @FragmentRetainedScoped
    @PropagateFragmentRetained
    @TestQualifier
    static Bar provideQualifiedBar() {
      return new Bar("qualified bar");
    }
  }
}

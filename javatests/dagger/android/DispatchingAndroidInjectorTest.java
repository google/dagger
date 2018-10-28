/*
 * Copyright (C) 2017 The Dagger Authors.
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

package dagger.android;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import android.app.Activity;
import com.google.common.collect.ImmutableMap;
import dagger.android.AndroidInjector.Factory;
import dagger.android.DispatchingAndroidInjector.InvalidInjectorBindingException;
import java.util.Map;
import javax.inject.Provider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public final class DispatchingAndroidInjectorTest {
  @Test
  public void withClassKeys() {
    DispatchingAndroidInjector<Activity> dispatchingAndroidInjector =
        newDispatchingAndroidInjector(
            ImmutableMap.of(FooActivity.class, FooInjector.Factory::new), ImmutableMap.of());

    FooActivity activity = Robolectric.setupActivity(FooActivity.class);
    assertThat(dispatchingAndroidInjector.maybeInject(activity)).isTrue();
  }

  @Test
  public void withStringKeys() {
    DispatchingAndroidInjector<Activity> dispatchingAndroidInjector =
        newDispatchingAndroidInjector(
            ImmutableMap.of(),
            ImmutableMap.of(FooActivity.class.getName(), FooInjector.Factory::new));

    FooActivity activity = Robolectric.setupActivity(FooActivity.class);
    assertThat(dispatchingAndroidInjector.maybeInject(activity)).isTrue();
  }

  @Test
  public void withMixedKeys() {
    DispatchingAndroidInjector<Activity> dispatchingAndroidInjector =
        newDispatchingAndroidInjector(
            ImmutableMap.of(FooActivity.class, FooInjector.Factory::new),
            ImmutableMap.of(BarActivity.class.getName(), BarInjector.Factory::new));

    FooActivity fooActivity = Robolectric.setupActivity(FooActivity.class);
    assertThat(dispatchingAndroidInjector.maybeInject(fooActivity)).isTrue();
    BarActivity barActivity = Robolectric.setupActivity(BarActivity.class);
    assertThat(dispatchingAndroidInjector.maybeInject(barActivity)).isTrue();
  }

  @Test
  public void maybeInject_returnsFalse_ifNoMatchingInjectorExists() {
    DispatchingAndroidInjector<Activity> dispatchingAndroidInjector =
        newDispatchingAndroidInjector(ImmutableMap.of(), ImmutableMap.of());

    BarActivity activity = Robolectric.setupActivity(BarActivity.class);
    assertThat(dispatchingAndroidInjector.maybeInject(activity)).isFalse();
  }

  @Test
  public void throwsIfFactoryCreateReturnsNull() {
    DispatchingAndroidInjector<Activity> dispatchingAndroidInjector =
        newDispatchingAndroidInjector(
            ImmutableMap.of(FooActivity.class, () -> null), ImmutableMap.of());
    FooActivity activity = Robolectric.setupActivity(FooActivity.class);

    try {
      dispatchingAndroidInjector.maybeInject(activity);
      fail("Expected NullPointerException");
    } catch (NullPointerException expected) {
    }
  }

  @Test
  public void throwsIfClassMismatched() {
    DispatchingAndroidInjector<Activity> dispatchingAndroidInjector =
        newDispatchingAndroidInjector(
            ImmutableMap.of(FooActivity.class, BarInjector.Factory::new), ImmutableMap.of());
    FooActivity activity = Robolectric.setupActivity(FooActivity.class);

    try {
      dispatchingAndroidInjector.maybeInject(activity);
      fail("Expected InvalidInjectorBindingException");
    } catch (InvalidInjectorBindingException expected) {
    }
  }

  private static <T> DispatchingAndroidInjector<T> newDispatchingAndroidInjector(
      Map<Class<?>, Provider<Factory<?>>> injectorFactoriesWithClassKeys,
      Map<String, Provider<AndroidInjector.Factory<?>>>
          injectorFactoriesWithStringKeys) {
    return new DispatchingAndroidInjector<>(
        injectorFactoriesWithClassKeys,
        injectorFactoriesWithStringKeys ,
        ImmutableMap.of(),
        ImmutableMap.of());
  }

  static class FooActivity extends Activity {}

  static class BarActivity extends Activity {}

  static class FooInjector implements AndroidInjector<FooActivity> {
    @Override
    public void inject(FooActivity instance) {}

    static class Factory implements AndroidInjector.Factory<FooActivity> {
      @Override
      public AndroidInjector<FooActivity> create(FooActivity activity) {
        return new FooInjector();
      }
    }
  }

  static class BarInjector implements AndroidInjector<BarActivity> {
    @Override
    public void inject(BarActivity instance) {}

    static class Factory implements AndroidInjector.Factory<BarActivity> {
      @Override
      public AndroidInjector<BarActivity> create(BarActivity activity) {
        return new BarInjector();
      }
    }
  }
}

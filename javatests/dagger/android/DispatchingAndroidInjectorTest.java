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
import org.robolectric.RobolectricTestRunner;
import dagger.android.DispatchingAndroidInjector.InvalidInjectorBindingException;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Provider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;

@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public final class DispatchingAndroidInjectorTest {
  private DispatchingAndroidInjector<Activity> dispatchingAndroidInjector;

  @Before
  public void setup() {
    Map<Class<? extends Activity>, Provider<AndroidInjector.Factory<? extends Activity>>>
        injectorFactories = new HashMap<>();
    injectorFactories.put(FooActivity.class, FooInjector.Factory::new);
    injectorFactories.put(ReturnsNullActivity.class, () -> null);
    injectorFactories.put(WrongActivity.class, FooInjector.Factory::new);
    dispatchingAndroidInjector = new DispatchingAndroidInjector<>(injectorFactories);
  }

  @Test
  public void maybeInject_returnsTrue_ifMatchingInjectorExists() {
    FooActivity fooActivity = Robolectric.setupActivity(FooActivity.class);
    assertThat(dispatchingAndroidInjector.maybeInject(fooActivity)).isTrue();
  }

  @Test
  public void maybeInject_returnsFalse_ifNoMatchingInjectorExists() {
    BarActivity barActivity = Robolectric.setupActivity(BarActivity.class);
    assertThat(dispatchingAndroidInjector.maybeInject(barActivity)).isFalse();
  }

  @Test
  public void throwsIfFactoryCreateReturnsNull() {
    ReturnsNullActivity returnsNullActivity = Robolectric.setupActivity(ReturnsNullActivity.class);

    try {
      dispatchingAndroidInjector.maybeInject(returnsNullActivity);
      fail("Expected NullPointerException");
    } catch (NullPointerException expected) {
    }
  }

  @Test
  public void throwsIfClassMismatched() {
    WrongActivity wrongActivity = Robolectric.setupActivity(WrongActivity.class);

    try {
      dispatchingAndroidInjector.maybeInject(wrongActivity);
      fail("Expected InvalidInjectorBindingException");
    } catch (InvalidInjectorBindingException expected) {
    }
  }

  static class FooActivity extends Activity {}

  static class BarActivity extends Activity {}

  static class ReturnsNullActivity extends Activity {}

  static class WrongActivity extends Activity {}

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
}

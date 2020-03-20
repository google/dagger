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

package dagger.hilt.android.example.gradle.simple;

import static com.google.common.truth.Truth.assertThat;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.GenerateComponents;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ApplicationComponent;
import dagger.hilt.android.testing.AndroidRobolectricEntryPoint;
import dagger.hilt.android.testing.HiltRobolectricTestRule;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** A simple test using Hilt. */
@GenerateComponents
@AndroidRobolectricEntryPoint
@RunWith(RobolectricTestRunner.class)
@Config(application = SimpleTest_Application.class)
public final class SimpleTest {
  private static final int TEST_VALUE = 9;

  @Module
  @InstallIn(ApplicationComponent.class)
  interface TestModule {
    @Provides
    static int provideInt() {
      return TEST_VALUE;
    }
  }

  static class Foo {
    final int value;

    @Inject
    Foo(int value) {
      this.value = value;
    }
  }

  @Rule public HiltRobolectricTestRule rule = new HiltRobolectricTestRule();

  @Inject @Model String model;
  @Inject Foo foo;

  // TODO(user): Add @BindValue

  @Test
  public void testInject() throws Exception {
    assertThat(model).isNull();

    SimpleTest_Application.get().inject(this);

    assertThat(model).isNotNull();
    assertThat(model).isEqualTo(android.os.Build.MODEL);
  }

  @Test
  public void testInjectFromTestModule() throws Exception {
    assertThat(foo).isNull();

    SimpleTest_Application.get().inject(this);

    assertThat(foo).isNotNull();
    assertThat(foo.value).isEqualTo(TEST_VALUE);
  }
}

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

import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.components.SingletonComponent;
import javax.inject.Inject;
import javax.inject.Named;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
// Robolectric requires Java9 to run API 29 and above, so use API 28 instead
@Config(sdk = Build.VERSION_CODES.P, application = HiltTestApplication.class)
public final class ModuleTest {
  public static class Dep1 {}
  public static class Dep2 {}
  public static class Dep3 {}
  public static class Dep4 { @Inject Dep4() {}}
  public static class Dep5 { @Inject Dep5() {}}
  public static class Dep6 {}
  public static class Dep7 {}

  @Rule public HiltAndroidRule rule = new HiltAndroidRule(this);

  // Test that modules with only static methods can have private constructors.
  @Module
  @InstallIn(SingletonComponent.class)
  static final class TestModule1 {
    private TestModule1() {} // This is fine because Dagger doesn't need an instance.

    @Provides
    static Dep1 provide() {
      return new Dep1();
    }
  }

  // Test that modules with only static methods can have constructors with parameters.
  @Module
  @InstallIn(SingletonComponent.class)
  static final class TestModule2 {
    TestModule2(String str) {} // This is fine because Dagger doesn't need an instance.

    @Provides
    static Dep2 provide() {
      return new Dep2();
    }
  }

  // Test that modules with non-static methods can have constructors with parameters if no-arg
  // constructor exists.
  @Module
  @InstallIn(SingletonComponent.class)
  static final class TestModule3 {
    TestModule3() {
      this("");
    }

    TestModule3(String str) {} // This is fine because Dagger can use the other constructor

    @Provides
    Dep3 provide() {
      return new Dep3();
    }
  }

  // Test that modules with only abstract methods can have private constructors.
  @Module
  @InstallIn(SingletonComponent.class)
  @SuppressWarnings("ClassCanBeStatic") // purposely testing non-static class here
  abstract class TestModule4 {
    private TestModule4() {}  // This is fine because Dagger doesn't need an instance.

    @Binds @Named("Dep4") abstract Object bind(Dep4 impl);
  }

  // Test that modules with only abstract methods can have constructors with parameters.
  @Module
  @InstallIn(SingletonComponent.class)
  @SuppressWarnings("ClassCanBeStatic") // purposely testing non-static class here
  abstract class TestModule5 {
    TestModule5(String str) {} // This is fine because Dagger doesn't need an instance.

    @Binds @Named("Dep5") abstract Object bind(Dep5 impl);
  }

  // Test that static modules with no methods can have private constructors.
  @Module
  @InstallIn(SingletonComponent.class)
  static final class TestModule6 {
    private TestModule6() {} // This is fine because Dagger doesn't need an instance.
  }

  // Test that static modules with no methods can have constructors with parameters.
  @Module
  @InstallIn(SingletonComponent.class)
  static final class TestModule7 {
    TestModule7(String str) {} // This is fine because Dagger doesn't need an instance.
  }

  // Test that abstract modules with no methods can have private constructors.
  @Module
  @InstallIn(SingletonComponent.class)
  @SuppressWarnings("ClassCanBeStatic") // purposely testing non-static class here
  abstract class TestModule8 {
    private TestModule8() {} // This is fine because Dagger doesn't need an instance.
  }

  // Test that abstract modules with no methods can have constructors with parameters.
  @Module
  @InstallIn(SingletonComponent.class)
  @SuppressWarnings("ClassCanBeStatic") // purposely testing non-static class here
  abstract class TestModule9 {
    TestModule9(String str) {} // This is fine because Dagger doesn't need an instance.
  }

  @Inject Dep1 dep1;
  @Inject Dep2 dep2;
  @Inject Dep5 dep3;
  @Inject @Named("Dep4") Object dep4;
  @Inject @Named("Dep5") Object dep5;

  @Before
  public void setup() {
    rule.inject();
  }

  @Test
  public void testDep1() throws Exception {
    assertThat(dep1).isNotNull();
  }

  @Test
  public void testDep2() throws Exception {
    assertThat(dep2).isNotNull();
  }

  @Test
  public void testDep3() throws Exception {
    assertThat(dep3).isNotNull();
  }

  @Test
  public void testDep4() throws Exception {
    assertThat(dep4).isNotNull();
    assertThat(dep4).isInstanceOf(Dep4.class);
  }

  @Test
  public void testDep5() throws Exception {
    assertThat(dep5).isNotNull();
    assertThat(dep5).isInstanceOf(Dep5.class);
  }
}

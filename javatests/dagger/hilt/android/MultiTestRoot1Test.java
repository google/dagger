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

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.os.Build;
import androidx.activity.ComponentActivity;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.EntryPoint;
import dagger.hilt.EntryPoints;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ActivityComponent;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import dagger.hilt.components.SingletonComponent;
import javax.inject.Inject;
import javax.inject.Named;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

// TODO(bcorso): Support transitively ignoring the @Module.includes of ignored modules?
// TODO(bcorso): Support including non-test @UninstallModules using @UninstallModules.includes?
@UninstallModules({
  MultiTestRootExternalModules.PkgPrivateAppModule.class,
  MultiTestRootExternalModules.PkgPrivateActivityModule.class
})
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
// Robolectric requires Java9 to run API 29 and above, so use API 28 instead
@Config(sdk = Build.VERSION_CODES.P, application = HiltTestApplication.class)
public final class MultiTestRoot1Test {
  private static final int INT_VALUE = 9;
  private static final String STR_VALUE = "MultiTestRoot1TestValue";
  private static final long LONG_VALUE = 11L;
  private static final String REPLACE_EXTERNAL_STR_VALUE = "REPLACED_EXTERNAL_STR_VALUE";
  private static final long REPLACE_EXTERNAL_LONG_VALUE = 17L;
  private static final String BIND_VALUE_STRING = "BIND_VALUE_STRING";
  private static final String TEST_QUALIFIER = "TEST_QUALIFIER";

  @AndroidEntryPoint(ComponentActivity.class)
  public static class TestActivity extends Hilt_MultiTestRoot1Test_TestActivity {
    @Inject Baz baz;
    @Inject @MultiTestRootExternalModules.External Long externalLongValue;
  }

  @EntryPoint
  @InstallIn(SingletonComponent.class)
  public interface BindValueEntryPoint {
    @Named(TEST_QUALIFIER)
    String bindValueString();
  }

  @Module
  @InstallIn(SingletonComponent.class)
  public interface ReplaceExternalAppModule {
    @Provides
    @MultiTestRootExternalModules.External
    static String provideString() {
      return REPLACE_EXTERNAL_STR_VALUE;
    }
  }

  @Module
  @InstallIn(ActivityComponent.class)
  public interface ReplaceExternalActivityModule {
    @Provides
    @MultiTestRootExternalModules.External
    static Long provideString() {
      return REPLACE_EXTERNAL_LONG_VALUE;
    }
  }

  @Module
  @InstallIn(ActivityComponent.class)
  public interface TestActivityModule {
    @Provides
    static Baz provideBaz() {
      return new Baz(LONG_VALUE);
    }
  }

  @Module
  @InstallIn(SingletonComponent.class)
  interface PkgPrivateTestModule {
    @Provides
    static Qux provideQux() {
      return new Qux();
    }
  }

  @Module
  @InstallIn(SingletonComponent.class)
  public interface TestModule {
    @Provides
    static int provideInt() {
      return INT_VALUE;
    }

    @Provides
    static String provideString() {
      return STR_VALUE;
    }
  }

  public static final class Outer {
    @Module
    @InstallIn(SingletonComponent.class)
    public interface NestedTestModule {
      @Provides
      static long provideLong() {
        return LONG_VALUE;
      }
    }

    private Outer() {}
  }

  static class Foo {
    final int value;

    @Inject
    Foo(int value) {
      this.value = value;
    }
  }

  // Must be public due to b/183636779
  public static class Bar {
    final String value;

    Bar(String value) {
      this.value = value;
    }
  }

  static class Baz {
    final long value;

    Baz(long value) {
      this.value = value;
    }
  }

  // Must be public due to b/183636779
  public static class Qux {}

  @Module
  @InstallIn(SingletonComponent.class)
  public interface BarModule {
    @Provides
    static Bar provideBar(String value) {
      return new Bar(value);
    }
  }

  @EntryPoint
  @InstallIn(SingletonComponent.class)
  public interface BarEntryPoint {
    Bar getBar();
  }

  @EntryPoint
  @InstallIn(SingletonComponent.class)
  interface PkgPrivateQuxEntryPoint {
    Qux getQux();
  }

  @Rule public HiltAndroidRule rule = new HiltAndroidRule(this);

  @Inject Foo foo;
  @Inject Qux qux;
  @Inject Long longValue;
  @Inject @MultiTestRootExternalModules.External String externalStrValue;

  @BindValue
  @Named(TEST_QUALIFIER)
  String bindValueString = BIND_VALUE_STRING;

  @Test
  public void testInjectFromTestModule() throws Exception {
    assertThat(foo).isNull();
    setupComponent();
    assertThat(foo).isNotNull();
    assertThat(foo.value).isEqualTo(INT_VALUE);
  }

  @Test
  public void testInjectFromNestedTestModule() throws Exception {
    assertThat(longValue).isNull();
    setupComponent();
    assertThat(longValue).isNotNull();
    assertThat(longValue).isEqualTo(LONG_VALUE);
  }

  @Test
  public void testInjectFromExternalAppModule() throws Exception {
    assertThat(externalStrValue).isNull();
    setupComponent();
    assertThat(externalStrValue).isNotNull();
    assertThat(externalStrValue).isEqualTo(REPLACE_EXTERNAL_STR_VALUE);
    assertThat(externalStrValue).isNotEqualTo(MultiTestRootExternalModules.EXTERNAL_STR_VALUE);
  }

  @Test
  public void testInjectFromExternalActivityModule() throws Exception {
    setupComponent();
    ActivityController<TestActivity> ac = Robolectric.buildActivity(TestActivity.class);
    assertThat(ac.get().externalLongValue).isNull();
    ac.create();
    assertThat(ac.get().externalLongValue).isNotNull();
    assertThat(ac.get().externalLongValue).isEqualTo(REPLACE_EXTERNAL_LONG_VALUE);
    assertThat(ac.get().externalLongValue)
        .isNotEqualTo(MultiTestRootExternalModules.EXTERNAL_LONG_VALUE);
  }

  @Test
  public void testInjectFromPkgPrivateTestModule() throws Exception {
    assertThat(qux).isNull();
    setupComponent();
    assertThat(qux).isNotNull();
  }

  @Test
  public void testLocalEntryPoint() throws Exception {
    setupComponent();
    Bar bar = EntryPoints.get(getApplicationContext(), BarEntryPoint.class).getBar();
    assertThat(bar).isNotNull();
    assertThat(bar.value).isEqualTo(STR_VALUE);
  }

  @Test
  public void testLocalPkgPrivateEntryPoint() throws Exception {
    setupComponent();
    Qux qux = EntryPoints.get(getApplicationContext(), PkgPrivateQuxEntryPoint.class).getQux();
    assertThat(qux).isNotNull();
  }

  @Test
  public void testAndroidEntryPoint() throws Exception {
    setupComponent();
    ActivityController<TestActivity> ac = Robolectric.buildActivity(TestActivity.class);
    assertThat(ac.get().baz).isNull();
    ac.create();
    assertThat(ac.get().baz).isNotNull();
    assertThat(ac.get().baz.value).isEqualTo(LONG_VALUE);
  }

  @Test
  public void testMissingMultiTestRoot2EntryPoint() throws Exception {
    setupComponent();
    ClassCastException exception =
        assertThrows(
            ClassCastException.class,
            () -> EntryPoints.get(getApplicationContext(), MultiTestRoot2Test.BarEntryPoint.class));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "Cannot cast dagger.hilt.android.internal.testing.root."
                + "DaggerMultiTestRoot1Test_HiltComponents_SingletonC$SingletonCImpl"
                + " to dagger.hilt.android.MultiTestRoot2Test$BarEntryPoint");
  }

  @Test
  public void testBindValueFieldIsProvided() throws Exception {
    setupComponent();
    assertThat(bindValueString).isEqualTo(BIND_VALUE_STRING);
    assertThat(getBinding()).isEqualTo(BIND_VALUE_STRING);
  }

  @Test
  public void testBindValueIsMutable() throws Exception {
    setupComponent();
    bindValueString = "newValue";
    assertThat(getBinding()).isEqualTo("newValue");
  }

  void setupComponent() {
    rule.inject();
  }

  private static String getBinding() {
    return EntryPoints.get(getApplicationContext(), BindValueEntryPoint.class).bindValueString();
  }
}

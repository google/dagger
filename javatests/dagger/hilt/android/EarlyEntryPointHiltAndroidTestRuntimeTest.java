/*
 * Copyright (C) 2021 The Dagger Authors.
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
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.hilt.EntryPoint;
import dagger.hilt.EntryPoints;
import dagger.hilt.InstallIn;
import dagger.hilt.android.EarlyEntryPointHiltAndroidTestRuntimeClasses.BarEntryPoint;
import dagger.hilt.android.EarlyEntryPointHiltAndroidTestRuntimeClasses.EarlyFooEntryPoint;
import dagger.hilt.android.EarlyEntryPointHiltAndroidTestRuntimeClasses.EarlyMySubcomponentBuilderEntryPoint;
import dagger.hilt.android.EarlyEntryPointHiltAndroidTestRuntimeClasses.MySubcomponent;
import dagger.hilt.android.EarlyEntryPointHiltAndroidTestRuntimeClasses.MySubcomponentBuilderEntryPoint;
import dagger.hilt.android.EarlyEntryPointHiltAndroidTestRuntimeClasses.MySubcomponentScoped;
import dagger.hilt.android.internal.testing.TestApplicationComponentManager;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.components.SingletonComponent;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
// Robolectric requires Java9 to run API 29 and above, so use API 28 instead
@Config(sdk = Build.VERSION_CODES.P, application = HiltTestApplication.class)
public final class EarlyEntryPointHiltAndroidTestRuntimeTest {
  @EntryPoint
  @InstallIn(SingletonComponent.class)
  interface FooEntryPoint {
    Foo foo();
  }

  @Singleton
  public static class Foo {
    @Inject
    Foo() {}
  }

  @MySubcomponentScoped
  public static class Bar {
    final Foo foo;
    final int id;

    @Inject
    Bar(Foo foo, int id) {
      this.foo = foo;
      this.id = id;
    }
  }

  @Rule public HiltAndroidRule rule = new HiltAndroidRule(this);

  @Test
  public void testEarlyEntryPointsWrongEntryPointFails() throws Exception {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> EarlyEntryPoints.get(getApplicationContext(), FooEntryPoint.class));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "FooEntryPoint should be called with EntryPoints.get() rather than "
                + "EarlyEntryPoints.get()");
  }

  @Test
  public void testEntryPointsWrongEntryPointFails() throws Exception {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> EntryPoints.get(getApplicationContext(), EarlyFooEntryPoint.class));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Interface, dagger.hilt.android.EarlyEntryPointHiltAndroidTestRuntimeClasses."
                + "EarlyFooEntryPoint, annotated with @EarlyEntryPoint should be called with "
                + "EarlyEntryPoints.get() rather than EntryPoints.get()");
  }

  @Test
  public void testComponentInstances() {
    Object componentManager = ((HiltTestApplication) getApplicationContext()).componentManager();
    Object component1 = ((TestApplicationComponentManager) componentManager).generatedComponent();
    Object component2 = ((TestApplicationComponentManager) componentManager).generatedComponent();
    assertThat(component1).isNotNull();
    assertThat(component2).isNotNull();
    assertThat(component1).isEqualTo(component2);

    Object earlyComponent1 =
        ((TestApplicationComponentManager) componentManager).earlySingletonComponent();
    Object earlyComponent2 =
        ((TestApplicationComponentManager) componentManager).earlySingletonComponent();
    assertThat(earlyComponent1).isNotNull();
    assertThat(earlyComponent2).isNotNull();
    assertThat(earlyComponent1).isEqualTo(earlyComponent2);
    assertThat(earlyComponent1).isNotEqualTo(component1);
  }

  // Test that the early entry point returns a different @Singleton binding instance.
  @Test
  public void testScopedEntryPointValues() {
    Foo foo1 = EntryPoints.get(getApplicationContext(), FooEntryPoint.class).foo();
    Foo foo2 = EntryPoints.get(getApplicationContext(), FooEntryPoint.class).foo();
    Foo earlyFoo1 =
        EarlyEntryPoints.get(getApplicationContext(), EarlyFooEntryPoint.class).foo();
    Foo earlyFoo2 =
        EarlyEntryPoints.get(getApplicationContext(), EarlyFooEntryPoint.class).foo();

    assertThat(foo1).isNotNull();
    assertThat(foo2).isNotNull();
    assertThat(earlyFoo1).isNotNull();
    assertThat(earlyFoo2).isNotNull();

    assertThat(foo1).isEqualTo(foo2);
    assertThat(earlyFoo1).isEqualTo(earlyFoo2);
    assertThat(earlyFoo1).isNotEqualTo(foo1);
  }

  // Test that the a subcomponent of the early component does not need to use EarlyEntryPoints.
  @Test
  public void testSubcomponentEntryPoints() {
    MySubcomponent subcomponent =
        EntryPoints.get(getApplicationContext(), MySubcomponentBuilderEntryPoint.class)
            .mySubcomponentBuilder()
            .id(5)
            .build();

    MySubcomponent earlySubcomponent =
        EarlyEntryPoints.get(
                getApplicationContext(), EarlyMySubcomponentBuilderEntryPoint.class)
            .mySubcomponentBuilder()
            .id(11)
            .build();

    assertThat(subcomponent).isNotNull();
    assertThat(earlySubcomponent).isNotNull();
    assertThat(subcomponent).isNotEqualTo(earlySubcomponent);

    // Test that subcomponents can use EntryPoints
    Bar bar1 = EntryPoints.get(subcomponent, BarEntryPoint.class).bar();
    Bar bar2 = EntryPoints.get(subcomponent, BarEntryPoint.class).bar();

    // Test that early subcomponents can use EntryPoints or EarlyEntryPoints
    Bar earlyBar1 = EntryPoints.get(earlySubcomponent, BarEntryPoint.class).bar();
    Bar earlyBar2 = EntryPoints.get(earlySubcomponent, BarEntryPoint.class).bar();

    assertThat(bar1).isNotNull();
    assertThat(bar2).isNotNull();
    assertThat(earlyBar1).isNotNull();
    assertThat(earlyBar2).isNotNull();
    assertThat(bar1).isEqualTo(bar2);
    assertThat(earlyBar1).isEqualTo(earlyBar2);
    assertThat(bar1).isNotEqualTo(earlyBar1);
  }
}

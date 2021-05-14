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

import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.EntryPoints;
import dagger.hilt.InstallIn;
import dagger.hilt.android.UsesComponentTestClasses.Foo;
import dagger.hilt.android.UsesComponentTestClasses.FooEntryPoint;
import dagger.hilt.android.UsesComponentTestClasses.UsesComponentQualifier;
import dagger.hilt.android.UsesComponentTestClasses.UsesComponentTestSubcomponent;
import dagger.hilt.android.UsesComponentTestClasses.UsesComponentTestSubcomponentBuilderEntryPoint;
import dagger.hilt.android.internal.testing.TestApplicationComponentManager;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.UninstallModules;
import dagger.hilt.components.SingletonComponent;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
// Robolectric requires Java9 to run API 29 and above, so use API 28 instead
@Config(sdk = Build.VERSION_CODES.P, application = HiltTestApplication.class)
@UninstallModules(UsesComponentTestModule.class)
public final class UsesLocalComponentUninstallModuleTest {

  @Rule public HiltAndroidRule rule = new HiltAndroidRule(this);

  @Inject @UsesComponentQualifier String injectedString;

  @Module
  @InstallIn(SingletonComponent.class)
  public interface LocalTestModule {

    @Provides
    @UsesComponentQualifier
    static String provideString() {
      return "local_string";
    }
  }

  @Test
  public void testInject() {
    rule.inject();
    assertThat(injectedString).isEqualTo("local_string");
  }

  @Test
  public void testSubcomponent() {
    UsesComponentTestSubcomponent subcomponent =
        EntryPoints.get(
                getApplicationContext(), UsesComponentTestSubcomponentBuilderEntryPoint.class)
            .mySubcomponentBuilder()
            .id(123)
            .build();

    Foo foo1 = EntryPoints.get(subcomponent, FooEntryPoint.class).foo();
    Foo foo2 = EntryPoints.get(subcomponent, FooEntryPoint.class).foo();

    assertThat(foo1).isNotNull();
    assertThat(foo2).isNotNull();
    assertThat(foo1).isSameInstanceAs(foo2);
  }

  @Test
  public void testUsesLocalComponent() {
    HiltTestApplication app = (HiltTestApplication) getApplicationContext();
    Object generatedComponent =
        ((TestApplicationComponentManager) app.componentManager()).generatedComponent();
    assertThat(generatedComponent.getClass().getName())
        .isEqualTo(UsesComponentHelper.perTestComponentName(this));
  }
}

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

import android.app.IntentService;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import android.widget.LinearLayout;
import androidx.lifecycle.ViewModel;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.migration.OptionalInject;
import dagger.hilt.components.SingletonComponent;
import javax.inject.Inject;
import javax.inject.Qualifier;

/** Test classes for optional injection. */
public final class OptionalInjectTestClasses {
  public static final String APP_BINDING = "app binding";
  public static final String ACTIVITY_BINDING = "activity binding";
  public static final String FRAGMENT_BINDING = "fragment binding";
  public static final String VIEW_BINDING = "view binding";

  @Qualifier
  @interface ActivityLevel {}

  @Qualifier
  @interface FragmentLevel {}

  @Qualifier
  @interface ViewLevel {}

  @AndroidEntryPoint(FragmentActivity.class)
  @OptionalInject
  public static class TestActivity extends Hilt_OptionalInjectTestClasses_TestActivity {
    @Inject @ActivityLevel String testActivityBinding;
  }

  @AndroidEntryPoint(TestActivity.class)
  public static final class NonOptionalSubclassActivity
      extends Hilt_OptionalInjectTestClasses_NonOptionalSubclassActivity {
    @Inject @ActivityLevel String testActivitySubclassBinding;
  }

  @AndroidEntryPoint(TestActivity.class)
  @OptionalInject
  public static final class OptionalSubclassActivity
      extends Hilt_OptionalInjectTestClasses_OptionalSubclassActivity {
    @Inject @ActivityLevel String testActivitySubclassBinding;
  }

  @AndroidEntryPoint(Fragment.class)
  @OptionalInject
  public static final class TestFragment extends Hilt_OptionalInjectTestClasses_TestFragment {
    @Inject @FragmentLevel String testFragmentBinding;
  }

  @AndroidEntryPoint(LinearLayout.class)
  @OptionalInject
  public static final class TestView extends Hilt_OptionalInjectTestClasses_TestView {
    @Inject @ViewLevel String testViewBinding;

    TestView(Context context) {
      super(context);
    }
  }

  @WithFragmentBindings
  @AndroidEntryPoint(LinearLayout.class)
  @OptionalInject
  public static final class TestWithFragmentBindingsView
      extends Hilt_OptionalInjectTestClasses_TestWithFragmentBindingsView {
    @Inject @ViewLevel String testViewBinding;

    TestWithFragmentBindingsView(Context context) {
      super(context);
    }
  }

  @HiltViewModel
  public static final class TestViewModel extends ViewModel {
    final String appBinding;

    @Inject TestViewModel(String appBinding) {
      this.appBinding = appBinding;
    }
  }

  public static final class NonHiltViewModel extends ViewModel {}

  @AndroidEntryPoint(Service.class)
  @OptionalInject
  public static final class TestService extends Hilt_OptionalInjectTestClasses_TestService {
    @Inject String testAppBinding;

    @Override
    public IBinder onBind(Intent intent) {
      return null;
    }
  }

  @AndroidEntryPoint(IntentService.class)
  @OptionalInject
  public static final class TestIntentService
      extends Hilt_OptionalInjectTestClasses_TestIntentService {
    TestIntentService() {
      super("TestIntentService");
    }

    @Inject String testAppBinding;

    @Override
    public void onHandleIntent(Intent intent) {}
  }

  @AndroidEntryPoint(BroadcastReceiver.class)
  @OptionalInject
  public static final class TestBroadcastReceiver
      extends Hilt_OptionalInjectTestClasses_TestBroadcastReceiver {
    @Inject String testAppBinding;
  }

  @Module
  @InstallIn(SingletonComponent.class)
  static final class AppModule {
    @Provides
    static String provideAppString() {
      return APP_BINDING;
    }

    @Provides
    @ActivityLevel
    static String provideActivityString() {
      return ACTIVITY_BINDING;
    }

    @Provides
    @FragmentLevel
    static String provideFragmentString() {
      return FRAGMENT_BINDING;
    }

    @Provides
    @ViewLevel
    static String provideViewString() {
      return VIEW_BINDING;
    }
  }
}

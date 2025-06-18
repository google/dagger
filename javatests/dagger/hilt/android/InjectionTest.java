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
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.Assert.fail;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.IntentService;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ActivityComponent;
import dagger.hilt.android.components.FragmentComponent;
import dagger.hilt.android.components.ServiceComponent;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.components.SingletonComponent;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Qualifier;
import javax.inject.Singleton;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
// Robolectric requires Java9 to run API 29 and above, so use API 28 instead
@Config(sdk = Build.VERSION_CODES.P, application = HiltTestApplication.class)
public final class InjectionTest {
  private static final String APP_BINDING = "APP_BINDING";
  private static final String ACTIVITY_BINDING = "ACTIVIY_BINDING";
  private static final String FRAGMENT_BINDING = "FRAGMENT_BINDING";
  private static final String SERVICE_BINDING = "SERVICE_BINDING";

  @Retention(RUNTIME)
  @Qualifier
  @Target({ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD})
  @interface ApplicationLevel {}

  @Retention(RUNTIME)
  @Qualifier
  @Target({ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD})
  @interface ActivityLevel {}

  @Retention(RUNTIME)
  @Qualifier
  @Target({ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD})
  @interface FragmentLevel {}

  @Retention(RUNTIME)
  @Qualifier
  @Target({ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD})
  @interface ServiceLevel {}

  /** Application level bindings */
  @Module
  @InstallIn(SingletonComponent.class)
  static final class AppModule {
    @Provides
    @ApplicationLevel
    static String providesAppBinding() {
      return APP_BINDING;
    }

    @Provides
    @Singleton
    static AtomicLong provideCounter() {
      return new AtomicLong();
    }

    @Provides
    static Long provideCount(AtomicLong counter) {
      return counter.incrementAndGet();
    }
  }

  /** Activity level bindings */
  @Module
  @InstallIn(ActivityComponent.class)
  static final class ActivityModule {
    @Provides
    @ActivityLevel
    static String providesActivityBinding() {
      return ACTIVITY_BINDING;
    }
  }

  /** Fragment level bindings */
  @Module
  @InstallIn(FragmentComponent.class)
  static final class FragmentModule {
    @Provides
    @FragmentLevel
    static String providesFragmentBinding() {
      return FRAGMENT_BINDING;
    }
  }

  /** Service level bindings */
  @Module
  @InstallIn(ServiceComponent.class)
  static final class ServiceModule {
    @Provides
    @ServiceLevel
    static String providesServiceBinding() {
      return SERVICE_BINDING;
    }
  }

  /** Hilt Activity */
  @AndroidEntryPoint(FragmentActivity.class)
  public static final class TestActivity extends Hilt_InjectionTest_TestActivity {
    @Inject @ApplicationLevel String appBinding;
    @Inject @ActivityLevel String activityBinding;
    boolean onCreateCalled;

    @Override
    public void onCreate(Bundle onSavedInstanceState) {
      assertThat(appBinding).isNull();
      assertThat(activityBinding).isNull();

      super.onCreate(onSavedInstanceState);

      assertThat(appBinding).isEqualTo(APP_BINDING);
      assertThat(activityBinding).isEqualTo(ACTIVITY_BINDING);

      onCreateCalled = true;
    }
  }

  /** Non-Hilt Activity */
  public static final class NonHiltActivity extends FragmentActivity {}

  /** Hilt Fragment */
  @AndroidEntryPoint(Fragment.class)
  public static final class TestFragment extends Hilt_InjectionTest_TestFragment {
    @Inject @ApplicationLevel String appBinding;
    @Inject @ActivityLevel String activityBinding;
    @Inject @FragmentLevel String fragmentBinding;
    boolean onAttachContextCalled;
    boolean onAttachActivityCalled;

    @Override
    public void onAttach(Context context) {
      preInjectionAssert();
      super.onAttach(context);
      postInjectionAssert();
      onAttachContextCalled = true;
    }

    @Override
    public void onAttach(Activity activity) {
      preInjectionAssert();
      super.onAttach(activity);
      postInjectionAssert();
      onAttachActivityCalled = true;
    }

    private void preInjectionAssert() {
      assertThat(appBinding).isNull();
      assertThat(activityBinding).isNull();
      assertThat(fragmentBinding).isNull();
    }

    private void postInjectionAssert() {
      assertThat(appBinding).isEqualTo(APP_BINDING);
      assertThat(activityBinding).isEqualTo(ACTIVITY_BINDING);
      assertThat(fragmentBinding).isEqualTo(FRAGMENT_BINDING);
    }
  }

  /** Non-Hilt Fragment */
  public static final class NonHiltFragment extends Fragment {}

  /** Hilt extends parameterized fragment. */
  @AndroidEntryPoint(ParameterizedFragment.class)
  public static final class TestParameterizedFragment
      extends Hilt_InjectionTest_TestParameterizedFragment<Integer> {
    @Inject @ApplicationLevel String appBinding;
    @Inject @ActivityLevel String activityBinding;
    @Inject @FragmentLevel String fragmentBinding;
    boolean onAttachContextCalled;
    boolean onAttachActivityCalled;

    @Override
    public void onAttach(Context context) {
      preInjectionAssert();
      super.onAttach(context);
      postInjectionAssert();
      onAttachContextCalled = true;
    }

    @Override
    public void onAttach(Activity activity) {
      preInjectionAssert();
      super.onAttach(activity);
      postInjectionAssert();
      onAttachActivityCalled = true;
    }

    private void preInjectionAssert() {
      assertThat(appBinding).isNull();
      assertThat(activityBinding).isNull();
      assertThat(fragmentBinding).isNull();
    }

    private void postInjectionAssert() {
      assertThat(appBinding).isEqualTo(APP_BINDING);
      assertThat(activityBinding).isEqualTo(ACTIVITY_BINDING);
      assertThat(fragmentBinding).isEqualTo(FRAGMENT_BINDING);
    }
  }

  /** Non-Hilt parameterized fragment */
  public static class ParameterizedFragment<T> extends Fragment {}

  /** Hilt View */
  @AndroidEntryPoint(LinearLayout.class)
  public static final class TestView extends Hilt_InjectionTest_TestView {
    @Inject @ApplicationLevel String appBinding;
    @Inject @ActivityLevel String activityBinding;

    TestView(Context context) {
      super(context);
    }

    TestView(Context context, AttributeSet attrs) {
      super(context, attrs);
    }

    TestView(Context context, AttributeSet attrs, int defStyleAttr) {
      super(context, attrs, defStyleAttr);
    }

    @TargetApi(21)
    TestView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
      super(context, attrs, defStyleAttr, defStyleRes);
    }
  }

  /** Hilt View (With Fragment bindings) */
  @WithFragmentBindings
  @AndroidEntryPoint(LinearLayout.class)
  public static final class TestViewWithFragmentBindings
      extends Hilt_InjectionTest_TestViewWithFragmentBindings {
    @Inject @ApplicationLevel String appBinding;
    @Inject @ActivityLevel String activityBinding;
    @Inject @FragmentLevel String fragmentBinding;

    TestViewWithFragmentBindings(Context context) {
      super(context);
    }
  }

  @AndroidEntryPoint(Service.class)
  public static final class TestService extends Hilt_InjectionTest_TestService {
    @Inject @ApplicationLevel String appBinding;
    @Inject @ServiceLevel String serviceBinding;

    @Override
    public IBinder onBind(Intent intent) {
      return null;
    }
  }

  @AndroidEntryPoint(IntentService.class)
  public static final class TestIntentService extends Hilt_InjectionTest_TestIntentService {
    private static final String NAME = "TestIntentServiceName";
    @Inject @ApplicationLevel String appBinding;
    @Inject @ServiceLevel String serviceBinding;

    TestIntentService() {
      super(NAME);
    }

    @Override
    public void onHandleIntent(Intent intent) {}
  }

  @AndroidEntryPoint(BroadcastReceiver.class)
  public static final class TestBroadcastReceiver extends Hilt_InjectionTest_TestBroadcastReceiver {
    @Inject @ApplicationLevel String appBinding;
    Intent lastIntent = null;

    @Override
    public void onReceive(Context context, Intent intent) {
      super.onReceive(context, intent);
      lastIntent = intent;
    }
  }

  @AndroidEntryPoint(BaseBroadcastReceiver.class)
  public static final class TestBroadcastReceiverWithBaseImplementingOnReceive
      extends Hilt_InjectionTest_TestBroadcastReceiverWithBaseImplementingOnReceive {
    @Inject @ApplicationLevel String appBinding;
    Intent baseLastIntent = null;

    @Override
    public void onReceive(Context context, Intent intent) {
      super.onReceive(context, intent);
      baseLastIntent = intent;
    }
  }

  abstract static class BaseBroadcastReceiver extends BroadcastReceiver {
    Intent lastIntent = null;

    @Override
    public void onReceive(Context context, Intent intent) {
      lastIntent = intent;
    }
  }

  @Rule public final HiltAndroidRule rule = new HiltAndroidRule(this);

  @Inject @ApplicationLevel String appBinding;

  @Before
  public void setup() {
    rule.inject();
  }

  @Test
  public void testAppInjection() throws Exception {
    assertThat(appBinding).isEqualTo(APP_BINDING);
  }

  @Test
  public void testActivityInjection() throws Exception {
    ActivityController<TestActivity> controller = Robolectric.buildActivity(TestActivity.class);

    assertThat(controller.get().onCreateCalled).isFalse();
    controller.create();
    assertThat(controller.get().onCreateCalled).isTrue();
  }

  @Test
  public void testFragmentInjection() throws Exception {
    TestFragment fragment = new TestFragment();
    assertThat(fragment.onAttachContextCalled).isFalse();
    assertThat(fragment.onAttachActivityCalled).isFalse();
    setupFragment(TestActivity.class, fragment);
    assertThat(fragment.onAttachContextCalled).isTrue();
    assertThat(fragment.onAttachActivityCalled).isTrue();
  }

  @Test
  public void testParameterizedFragmentInjection() throws Exception {
    TestParameterizedFragment fragment = new TestParameterizedFragment();
    assertThat(fragment.onAttachContextCalled).isFalse();
    assertThat(fragment.onAttachActivityCalled).isFalse();
    setupFragment(TestActivity.class, fragment);
    assertThat(fragment.onAttachContextCalled).isTrue();
    assertThat(fragment.onAttachActivityCalled).isTrue();
  }

  @Test
  public void testViewNoFragmentBindingsWithActivity() throws Exception {
    TestActivity activity = Robolectric.setupActivity(TestActivity.class);
    TestView view = new TestView(activity);
    assertThat(view.appBinding).isEqualTo(APP_BINDING);
    assertThat(view.activityBinding).isEqualTo(ACTIVITY_BINDING);
  }

  @Test
  public void testViewNoFragmentBindingsWithFragment() throws Exception {
    TestFragment fragment = setupFragment(TestActivity.class, new TestFragment());
    TestView view = new TestView(fragment.getContext());
    assertThat(view.appBinding).isEqualTo(APP_BINDING);
    assertThat(view.activityBinding).isEqualTo(ACTIVITY_BINDING);
  }

  @Test
  public void testViewNoFragmentBindingsWithFragment_secondConstructor() throws Exception {
    TestFragment fragment = setupFragment(TestActivity.class, new TestFragment());
    TestView view = new TestView(fragment.getContext(), /* attrs= */ null);
    assertThat(view.appBinding).isEqualTo(APP_BINDING);
    assertThat(view.activityBinding).isEqualTo(ACTIVITY_BINDING);
  }

  @Test
  public void testViewNoFragmentBindingsWithFragment_thirdConstructor() throws Exception {
    TestFragment fragment = setupFragment(TestActivity.class, new TestFragment());
    TestView view = new TestView(fragment.getContext(), /* attrs= */ null, /* defStyleAttr= */ 0);
    assertThat(view.appBinding).isEqualTo(APP_BINDING);
    assertThat(view.activityBinding).isEqualTo(ACTIVITY_BINDING);
  }

  @Test
  @Config(sdk = Config.OLDEST_SDK)
  public void testViewNoFragmentBindingsWithFragment_fourthConstructor_presentOnTwentyOne()
      throws Exception {
    TestFragment fragment = setupFragment(TestActivity.class, new TestFragment());
    TestView view =
        new TestView(
            fragment.getContext(), /* attrs= */ null, /* defStyleAttr= */ 0, /* defStyleRes= */ 0);
    assertThat(view.appBinding).isEqualTo(APP_BINDING);
    assertThat(view.activityBinding).isEqualTo(ACTIVITY_BINDING);
  }

  @Test
  public void testServiceInjection() throws Exception {
    TestService testService = Robolectric.setupService(TestService.class);
    assertThat(testService.appBinding).isEqualTo(APP_BINDING);
    assertThat(testService.serviceBinding).isEqualTo(SERVICE_BINDING);
  }

  @Test
  public void testIntentServiceInjection() throws Exception {
    TestIntentService testIntentService = Robolectric.setupService(TestIntentService.class);
    assertThat(testIntentService.appBinding).isEqualTo(APP_BINDING);
    assertThat(testIntentService.serviceBinding).isEqualTo(SERVICE_BINDING);
  }

  @Test
  public void testBroadcastReceiverInjection() throws Exception {
    TestBroadcastReceiver testBroadcastReceiver = new TestBroadcastReceiver();
    Intent intent = new Intent();
    testBroadcastReceiver.onReceive(getApplicationContext(), intent);
    assertThat(testBroadcastReceiver.appBinding).isEqualTo(APP_BINDING);
    assertThat(testBroadcastReceiver.lastIntent).isSameInstanceAs(intent);
  }

  @Test
  public void testBroadcastReceiverWithBaseImplementingOnReceiveInjection() throws Exception {
    TestBroadcastReceiverWithBaseImplementingOnReceive testBroadcastReceiver =
        new TestBroadcastReceiverWithBaseImplementingOnReceive();
    Intent intent = new Intent();
    testBroadcastReceiver.onReceive(getApplicationContext(), intent);
    assertThat(testBroadcastReceiver.appBinding).isEqualTo(APP_BINDING);
    assertThat(testBroadcastReceiver.lastIntent).isSameInstanceAs(intent);
    assertThat(testBroadcastReceiver.baseLastIntent).isSameInstanceAs(intent);
  }

  @Test
  public void testViewWithFragmentBindingsWithFragment() throws Exception {
    TestFragment fragment = setupFragment(TestActivity.class, new TestFragment());

    Context fragmentContext = fragment.getContext();
    TestViewWithFragmentBindings view = new TestViewWithFragmentBindings(fragmentContext);
    assertThat(view.appBinding).isEqualTo(APP_BINDING);
    assertThat(view.activityBinding).isEqualTo(ACTIVITY_BINDING);
    assertThat(view.fragmentBinding).isEqualTo(FRAGMENT_BINDING);
  }

  @Test
  public void testViewWithFragmentBindingsFailsWithActivity() throws Exception {
    TestActivity activity = Robolectric.setupActivity(TestActivity.class);
    try {
      new TestViewWithFragmentBindings(activity);
      fail("Expected test to fail but it passes!");
    } catch (IllegalStateException e) {
      assertThat(e)
          .hasMessageThat()
          .contains(
              "@WithFragmentBindings Hilt view must be attached to an @AndroidEntryPoint Fragment");
    }
  }

  @Test
  public void testFragmentAttachedToNonHiltActivityFails() throws Exception {
    NonHiltActivity activity = Robolectric.setupActivity(NonHiltActivity.class);
    try {
      activity
          .getSupportFragmentManager()
          .beginTransaction()
          .add(new TestFragment(), null)
          .commitNow();
      fail("Expected test to fail but it passes!");
    } catch (IllegalStateException e) {
      assertThat(e)
          .hasMessageThat()
          .contains("Hilt Fragments must be attached to an @AndroidEntryPoint Activity");
    }
  }

  @Test
  public void testViewAttachedToNonHiltActivityFails() throws Exception {
    NonHiltActivity activity = Robolectric.setupActivity(NonHiltActivity.class);
    try {
      new TestView(activity);
      fail("Expected test to fail but it passes!");
    } catch (IllegalStateException e) {
      assertThat(e)
          .hasMessageThat()
          .contains("Hilt view must be attached to an @AndroidEntryPoint Fragment or Activity");
    }
  }

  @Test
  public void testViewAttachedToNonHiltFragmentFails() throws Exception {
    NonHiltActivity activity = Robolectric.setupActivity(NonHiltActivity.class);
    NonHiltFragment fragment = new NonHiltFragment();
    activity.getSupportFragmentManager().beginTransaction().add(fragment, null).commitNow();
    Context nonHiltContext = fragment.getContext();
    try {
      new TestView(nonHiltContext);
      fail("Expected test to fail but it passes!");
    } catch (IllegalStateException e) {
      assertThat(e)
          .hasMessageThat()
          .contains("Hilt view must be attached to an @AndroidEntryPoint Fragment or Activity");
    }
  }

  @Test
  public void testViewAttachedToApplicationContextFails() throws Exception {
    try {
      new TestView(getApplicationContext());
      fail("Expected test to fail but it passes!");
    } catch (IllegalStateException e) {
      assertThat(e)
          .hasMessageThat()
          .contains(
              "Hilt view cannot be created using the application context. "
                  + "Use a Hilt Fragment or Activity context");
    }
  }

  /** Hilt Activity that manually calls inject(). */
  @AndroidEntryPoint(FragmentActivity.class)
  public static final class DoubleInjectActivity extends Hilt_InjectionTest_DoubleInjectActivity {
    @Inject Long counter;

    @Override
    public void onCreate(Bundle onSavedInstanceState) {
      inject();
      super.onCreate(onSavedInstanceState);
    }
  }

  @Test
  public void testActivityDoesNotInjectTwice() throws Exception {
    ActivityController<DoubleInjectActivity> controller =
        Robolectric.buildActivity(DoubleInjectActivity.class);
    controller.create();
    assertThat(controller.get().counter).isEqualTo(1L);
  }

  /** Hilt Fragment that manually calls inject(). */
  @AndroidEntryPoint(Fragment.class)
  public static final class DoubleInjectFragment extends Hilt_InjectionTest_DoubleInjectFragment {
    @Inject Long counter;

    @Override
    public void onAttach(Context context) {
      inject();
      super.onAttach(context);
    }

    @Override
    public void onAttach(Activity activity) {
      inject();
      super.onAttach(activity);
    }
  }

  @Test
  public void testFragmentDoesNotInjectTwice() throws Exception {
    DoubleInjectFragment fragment = setupFragment(TestActivity.class, new DoubleInjectFragment());
    assertThat(fragment.counter).isEqualTo(1L);
  }

  /** Hilt View that manually calls inject(). */
  @AndroidEntryPoint(LinearLayout.class)
  public static final class DoubleInjectView extends Hilt_InjectionTest_DoubleInjectView {
    @Inject Long counter;

    DoubleInjectView(Context context) {
      super(context);
      inject();
    }

    DoubleInjectView(Context context, AttributeSet attrs) {
      super(context, attrs);
      inject();
    }

    DoubleInjectView(Context context, AttributeSet attrs, int defStyleAttr) {
      super(context, attrs, defStyleAttr);
      inject();
    }

    @TargetApi(21)
    DoubleInjectView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
      super(context, attrs, defStyleAttr, defStyleRes);
      inject();
    }
  }

  @Test
  public void testViewDoesNotInjectTwice() throws Exception {
    TestActivity activity = Robolectric.setupActivity(TestActivity.class);
    DoubleInjectView view = new DoubleInjectView(activity);
    assertThat(view.counter).isEqualTo(1L);
  }

  /** Hilt Service that manually calls inject(). */
  @AndroidEntryPoint(Service.class)
  public static final class DoubleInjectService extends Hilt_InjectionTest_DoubleInjectService {
    @Inject Long counter;

    @Override public void onCreate() {
      inject();
      super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
      return null;
    }
  }

  @Test
  public void testServiceDoesNotInjectTwice() throws Exception {
    DoubleInjectService testService = Robolectric.setupService(DoubleInjectService.class);
    assertThat(testService.counter).isEqualTo(1L);
  }

  /** Hilt BroadcastReceiver that manually calls inject(). */
  @AndroidEntryPoint(BroadcastReceiver.class)
  public static final class DoubleInjectBroadcastReceiver
      extends Hilt_InjectionTest_DoubleInjectBroadcastReceiver {
    @Inject Long counter;

    @Override
    public void onReceive(Context context, Intent intent) {
      inject(context);
      super.onReceive(context, intent);
    }
  }

  @Test
  public void testBroadcastReceiverDoesNotInjectTwice() throws Exception {
    DoubleInjectBroadcastReceiver testBroadcastReceiver = new DoubleInjectBroadcastReceiver();
    Intent intent = new Intent();
    testBroadcastReceiver.onReceive(getApplicationContext(), intent);
    assertThat(testBroadcastReceiver.counter).isEqualTo(1L);
  }

  private static <T extends Fragment> T setupFragment(
      Class<? extends FragmentActivity> activityClass, T fragment) {
    FragmentActivity activity = Robolectric.setupActivity(activityClass);
    attachFragment(activity, fragment);
    return fragment;
  }

  private static void attachFragment(FragmentActivity activity, Fragment fragment) {
    activity.getSupportFragmentManager().beginTransaction().add(fragment, "").commitNow();
  }
}

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

package dagger.android.support.functional;

import android.app.Activity;
import android.app.Application;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.support.v4.app.Fragment;
import dagger.Binds;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.Subcomponent;
import dagger.android.ActivityKey;
import dagger.android.AndroidInjector;
import dagger.android.BroadcastReceiverKey;
import dagger.android.DispatchingAndroidInjector;
import dagger.android.HasDispatchingActivityInjector;
import dagger.android.HasDispatchingBroadcastReceiverInjector;
import dagger.android.HasDispatchingServiceInjector;
import dagger.android.ServiceKey;
import dagger.android.support.AndroidSupportInjectionModule;
import dagger.android.support.FragmentKey;
import dagger.android.support.functional.AllControllersAreDirectChildrenOfApplication.ApplicationComponent.BroadcastReceiverSubcomponent.BroadcastReceiverModule;
import dagger.android.support.functional.AllControllersAreDirectChildrenOfApplication.ApplicationComponent.IntentServiceSubcomponent.IntentServiceModule;
import dagger.android.support.functional.AllControllersAreDirectChildrenOfApplication.ApplicationComponent.ServiceSubcomponent.ServiceModule;
import dagger.multibindings.IntoMap;
import dagger.multibindings.IntoSet;
import javax.inject.Inject;

public final class AllControllersAreDirectChildrenOfApplication extends Application
    implements HasDispatchingActivityInjector,
        HasDispatchingServiceInjector,
        HasDispatchingBroadcastReceiverInjector {
  @Inject DispatchingAndroidInjector<Activity> activityInjector;
  @Inject DispatchingAndroidInjector<Service> serviceInjector;
  @Inject DispatchingAndroidInjector<BroadcastReceiver> broadcastReceiverInjector;

  @Override
  public void onCreate() {
    super.onCreate();
    DaggerAllControllersAreDirectChildrenOfApplication_ApplicationComponent.create().inject(this);
  }

  @Override
  public DispatchingAndroidInjector<Activity> activityInjector() {
    return activityInjector;
  }

  @Override
  public DispatchingAndroidInjector<Service> serviceInjector() {
    return serviceInjector;
  }

  @Override
  public DispatchingAndroidInjector<BroadcastReceiver> broadcastReceiverInjector() {
    return broadcastReceiverInjector;
  }

  @Component(
    modules = {ApplicationComponent.ApplicationModule.class, AndroidSupportInjectionModule.class}
  )
  interface ApplicationComponent {
    void inject(AllControllersAreDirectChildrenOfApplication application);

    @Module(
      subcomponents = {
        ActivitySubcomponent.class,
        ParentFragmentSubcomponent.class,
        ChildFragmentSubcomponent.class,
        ServiceSubcomponent.class,
        IntentServiceSubcomponent.class,
        BroadcastReceiverSubcomponent.class,
      }
    )
    abstract class ApplicationModule {
      @Provides
      @IntoSet
      static Class<?> addToComponentHierarchy() {
        return ApplicationComponent.class;
      }

      @Binds
      @IntoMap
      @ActivityKey(TestActivity.class)
      abstract AndroidInjector.Factory<? extends Activity> bindFactoryForTestActivity(
          ActivitySubcomponent.Builder builder);

      @Binds
      @IntoMap
      @FragmentKey(TestParentFragment.class)
      abstract AndroidInjector.Factory<? extends Fragment> bindFactoryForParentFragment(
          ParentFragmentSubcomponent.Builder builder);

      @Binds
      @IntoMap
      @FragmentKey(TestChildFragment.class)
      abstract AndroidInjector.Factory<? extends Fragment> bindFactoryForChildFragment(
          ChildFragmentSubcomponent.Builder builder);

      @Binds
      @IntoMap
      @ServiceKey(TestService.class)
      abstract AndroidInjector.Factory<? extends Service> bindFactoryForService(
          ServiceSubcomponent.Builder b);

      @Binds
      @IntoMap
      @ServiceKey(TestIntentService.class)
      abstract AndroidInjector.Factory<? extends Service> bindFactoryForIntentService(
          IntentServiceSubcomponent.Builder b);

      @Binds
      @IntoMap
      @BroadcastReceiverKey(TestBroadcastReceiver.class)
      abstract AndroidInjector.Factory<? extends BroadcastReceiver> bindFactoryForBroadcastReceiver(
          BroadcastReceiverSubcomponent.Builder b);
    }

    @Subcomponent(modules = ActivitySubcomponent.ActivityModule.class)
    interface ActivitySubcomponent extends AndroidInjector<TestActivity> {
      @Module
      abstract class ActivityModule {
        @Provides
        @IntoSet
        static Class<?> addToComponentHierarchy() {
          return ActivitySubcomponent.class;
        }
      }

      @Subcomponent.Builder
      abstract class Builder extends AndroidInjector.Builder<TestActivity> {}
    }

    @Subcomponent(modules = ParentFragmentSubcomponent.ParentFragmentModule.class)
    interface ParentFragmentSubcomponent extends AndroidInjector<TestParentFragment> {
      @Module
      abstract class ParentFragmentModule {
        @Provides
        @IntoSet
        static Class<?> addToComponentHierarchy() {
          return ParentFragmentSubcomponent.class;
        }
      }

      @Subcomponent.Builder
      abstract class Builder extends AndroidInjector.Builder<TestParentFragment> {}
    }

    @Subcomponent(modules = ChildFragmentSubcomponent.ChildFragmentModule.class)
    interface ChildFragmentSubcomponent extends AndroidInjector<TestChildFragment> {
      @Module
      abstract class ChildFragmentModule {
        @Provides
        @IntoSet
        static Class<?> addToComponentHierarchy() {
          return ChildFragmentSubcomponent.class;
        }
      }

      @Subcomponent.Builder
      abstract class Builder extends AndroidInjector.Builder<TestChildFragment> {}
    }

    @Subcomponent(modules = ServiceModule.class)
    interface ServiceSubcomponent extends AndroidInjector<TestService> {
      @Subcomponent.Builder
      abstract class Builder extends AndroidInjector.Builder<TestService> {}

      @Module
      abstract class ServiceModule {
        @Provides
        @IntoSet
        static Class<?> addToComponentHierarchy() {
          return ServiceSubcomponent.class;
        }
      }
    }

    @Subcomponent(modules = IntentServiceModule.class)
    interface IntentServiceSubcomponent extends AndroidInjector<TestIntentService> {
      @Subcomponent.Builder
      abstract class Builder extends AndroidInjector.Builder<TestIntentService> {}

      @Module
      abstract class IntentServiceModule {
        @Provides
        @IntoSet
        static Class<?> addToComponentHierarchy() {
          return IntentServiceSubcomponent.class;
        }
      }
    }

    @Subcomponent(modules = BroadcastReceiverModule.class)
    interface BroadcastReceiverSubcomponent extends AndroidInjector<TestBroadcastReceiver> {
      @Subcomponent.Builder
      abstract class Builder extends AndroidInjector.Builder<TestBroadcastReceiver> {}

      @Module
      abstract class BroadcastReceiverModule {
        @Provides
        @IntoSet
        static Class<?> addToComponentHierarchy() {
          return BroadcastReceiverSubcomponent.class;
        }
      }
    }
  }
}

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

import dagger.Binds;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.Subcomponent;
import dagger.android.AndroidInjectionModule;
import dagger.android.AndroidInjector;
import dagger.android.support.DaggerApplication;
import dagger.android.support.functional.AllControllersAreDirectChildrenOfApplication.ApplicationComponent.BroadcastReceiverSubcomponent.BroadcastReceiverModule;
import dagger.android.support.functional.AllControllersAreDirectChildrenOfApplication.ApplicationComponent.ContentProviderSubcomponent.ContentProviderModule;
import dagger.android.support.functional.AllControllersAreDirectChildrenOfApplication.ApplicationComponent.InnerActivitySubcomponent.InnerActivityModule;
import dagger.android.support.functional.AllControllersAreDirectChildrenOfApplication.ApplicationComponent.IntentServiceSubcomponent.IntentServiceModule;
import dagger.android.support.functional.AllControllersAreDirectChildrenOfApplication.ApplicationComponent.ServiceSubcomponent.ServiceModule;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;
import dagger.multibindings.IntoSet;

public final class AllControllersAreDirectChildrenOfApplication extends DaggerApplication {

  @Override
  protected AndroidInjector<AllControllersAreDirectChildrenOfApplication> applicationInjector() {
    return DaggerAllControllersAreDirectChildrenOfApplication_ApplicationComponent.create();
  }

  @Component(modules = {ApplicationComponent.ApplicationModule.class, AndroidInjectionModule.class})
  interface ApplicationComponent
      extends AndroidInjector<AllControllersAreDirectChildrenOfApplication> {
    @Module(
      subcomponents = {
        ActivitySubcomponent.class,
        InnerActivitySubcomponent.class,
        ParentFragmentSubcomponent.class,
        ChildFragmentSubcomponent.class,
        DialogFragmentSubcomponent.class,
        ServiceSubcomponent.class,
        IntentServiceSubcomponent.class,
        BroadcastReceiverSubcomponent.class,
        ContentProviderSubcomponent.class
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
      @ClassKey(TestActivity.class)
      abstract AndroidInjector.Factory<?> bindFactoryForTestActivity(
          ActivitySubcomponent.Builder builder);

      @Binds
      @IntoMap
      @ClassKey(OuterClass.TestInnerClassActivity.class)
      abstract AndroidInjector.Factory<?> bindFactoryForInnerActivity(
          InnerActivitySubcomponent.Builder builder);

      @Binds
      @IntoMap
      @ClassKey(TestParentFragment.class)
      abstract AndroidInjector.Factory<?> bindFactoryForParentFragment(
          ParentFragmentSubcomponent.Builder builder);

      @Binds
      @IntoMap
      @ClassKey(TestChildFragment.class)
      abstract AndroidInjector.Factory<?> bindFactoryForChildFragment(
          ChildFragmentSubcomponent.Builder builder);

      @Binds
      @IntoMap
      @ClassKey(TestDialogFragment.class)
      abstract AndroidInjector.Factory<?> bindFactoryForDialogFragment(
          DialogFragmentSubcomponent.Builder builder);

      @Binds
      @IntoMap
      @ClassKey(TestService.class)
      abstract AndroidInjector.Factory<?> bindFactoryForService(
          ServiceSubcomponent.Builder builder);

      @Binds
      @IntoMap
      @ClassKey(TestIntentService.class)
      abstract AndroidInjector.Factory<?> bindFactoryForIntentService(
          IntentServiceSubcomponent.Builder builder);

      @Binds
      @IntoMap
      @ClassKey(TestBroadcastReceiver.class)
      abstract AndroidInjector.Factory<?> bindFactoryForBroadcastReceiver(
          BroadcastReceiverSubcomponent.Builder builder);

      @Binds
      @IntoMap
      @ClassKey(TestContentProvider.class)
      abstract AndroidInjector.Factory<?> bindFactoryForContentProvider(
          ContentProviderSubcomponent.Builder builder);
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

    @Subcomponent(modules = InnerActivityModule.class)
    interface InnerActivitySubcomponent extends AndroidInjector<OuterClass.TestInnerClassActivity> {
      @Subcomponent.Builder
      abstract class Builder extends AndroidInjector.Builder<OuterClass.TestInnerClassActivity> {}

      @Module
      abstract class InnerActivityModule {
        @Provides
        @IntoSet
        static Class<?> addToComponentHierarchy() {
          return InnerActivitySubcomponent.class;
        }
      }
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

    @Subcomponent(modules = DialogFragmentSubcomponent.DialogFragmentModule.class)
    interface DialogFragmentSubcomponent extends AndroidInjector<TestDialogFragment> {
      @Module
      abstract class DialogFragmentModule {
        @Provides
        @IntoSet
        static Class<?> addToComponentHierarchy() {
          return DialogFragmentSubcomponent.class;
        }
      }

      @Subcomponent.Builder
      abstract class Builder extends AndroidInjector.Builder<TestDialogFragment> {}
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

    @Subcomponent(modules = ContentProviderModule.class)
    interface ContentProviderSubcomponent extends AndroidInjector<TestContentProvider> {
      @Subcomponent.Builder
      abstract class Builder extends AndroidInjector.Builder<TestContentProvider> {}

      @Module
      abstract class ContentProviderModule {
        @Provides
        @IntoSet
        static Class<?> addToComponentHierarchy() {
          return ContentProviderSubcomponent.class;
        }
      }
    }
  }
}

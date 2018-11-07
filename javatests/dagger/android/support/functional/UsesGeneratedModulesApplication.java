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

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.android.AndroidInjectionModule;
import dagger.android.AndroidInjector;
import dagger.android.ContributesAndroidInjector;
import dagger.android.support.DaggerApplication;
import dagger.multibindings.IntoSet;
import java.lang.annotation.Retention;
import java.util.UUID;
import javax.inject.Scope;

public final class UsesGeneratedModulesApplication extends DaggerApplication {

  @Override
  protected AndroidInjector<? extends DaggerApplication> applicationInjector() {
    return DaggerUsesGeneratedModulesApplication_ApplicationComponent.create();
  }

  @Component(modules = {ApplicationModule.class, AndroidInjectionModule.class})
  interface ApplicationComponent extends AndroidInjector<UsesGeneratedModulesApplication> {}

  @Module
  abstract static class ApplicationModule {
    @Provides
    @IntoSet
    static Class<?> addToComponentHierarchy() {
      return ApplicationComponent.class;
    }

    @ActivityScope
    @ContributesAndroidInjector(modules = ActivityScopedModule.class)
    abstract TestActivityWithScope contributeTestActivityWithScopeInjector();

    @ContributesAndroidInjector(modules = DummyActivitySubcomponent.AddToHierarchy.class)
    abstract TestActivity contributeTestActivityInjector();

    @ContributesAndroidInjector(modules = DummyInnerActivitySubcomponent.AddToHierarchy.class)
    abstract OuterClass.TestInnerClassActivity contributeInnerActivityInjector();

    @ContributesAndroidInjector(modules = DummyParentFragmentSubcomponent.AddToHierarchy.class)
    abstract TestParentFragment contributeTestParentFragmentInjector();

    @ContributesAndroidInjector(modules = DummyChildFragmentSubcomponent.AddToHierarchy.class)
    abstract TestChildFragment contributeTestChildFragmentInjector();

    @ContributesAndroidInjector(modules = DummyDialogFragmentSubcomponent.AddToHierarchy.class)
    abstract TestDialogFragment contributeTestDialogFragmentInjector();

    @ContributesAndroidInjector(modules = DummyServiceSubcomponent.AddToHierarchy.class)
    abstract TestService contributeTestServiceInjector();

    @ContributesAndroidInjector(modules = DummyIntentServiceSubcomponent.AddToHierarchy.class)
    abstract TestIntentService contributeTestIntentServiceInjector();

    @ContributesAndroidInjector(modules = DummyBroadcastReceiverSubcomponent.AddToHierarchy.class)
    abstract TestBroadcastReceiver contributeTestBroadcastReceiverInjector();

    @ContributesAndroidInjector(modules = DummyContentProviderSubcomponent.AddToHierarchy.class)
    abstract TestContentProvider contributeTestContentProviderInjector();
  }

  @Retention(RUNTIME)
  @Scope
  @interface ActivityScope {}

  @Module
  static class ActivityScopedModule {
    @Provides
    @ActivityScope
    static String provideScopedString() {
      return UUID.randomUUID().toString();
    }
  }

  interface DummyActivitySubcomponent {
    @Module
    abstract class AddToHierarchy {
      @Provides
      @IntoSet
      static Class<?> addDummyValueToComponentHierarchy() {
        return DummyActivitySubcomponent.class;
      }
    }
  }

  interface DummyInnerActivitySubcomponent {
    @Module
    abstract class AddToHierarchy {
      @Provides
      @IntoSet
      static Class<?> addDummyValueToComponentHierarchy() {
        return DummyInnerActivitySubcomponent.class;
      }
    }
  }

  interface DummyParentFragmentSubcomponent {
    @Module
    abstract class AddToHierarchy {
      @Provides
      @IntoSet
      static Class<?> addDummyValueToComponentHierarchy() {
        return DummyParentFragmentSubcomponent.class;
      }
    }
  }

  interface DummyChildFragmentSubcomponent {
    @Module
    abstract class AddToHierarchy {
      @Provides
      @IntoSet
      static Class<?> addDummyValueToComponentHierarchy() {
        return DummyChildFragmentSubcomponent.class;
      }
    }
  }

  interface DummyDialogFragmentSubcomponent {
    @Module
    abstract class AddToHierarchy {
      @Provides
      @IntoSet
      static Class<?> addDummyValueToComponentHierarchy() {
        return DummyDialogFragmentSubcomponent.class;
      }
    }
  }

  interface DummyServiceSubcomponent {
    @Module
    abstract class AddToHierarchy {
      @Provides
      @IntoSet
      static Class<?> addDummyValueToComponentHierarchy() {
        return DummyServiceSubcomponent.class;
      }
    }
  }

  interface DummyIntentServiceSubcomponent {
    @Module
    abstract class AddToHierarchy {
      @Provides
      @IntoSet
      static Class<?> addDummyValueToComponentHierarchy() {
        return DummyIntentServiceSubcomponent.class;
      }
    }
  }

  interface DummyBroadcastReceiverSubcomponent {
    @Module
    abstract class AddToHierarchy {
      @Provides
      @IntoSet
      static Class<?> addDummyValueToComponentHierarchy() {
        return DummyBroadcastReceiverSubcomponent.class;
      }
    }
  }

  interface DummyContentProviderSubcomponent {
    @Module
    abstract class AddToHierarchy {
      @Provides
      @IntoSet
      static Class<?> addDummyValueToComponentHierarchy() {
        return DummyContentProviderSubcomponent.class;
      }
    }
  }
}

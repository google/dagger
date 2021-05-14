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

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dagger.BindsInstance;
import dagger.hilt.DefineComponent;
import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.android.EarlyEntryPointHiltAndroidTestRuntimeClasses.MySubcomponentScoped;
import dagger.hilt.android.EarlyEntryPointHiltAndroidTestRuntimeTest.Bar;
import dagger.hilt.android.EarlyEntryPointHiltAndroidTestRuntimeTest.Foo;
import dagger.hilt.components.SingletonComponent;
import java.lang.annotation.Retention;
import javax.inject.Scope;

/** Classes for {@link EarlyEntryPointHiltAndroidTestRuntimeTest}. */
public final class EarlyEntryPointHiltAndroidTestRuntimeClasses {
  private EarlyEntryPointHiltAndroidTestRuntimeClasses() {}

  // @EarlyEntryPoint cannot be nested in tests, so we've separated it out into this class.
  @EarlyEntryPoint
  @InstallIn(SingletonComponent.class)
  interface EarlyFooEntryPoint {
    Foo foo();
  }

  // @EarlyEntryPoint cannot be nested in tests, so we've separated it out into this class.
  @EarlyEntryPoint
  @InstallIn(SingletonComponent.class)
  interface EarlyMySubcomponentBuilderEntryPoint {
    MySubcomponent.Builder mySubcomponentBuilder();
  }

  @Scope
  @Retention(RUNTIME)
  public @interface MySubcomponentScoped {}

  @MySubcomponentScoped
  @DefineComponent(parent = SingletonComponent.class)
  public interface MySubcomponent {
    @DefineComponent.Builder
    interface Builder {
      @BindsInstance
      Builder id(int id);

      MySubcomponent build();
    }
  }

  // This needs to be defined outside the test so that it gets installed in the early component.
  @EntryPoint
  @InstallIn(SingletonComponent.class)
  interface MySubcomponentBuilderEntryPoint {
    MySubcomponent.Builder mySubcomponentBuilder();
  }

  // This needs to be defined outside the test so that it gets installed in the early component.
  @EntryPoint
  @InstallIn(MySubcomponent.class)
  interface BarEntryPoint {
    Bar bar();
  }
}

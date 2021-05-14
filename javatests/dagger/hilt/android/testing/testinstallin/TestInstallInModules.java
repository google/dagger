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

package dagger.hilt.android.testing.testinstallin;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import dagger.hilt.testing.TestInstallIn;

/** Modules and classes used in TestInstallInFooTest and TestInstallInBarTest. */
final class TestInstallInModules {
  private TestInstallInModules() {}

  static class Foo {
    Class<?> moduleClass;

    Foo(Class<?> moduleClass) {
      this.moduleClass = moduleClass;
    }
  }

  static class Bar {
    Class<?> moduleClass;

    Bar(Class<?> moduleClass) {
      this.moduleClass = moduleClass;
    }
  }

  @Module
  @InstallIn(SingletonComponent.class)
  interface GlobalFooModule {
    @Provides
    static Foo provideFoo() {
      return new Foo(GlobalFooModule.class);
    }
  }

  @Module
  @InstallIn(SingletonComponent.class)
  interface GlobalBarModule {
    @Provides
    static Bar provideFoo() {
      return new Bar(GlobalBarModule.class);
    }
  }

  @Module
  @TestInstallIn(components = SingletonComponent.class, replaces = GlobalFooModule.class)
  interface GlobalFooTestModule {
    @Provides
    static Foo provideFoo() {
      return new Foo(GlobalFooTestModule.class);
    }
  }
}

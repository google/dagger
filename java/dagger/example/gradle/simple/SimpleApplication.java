/*
 * Copyright (C) 2019 The Dagger Authors.
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

package dagger.example.gradle.simple;

import dagger.Component;
import dagger.Module;
import dagger.Provides;
import javax.inject.Inject;
import javax.inject.Singleton;

/** A simple, skeletal application that defines a simple component. */
public class SimpleApplication {
  static final class Foo {
    @Inject Foo() {}
  }

  @Module
  static final class SimpleModule {
    @Provides
    static Foo provideFoo() {
      return new Foo();
    }
  }

  @Singleton
  @Component(modules = { SimpleModule.class })
  interface SimpleComponent {
    Foo foo();
  }

  public static void main(String[] args) {
    Foo foo = DaggerSimpleApplication_SimpleComponent.create().foo();
  }
}

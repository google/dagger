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

package dagger.example.gradle.simple;

import dagger.Component;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import javax.inject.Inject;

/** A simple, skeletal application that defines a simple component. */
public class AssistedInjectApplication {
  @Component
  interface MyComponent {
    FooFactory fooFactory();

    // ParameterizedFooFactory<Foo, String> parameterizedFooFactory();
  }

  static final class Bar {
    @Inject
    Bar() {}
  }

  static class Foo {
    @AssistedInject
    Foo(Bar bar, @Assisted String str) {}
  }

  @AssistedFactory
  interface FooFactory {
    Foo create(String str);
  }

//   static class ParameterizedFoo<T1, T2> {
//     @AssistedInject
//     ParameterizedFoo(T1 t1, @Assisted T2 t2) {}
//   }

//   @AssistedFactory
//   interface ParameterizedFooFactory<T1, T2> {
//     ParameterizedFoo<T1, T2> create(T2 t2);
//   }

  public static void main(String[] args) {
    Foo foo = DaggerAssistedInjectApplication_MyComponent.create().fooFactory().create("");
    // ParameterizedFoo<Foo, String> parameterizedFoo =
    //     DaggerAssistedInjectApplication_MyComponent.create()
    //         .parameterizedFooFactory()
    //         .create("");
  }
}

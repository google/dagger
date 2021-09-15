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

package dagger.functional.binds;

import static com.google.common.truth.Truth.assertThat;

import dagger.Binds;
import dagger.Component;
import dagger.Module;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ScopedBindsTest {
  interface Foo {}

  static final class FooImpl implements Foo {
    @Inject FooImpl() {}
  }

  @Module
  interface FooModule {
    @Binds
    @Singleton
    Foo bindFoo(FooImpl impl);
  }

  @Component(modules = FooModule.class)
  @Singleton
  interface TestComponent {
    Foo foo();

    Provider<Foo> fooProvider();
  }

  @Test
  public void fooVsFooProvider_sameInstanceTest() {
    TestComponent component = DaggerScopedBindsTest_TestComponent.create();

    // These should be the same instance because Foo is scoped
    assertThat(component.foo()).isSameInstanceAs(component.fooProvider().get());
  }
}

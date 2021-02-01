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

package dagger.functional.assisted;

import static com.google.common.truth.Truth.assertThat;

import dagger.Component;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AssistedFactoryWithArrayTypesTest {
  @Component
  interface TestComponent {
    FooFactory fooFactory();
  }

  @AssistedFactory
  interface FooFactory {
    Foo create(Dep[] depArray);
  }

  static class Dep {}

  static class Foo {
    private final Dep[] depArray;

    @AssistedInject
    Foo(@Assisted Dep[] depArray) {
      this.depArray = depArray;
    }
  }

  @Test
  public void testFooFactory() {
    Dep[] depArray = {new Dep(), new Dep()};
    Foo foo =
        DaggerAssistedFactoryWithArrayTypesTest_TestComponent.create()
            .fooFactory()
            .create(depArray);
    assertThat(foo.depArray).isEqualTo(depArray);
  }
}

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

import dagger.BindsInstance;
import dagger.Component;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AssistedFactoryDuplicatedParamNamesTest {
  static final class Foo {
    private final String arg;
    private final Bar bar;

    @AssistedInject
    Foo(@Assisted String arg, Bar bar) {
      this.arg = arg;
      this.bar = bar;
    }

    Bar getBar() {
      return bar;
    }

    String getArg() {
      return arg;
    }
  }

  static final class Bar {}

  @AssistedFactory
  interface FooFactory {
    Foo create(String arg);
  }

  @Component
  interface TestComponent {
    @Component.Factory
    interface Factory {
      TestComponent create(@BindsInstance Bar arg);
    }

    FooFactory fooFactory();
  }

  @Test
  public void duplicatedParameterNames_doesNotConflict() {
    String str = "test";
    Bar bar = new Bar();

    Foo foo =
        DaggerAssistedFactoryDuplicatedParamNamesTest_TestComponent.factory()
            .create(bar)
            .fooFactory()
            .create(str);

    assertThat(foo.getArg()).isEqualTo(str);
    assertThat(foo.getBar()).isEqualTo(bar);
  }
}

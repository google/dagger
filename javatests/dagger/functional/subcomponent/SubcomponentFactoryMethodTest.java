/*
 * Copyright (C) 2018 The Dagger Authors.
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

package dagger.functional.subcomponent;

import static org.junit.Assert.fail;

import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.Subcomponent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for subcomponent factory methods. */
@RunWith(JUnit4.class)
public final class SubcomponentFactoryMethodTest {

  @Module
  static class IntModule {
    @Provides
    int provideInt() {
      return 42;
    }
  }

  @Module
  static class StringModule {
    final String s;

    StringModule(String s) {
      this.s = s;
    }

    @Provides
    String provideString(int i) {
      return s + i;
    }
  }

  @Component(modules = IntModule.class)
  interface TestComponent {
    TestSubcomponent newSubcomponent(StringModule stringModule);
  }

  @Subcomponent(modules = StringModule.class)
  interface TestSubcomponent {
    String string();
  }

  @Test
  public void creatingSubcomponentViaFactoryMethod_failsForNullParameter() {
    TestComponent component = DaggerSubcomponentFactoryMethodTest_TestComponent.create();
    try {
      component.newSubcomponent(null);
      fail();
    } catch (NullPointerException expected) {
    }
  }
}

/*
 * Copyright (C) 2026 The Dagger Authors.
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

import static com.google.common.truth.Truth.assertThat;

import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.Subcomponent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for subcomponent factory methods defined on generic supertypes. */
@RunWith(JUnit4.class)
public final class GenericSubcomponentFactoryMethodTest {

  @Component(modules = ParentModule.class)
  interface Parent extends SubcomponentProvider<Child, ChildModule> {}

  @Module
  static class ParentModule {
    @Provides
    int provideInt() {
      return 42;
    }
  }

  @Subcomponent(modules = ChildModule.class)
  interface Child {
    String string();
  }

  @Module
  static class ChildModule {
    final String s;

    ChildModule(String s) {
      this.s = s;
    }

    @Provides
    String provideString(int i) {
      return s + i;
    }
  }

  public interface SubcomponentProvider<C, M> {
    C createSubcomponent(M module);
  }

  @Test
  public void factoryMethod_genericSupertype() {
    Parent parent = DaggerGenericSubcomponentFactoryMethodTest_Parent.create();
    Child child = parent.createSubcomponent(new ChildModule("hello "));
    assertThat(child.string()).isEqualTo("hello 42");
  }
}

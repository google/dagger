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

package dagger.functional.factory;

import static com.google.common.truth.Truth.assertThat;

import dagger.BindsInstance;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.Subcomponent;
import javax.inject.Inject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@linkplain Subcomponent.Factory subcomponent factories}.
 *
 * <p>Most things are tested in {@code FactoryTest}; this is just intended to test some things like
 * injecting subcomponent factories and returning them from component methods.
 */
@RunWith(JUnit4.class)
public final class SubcomponentFactoryTest {

  @Component
  interface ParentWithSubcomponentFactory {
    Sub.Factory subcomponentFactory();

    @Component.Factory
    interface Factory {
      ParentWithSubcomponentFactory create(@BindsInstance int i);
    }
  }

  @Subcomponent
  interface Sub {
    int i();
    String s();

    @Subcomponent.Factory
    interface Factory {
      Sub create(@BindsInstance String s);
    }
  }

  @Test
  public void parentComponentWithSubcomponentFactoryEntryPoint() {
    ParentWithSubcomponentFactory parent =
        DaggerSubcomponentFactoryTest_ParentWithSubcomponentFactory.factory().create(3);
    Sub subcomponent = parent.subcomponentFactory().create("foo");
    assertThat(subcomponent.i()).isEqualTo(3);
    assertThat(subcomponent.s()).isEqualTo("foo");
  }

  @Module(subcomponents = Sub.class)
  abstract static class ModuleWithSubcomponent {
    @Provides
    static int provideInt() {
      return 42;
    }
  }

  static class UsesSubcomponentFactory {
    private final Sub.Factory subFactory;

    @Inject
    UsesSubcomponentFactory(Sub.Factory subFactory) {
      this.subFactory = subFactory;
    }

    Sub getSubcomponent(String s) {
      return subFactory.create(s);
    }
  }

  @Component(modules = ModuleWithSubcomponent.class)
  interface ParentWithModuleWithSubcomponent {
    UsesSubcomponentFactory usesSubcomponentFactory();
  }

  @Test
  public void parentComponentWithModuleWithSubcomponent() {
    ParentWithModuleWithSubcomponent parent =
        DaggerSubcomponentFactoryTest_ParentWithModuleWithSubcomponent.create();
    UsesSubcomponentFactory usesSubcomponentFactory = parent.usesSubcomponentFactory();

    Sub subcomponent1 = usesSubcomponentFactory.getSubcomponent("foo");
    assertThat(subcomponent1.i()).isEqualTo(42);
    assertThat(subcomponent1.s()).isEqualTo("foo");

    Sub subcomponent2 = usesSubcomponentFactory.getSubcomponent("bar");
    assertThat(subcomponent2.i()).isEqualTo(42);
    assertThat(subcomponent2.s()).isEqualTo("bar");
  }
}

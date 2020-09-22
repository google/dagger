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

package dagger.functional.assisted;

import static com.google.common.truth.Truth.assertThat;

import dagger.Binds;
import dagger.Component;
import dagger.Module;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import javax.inject.Inject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AssistedFactoryBindsTest {
  @Component(modules = FooFactoryModule.class)
  interface ParentComponent {
    // Test using @Binds where Foo => FooImpl and FooFactory => FooFactoryImpl
    FooFactory fooFactory();
  }

  @Module
  interface FooFactoryModule {
    @Binds
    FooFactory bind(FooFactoryImpl impl);
  }

  interface Foo {}

  static final class FooImpl implements Foo {
    private final Dep dep;
    private final AssistedDep assistedDep;

    @AssistedInject
    FooImpl(Dep dep, @Assisted AssistedDep assistedDep) {
      this.dep = dep;
      this.assistedDep = assistedDep;
    }
  }

  interface FooFactory {
    Foo create(AssistedDep assistedDep);
  }

  @AssistedFactory
  interface FooFactoryImpl extends FooFactory {
    @Override
    FooImpl create(AssistedDep assistedDep);
  }

  static final class AssistedDep {}

  static final class Dep {
    @Inject
    Dep() {}
  }

  @Test
  public void testFooFactory() {
    FooFactory fooFactory = DaggerAssistedFactoryBindsTest_ParentComponent.create().fooFactory();
    assertThat(fooFactory).isInstanceOf(FooFactoryImpl.class);

    AssistedDep assistedDep = new AssistedDep();
    Foo foo = fooFactory.create(assistedDep);
    assertThat(foo).isInstanceOf(FooImpl.class);

    FooImpl fooImpl = (FooImpl) foo;
    assertThat(fooImpl.dep).isNotNull();
    assertThat(fooImpl.assistedDep).isEqualTo(assistedDep);
  }
}

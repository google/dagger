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

import dagger.Component;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AssistedFactoryParameterizedTest {
  @Singleton
  @Component
  interface ParentComponent {
    // Parameterized Factory
    ParameterizedFooFactory<Dep2, AssistedDep2> parameterizedFooFactory();

    // This class tests the request of factories from another binding.
    SomeEntryPoint someEntryPoint();
  }

  static class SomeEntryPoint {
    private final ParameterizedFooFactory<Dep1, AssistedDep1> parameterizedFooFactory;

    @Inject
    SomeEntryPoint(ParameterizedFooFactory<Dep1, AssistedDep1> parameterizedFooFactory) {
      this.parameterizedFooFactory = parameterizedFooFactory;
    }
  }

  static final class Dep1 {
    @Inject
    Dep1(Dep2 dep2, Dep3 dep3) {}
  }

  static final class Dep2 {
    @Inject
    Dep2(Dep3 dep3) {}
  }

  static final class Dep3 {
    @Inject
    Dep3(Dep4 dep4) {}
  }

  static final class Dep4 {
    @Inject
    Dep4() {}
  }

  // A base interface to test that factories can reference subclasses of the assisted parameter.
  interface AssistedDep {}

  static final class AssistedDep1 implements AssistedDep {}

  static final class AssistedDep2 implements AssistedDep {}

  abstract static class BaseFoo {
    @Inject Dep4 dep4;
  }

  static final class ParameterizedFoo<DepT, AssistedDepT> extends BaseFoo {
    private final Dep1 dep1;
    private final Provider<DepT> depTProvider;
    private final AssistedDep1 assistedDep1;
    private final AssistedDepT assistedDepT;
    private final int assistedInt;
    private final ParameterizedFooFactory<DepT, AssistedDepT> factory;

    @Inject Dep3 dep3;

    @AssistedInject
    ParameterizedFoo(
        Dep1 dep1,
        @Assisted AssistedDep1 assistedDep1,
        Provider<DepT> depTProvider,
        @Assisted AssistedDepT assistedDepT,
        @Assisted int assistedInt,
        ParameterizedFooFactory<DepT, AssistedDepT> factory) {
      this.dep1 = dep1;
      this.depTProvider = depTProvider;
      this.assistedDep1 = assistedDep1;
      this.assistedDepT = assistedDepT;
      this.assistedInt = assistedInt;
      this.factory = factory;
    }
  }

  @AssistedFactory
  interface ParameterizedFooFactory<DepT, AssistedDepT> {
    // Use different parameter names than Foo to make sure we're not assuming they're the same.
    ParameterizedFoo<DepT, AssistedDepT> create(
        AssistedDep1 factoryAssistedDep1, AssistedDepT factoryAssistedDepT, int factoryAssistedInt);
  }

  @Test
  public void testParameterizedFooFactory() {
    AssistedDep1 assistedDep1 = new AssistedDep1();
    AssistedDep2 assistedDep2 = new AssistedDep2();
    int assistedInt = 7;
    ParameterizedFoo<Dep2, AssistedDep2> parameterizedFoo =
        DaggerAssistedFactoryParameterizedTest_ParentComponent.create()
            .parameterizedFooFactory()
            .create(assistedDep1, assistedDep2, assistedInt);
    assertThat(parameterizedFoo.dep1).isNotNull();
    assertThat(parameterizedFoo.depTProvider).isNotNull();
    assertThat(parameterizedFoo.depTProvider.get()).isNotNull();
    assertThat(parameterizedFoo.dep3).isNotNull();
    assertThat(parameterizedFoo.dep4).isNotNull();
    assertThat(parameterizedFoo.assistedDep1).isEqualTo(assistedDep1);
    assertThat(parameterizedFoo.assistedDepT).isEqualTo(assistedDep2);
    assertThat(parameterizedFoo.assistedInt).isEqualTo(assistedInt);
    assertThat(parameterizedFoo.factory).isNotNull();
  }

  @Test
  public void testParameterizedFooFactoryFromSomeEntryPoint() {
    AssistedDep1 assistedDep1 = new AssistedDep1();
    int assistedInt = 7;
    ParameterizedFoo<Dep1, AssistedDep1> parameterizedFoo =
        DaggerAssistedFactoryParameterizedTest_ParentComponent.create()
            .someEntryPoint()
            .parameterizedFooFactory
            .create(assistedDep1, assistedDep1, assistedInt);
    assertThat(parameterizedFoo.dep1).isNotNull();
    assertThat(parameterizedFoo.depTProvider).isNotNull();
    assertThat(parameterizedFoo.depTProvider.get()).isNotNull();
    assertThat(parameterizedFoo.dep3).isNotNull();
    assertThat(parameterizedFoo.dep4).isNotNull();
    assertThat(parameterizedFoo.assistedDep1).isEqualTo(assistedDep1);
    assertThat(parameterizedFoo.assistedDepT).isEqualTo(assistedDep1);
    assertThat(parameterizedFoo.assistedInt).isEqualTo(assistedInt);
    assertThat(parameterizedFoo.factory).isNotNull();
  }
}

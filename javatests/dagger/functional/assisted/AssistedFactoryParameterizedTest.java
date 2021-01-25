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
    // Tests a parameterized Factory with unique @Assisted types
    ParameterizedFooFactory<Dep2, AssistedDep2> uniqueParameterizedFooFactory();

    // Tests a parameterized Factory with duplicate @Assisted types in its resolved request type.
    // Note: this is fine since the @Assisted types are still unique on the @AssistedInject and
    // @AssistedFactory types, so that the generated code can correctly matches types.
    ParameterizedFooFactory<Dep1, AssistedDep1> dupeParameterizedFooFactory();

    // Tests a parameterized Factory with same type as binding
    ParameterizedFooFactory<Dep1, Dep1> bindingParameterizedFooFactory();

    // Tests a parameterized Factory with fixed type parameters
    FixedParameterizedFooFactory fixedParameterizedFooFactory();

    // Tests a parameterized Factory that extends an interface with a parameterized return type
    ExtendedFooFactory<Dep2, AssistedDep2> extendedParameterizedFooFactory();

    // Tests a request of factories from another binding.
    SomeEntryPoint someEntryPoint();
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
    ParameterizedFoo<DepT, AssistedDepT> create(
        AssistedDep1 assistedDep1, AssistedDepT assistedDepT, int assistedInt);
  }

  @Test
  public void testUniqueParameterizedFooFactory() {
    AssistedDep1 assistedDep1 = new AssistedDep1();
    AssistedDep2 assistedDep2 = new AssistedDep2();
    int assistedInt = 7;
    ParameterizedFoo<Dep2, AssistedDep2> parameterizedFoo =
        DaggerAssistedFactoryParameterizedTest_ParentComponent.create()
            .uniqueParameterizedFooFactory()
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
  public void testDupeParameterizedFooFactory() {
    AssistedDep1 assistedDep1 = new AssistedDep1();
    int assistedInt = 7;
    ParameterizedFoo<Dep1, AssistedDep1> parameterizedFoo =
        DaggerAssistedFactoryParameterizedTest_ParentComponent.create()
            .dupeParameterizedFooFactory()
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

  @Test
  public void testBindingParameterizedFooFactory() {
    AssistedDep1 assistedDep1 = new AssistedDep1();
    Dep1 dep1 = new Dep1(new Dep2(new Dep3(new Dep4())), new Dep3(new Dep4()));
    int assistedInt = 7;
    ParameterizedFoo<Dep1, Dep1> parameterizedFoo =
        DaggerAssistedFactoryParameterizedTest_ParentComponent.create()
            .bindingParameterizedFooFactory()
            .create(assistedDep1, dep1, assistedInt);
    assertThat(parameterizedFoo.dep1).isNotNull();
    assertThat(parameterizedFoo.depTProvider).isNotNull();
    assertThat(parameterizedFoo.depTProvider.get()).isNotNull();
    assertThat(parameterizedFoo.dep3).isNotNull();
    assertThat(parameterizedFoo.dep4).isNotNull();
    assertThat(parameterizedFoo.assistedDep1).isEqualTo(assistedDep1);
    assertThat(parameterizedFoo.assistedDepT).isEqualTo(dep1);
    assertThat(parameterizedFoo.assistedInt).isEqualTo(assistedInt);
    assertThat(parameterizedFoo.factory).isNotNull();
  }

  @AssistedFactory
  interface FixedParameterizedFooFactory {
    ParameterizedFoo<Dep2, AssistedDep2> create(
        AssistedDep1 assistedDep1, AssistedDep2 assistedDep2, int assistedInt);
  }

  @Test
  public void testFixedParameterizedFooFactory() {
    AssistedDep1 assistedDep1 = new AssistedDep1();
    AssistedDep2 assistedDep2 = new AssistedDep2();
    int assistedInt = 7;
    ParameterizedFoo<Dep2, AssistedDep2> parameterizedFoo =
        DaggerAssistedFactoryParameterizedTest_ParentComponent.create()
            .fixedParameterizedFooFactory()
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

  interface ParameterizedFactory<ReturnT, DepT, AssistedDepT> {
    // Use different parameter names than Foo to make sure we're not assuming they're the same.
    ReturnT create(
        AssistedDep1 factoryAssistedDep1, AssistedDepT factoryAssistedDepT, int factoryAssistedInt);
  }

  @AssistedFactory
  interface ExtendedFooFactory<DepT, AssistedDepT>
      extends ParameterizedFactory<ParameterizedFoo<DepT, AssistedDepT>, DepT, AssistedDepT> {}

  @Test
  public void testExtendedFooFactory() {
    AssistedDep1 assistedDep1 = new AssistedDep1();
    AssistedDep2 assistedDep2 = new AssistedDep2();
    int assistedInt = 7;
    ParameterizedFoo<Dep2, AssistedDep2> parameterizedFoo =
        DaggerAssistedFactoryParameterizedTest_ParentComponent.create()
            .extendedParameterizedFooFactory()
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

  static class SomeEntryPoint {
    private final ParameterizedFooFactory<Dep1, AssistedDep1> dupeParameterizedFooFactory;

    @Inject
    SomeEntryPoint(ParameterizedFooFactory<Dep1, AssistedDep1> dupeParameterizedFooFactory) {
      this.dupeParameterizedFooFactory = dupeParameterizedFooFactory;
    }
  }

  @Test
  public void testParameterizedFooFactoryFromSomeEntryPoint() {
    AssistedDep1 assistedDep1 = new AssistedDep1();
    int assistedInt = 7;
    ParameterizedFoo<Dep1, AssistedDep1> parameterizedFoo =
        DaggerAssistedFactoryParameterizedTest_ParentComponent.create()
            .someEntryPoint()
            .dupeParameterizedFooFactory
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

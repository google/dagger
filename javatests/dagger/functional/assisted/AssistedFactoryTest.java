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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AssistedFactoryTest {
  @Component
  interface ParentComponent {
    // Simple factory using a nested factory.
    SimpleFoo.Factory nestedSimpleFooFactory();

    Provider<SimpleFoo.Factory> nestedSimpleFooFactoryProvider();

    // Simple factory using a non-nested factory.
    SimpleFooFactory nonNestedSimpleFooFactory();

    // Simple factory using a factory that extends a supertype.
    ExtendedSimpleFooFactory extendedSimpleFooFactory();

    // Factory as interface
    FooFactory fooFactory();

    // Factory as abstract class
    AbstractFooFactory abstractFooFactory();

    // Factory without any assisted parameters
    NoAssistedParametersFooFactory noAssistedParametersFooFactory();

    // Test injecting the factories from another class
    SomeEntryPoint someEntryPoint();
  }

  // This class tests the request of factories from another binding.
  static class SomeEntryPoint {
    private final SimpleFoo.Factory nestedSimpleFooFactory;
    private final SimpleFooFactory nonNestedSimpleFooFactory;
    private final Provider<SimpleFoo.Factory> nestedSimpleFooFactoryProvider;
    private final ExtendedSimpleFooFactory extendedSimpleFooFactory;
    private final FooFactory fooFactory;
    private final AbstractFooFactory abstractFooFactory;
    private final NoAssistedParametersFooFactory noAssistedParametersFooFactory;

    @Inject
    SomeEntryPoint(
        SimpleFoo.Factory nestedSimpleFooFactory,
        Provider<SimpleFoo.Factory> nestedSimpleFooFactoryProvider,
        SimpleFooFactory nonNestedSimpleFooFactory,
        ExtendedSimpleFooFactory extendedSimpleFooFactory,
        FooFactory fooFactory,
        AbstractFooFactory abstractFooFactory,
        NoAssistedParametersFooFactory noAssistedParametersFooFactory) {
      this.nestedSimpleFooFactory = nestedSimpleFooFactory;
      this.nestedSimpleFooFactoryProvider = nestedSimpleFooFactoryProvider;
      this.nonNestedSimpleFooFactory = nonNestedSimpleFooFactory;
      this.extendedSimpleFooFactory = extendedSimpleFooFactory;
      this.fooFactory = fooFactory;
      this.abstractFooFactory = abstractFooFactory;
      this.noAssistedParametersFooFactory = noAssistedParametersFooFactory;
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

  static final class SimpleFoo {
    private final AssistedDep assistedDep;

    @AssistedInject
    SimpleFoo(@Assisted AssistedDep assistedDep) {
      this.assistedDep = assistedDep;
    }

    @AssistedFactory
    interface Factory {
      // Use different parameter names than Foo to make sure we're not assuming they're the same.
      SimpleFoo createSimpleFoo(AssistedDep factoryAssistedDep);

      // A no-op method to test static methods in assisted factories
      static void staticMethod() {
        return;
      }

      // A no-op method to test default methods in assisted factories
      default void defaultMethod() {
        return;
      }
    }
  }

  @AssistedFactory
  interface SimpleFooFactory {
    // Use different parameter names than Foo to make sure we're not assuming they're the same.
    SimpleFoo createSimpleFoo(AssistedDep factoryAssistedDep1);

    // A no-op method to test static methods are allowed
    static void staticMethod() {
      return;
    }

    // A no-op method to test static methods that return assisted type are allowed
    static SimpleFoo staticSimpleFooMethod() {
      return null;
    }

    // A no-op method to test default methods are allowed
    default void defaultMethod() {
      return;
    }

    // A no-op method to test default methods that return assisted type are allowed
    default SimpleFoo defaultSimpleFooMethod() {
      return null;
    }
  }

  @AssistedFactory
  interface ExtendedSimpleFooFactory extends SimpleFooFactory {}

  abstract static class BaseFoo {
    @Inject Dep4 dep4;
  }

  static final class Foo extends BaseFoo {
    private final Dep1 dep1;
    private final Provider<Dep2> dep2Provider;
    private final AssistedDep1 assistedDep1;
    private final AssistedDep2 assistedDep2;
    private final int assistedInt;
    private final FooFactory factory;

    @Inject Dep3 dep3;

    @AssistedInject
    Foo(
        Dep1 dep1,
        @Assisted AssistedDep1 assistedDep1,
        Provider<Dep2> dep2Provider,
        @Assisted AssistedDep2 assistedDep2,
        @Assisted int assistedInt,
        FooFactory factory) {
      this.dep1 = dep1;
      this.dep2Provider = dep2Provider;
      this.assistedDep1 = assistedDep1;
      this.assistedDep2 = assistedDep2;
      this.assistedInt = assistedInt;
      this.factory = factory;
    }
  }

  @AssistedFactory
  interface FooFactory {
    // Use different parameter names than Foo to make sure we're not assuming they're the same.
    Foo createFoo(
        AssistedDep1 factoryAssistedDep1, AssistedDep2 factoryAssistedDep2, int factoryAssistedInt);
  }

  @AssistedFactory
  abstract static class AbstractFooFactory {
    // Use different parameter names than Foo to make sure we're not assuming they're the same.
    abstract Foo createFoo(
        AssistedDep1 factoryAssistedDep1, AssistedDep2 factoryAssistedDep2, int factoryAssistedInt);

    // A no-op method to test static methods are allowed
    static void staticMethod() {
      return;
    }

    // A no-op method to test static methods that return assisted type are allowed
    static Foo staticFooMethod() {
      return null;
    }

    // A no-op method to test concrete methods are allowed
    void concreteMethod() {
      return;
    }

    // A no-op method to test concrete methods that return assisted type are allowed
    Foo concreteFooMethod() {
      return null;
    }
  }

  static final class NoAssistedParametersFoo extends BaseFoo {
    private final Dep1 dep1;
    private final Provider<Dep2> dep2Provider;
    private final NoAssistedParametersFooFactory factory;

    @Inject Dep3 dep3;

    @AssistedInject
    NoAssistedParametersFoo(
        Dep1 dep1, Provider<Dep2> dep2Provider, NoAssistedParametersFooFactory factory) {
      this.dep1 = dep1;
      this.dep2Provider = dep2Provider;
      this.factory = factory;
    }
  }

  @AssistedFactory
  interface NoAssistedParametersFooFactory {
    NoAssistedParametersFoo createNoAssistedParametersFoo();
  }

  @Test
  public void testNestedSimpleFooFactory() {
    AssistedDep1 assistedDep1 = new AssistedDep1();
    SimpleFoo simpleFoo1 =
        DaggerAssistedFactoryTest_ParentComponent.create()
            .nestedSimpleFooFactory()
            .createSimpleFoo(assistedDep1);
    assertThat(simpleFoo1.assistedDep).isEqualTo(assistedDep1);

    AssistedDep2 assistedDep2 = new AssistedDep2();
    SimpleFoo simpleFoo2 =
        DaggerAssistedFactoryTest_ParentComponent.create()
            .nestedSimpleFooFactory()
            .createSimpleFoo(assistedDep2);
    assertThat(simpleFoo2.assistedDep).isEqualTo(assistedDep2);
  }

  @Test
  public void testNestedSimpleFooFactoryProvider() {
    AssistedDep1 assistedDep1 = new AssistedDep1();
    SimpleFoo simpleFoo1 =
        DaggerAssistedFactoryTest_ParentComponent.create()
            .nestedSimpleFooFactoryProvider()
            .get()
            .createSimpleFoo(assistedDep1);
    assertThat(simpleFoo1.assistedDep).isEqualTo(assistedDep1);

    AssistedDep2 assistedDep2 = new AssistedDep2();
    SimpleFoo simpleFoo2 =
        DaggerAssistedFactoryTest_ParentComponent.create()
            .nestedSimpleFooFactoryProvider()
            .get()
            .createSimpleFoo(assistedDep2);
    assertThat(simpleFoo2.assistedDep).isEqualTo(assistedDep2);
  }

  @Test
  public void testNonNestedSimpleFooFactory() {
    AssistedDep1 assistedDep1 = new AssistedDep1();
    SimpleFoo simpleFoo =
        DaggerAssistedFactoryTest_ParentComponent.create()
            .nonNestedSimpleFooFactory()
            .createSimpleFoo(assistedDep1);
    assertThat(simpleFoo.assistedDep).isEqualTo(assistedDep1);
  }

  @Test
  public void testExtendedSimpleFooFactory() {
    AssistedDep1 assistedDep1 = new AssistedDep1();
    SimpleFoo simpleFoo =
        DaggerAssistedFactoryTest_ParentComponent.create()
            .extendedSimpleFooFactory()
            .createSimpleFoo(assistedDep1);
    assertThat(simpleFoo.assistedDep).isEqualTo(assistedDep1);
  }

  @Test
  public void testFooFactory() {
    AssistedDep1 assistedDep1 = new AssistedDep1();
    AssistedDep2 assistedDep2 = new AssistedDep2();
    int assistedInt = 7;
    Foo foo =
        DaggerAssistedFactoryTest_ParentComponent.create()
            .fooFactory()
            .createFoo(assistedDep1, assistedDep2, assistedInt);
    assertThat(foo.dep1).isNotNull();
    assertThat(foo.dep2Provider).isNotNull();
    assertThat(foo.dep2Provider.get()).isNotNull();
    assertThat(foo.dep3).isNotNull();
    assertThat(foo.dep4).isNotNull();
    assertThat(foo.assistedDep1).isEqualTo(assistedDep1);
    assertThat(foo.assistedDep2).isEqualTo(assistedDep2);
    assertThat(foo.assistedInt).isEqualTo(assistedInt);
    assertThat(foo.factory).isNotNull();
  }

  @Test
  public void testNoAssistedParametersFooFactory() {
    NoAssistedParametersFoo foo =
        DaggerAssistedFactoryTest_ParentComponent.create()
            .noAssistedParametersFooFactory()
            .createNoAssistedParametersFoo();
    assertThat(foo.dep1).isNotNull();
    assertThat(foo.dep2Provider).isNotNull();
    assertThat(foo.dep2Provider.get()).isNotNull();
    assertThat(foo.dep3).isNotNull();
    assertThat(foo.dep4).isNotNull();
    assertThat(foo.factory).isNotNull();
  }

  @Test
  public void testAssistedFactoryFromSomeEntryPoint() {
    SomeEntryPoint someEntryPoint =
        DaggerAssistedFactoryTest_ParentComponent.create().someEntryPoint();
    assertThat(someEntryPoint.nestedSimpleFooFactory).isNotNull();
    assertThat(someEntryPoint.nestedSimpleFooFactoryProvider).isNotNull();
    assertThat(someEntryPoint.nonNestedSimpleFooFactory).isNotNull();
    assertThat(someEntryPoint.extendedSimpleFooFactory).isNotNull();
    assertThat(someEntryPoint.fooFactory).isNotNull();
    assertThat(someEntryPoint.abstractFooFactory).isNotNull();
    assertThat(someEntryPoint.noAssistedParametersFooFactory).isNotNull();
  }
}

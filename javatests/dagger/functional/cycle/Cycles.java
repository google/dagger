/*
 * Copyright (C) 2015 The Dagger Authors.
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

package dagger.functional.cycle;

import dagger.Binds;
import dagger.Component;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import dagger.Subcomponent;
import dagger.multibindings.IntoMap;
import dagger.multibindings.StringKey;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Cycle classes used for testing cyclic dependencies.
 *
 * <pre>
 * {@literal A ← (E ← D ← B ← C ← Provider<A>, Lazy<A>), (B ← C ← Provider<A>, Lazy<A>)}
 * {@literal S ← Provider<S>, Lazy<S>}
 * </pre>
 */
final class Cycles {
  private Cycles() {}

  static class A {
    public final B b;
    public final E e;

    @Inject
    A(E e, B b) {
      this.e = e;
      this.b = b;
    }
  }

  static class B {
    public final C c;

    @Inject
    B(C c) {
      this.c = c;
    }
  }

  static class C {
    public final Provider<A> aProvider;
    @Inject public Lazy<A> aLazy;
    @Inject public Provider<Lazy<A>> aLazyProvider;

    @Inject
    C(Provider<A> aProvider) {
      this.aProvider = aProvider;
    }
  }

  static class D {
    public final B b;

    @Inject
    D(B b) {
      this.b = b;
    }
  }

  static class E {
    public final D d;

    @Inject
    E(D d) {
      this.d = d;
    }
  }

  static class S {
    public final Provider<S> sProvider;
    @Inject public Lazy<S> sLazy;

    @Inject
    S(Provider<S> sProvider) {
      this.sProvider = sProvider;
    }
  }

  static class X {
    public final Y y;

    @Inject
    X(Y y) {
      this.y = y;
    }
  }

  static class Y {
    public final Map<String, Provider<X>> mapOfProvidersOfX;
    public final Map<String, Provider<Y>> mapOfProvidersOfY;

    @Inject
    Y(Map<String, Provider<X>> mapOfProvidersOfX, Map<String, Provider<Y>> mapOfProvidersOfY) {
      this.mapOfProvidersOfX = mapOfProvidersOfX;
      this.mapOfProvidersOfY = mapOfProvidersOfY;
    }
  }

  @Module
  abstract static class CycleMapModule {
    @Binds
    @IntoMap
    @StringKey("X")
    abstract X x(X x);

    @Binds
    @IntoMap
    @StringKey("Y")
    abstract Y y(Y y);
  }

  @SuppressWarnings("dependency-cycle")
  @Component(modules = CycleMapModule.class)
  interface CycleMapComponent {
    Y y();
  }

  @SuppressWarnings("dependency-cycle")
  @Component(modules = CycleModule.class)
  interface CycleComponent {
    A a();

    C c();

    ChildCycleComponent child();
  }

  @Module
  static class CycleModule {
    @Provides
    static Object provideObjectWithCycle(@SuppressWarnings("unused") Provider<Object> object) {
      return "object";
    }
  }

  @SuppressWarnings("dependency-cycle")
  @Component
  interface SelfCycleComponent {
    S s();
  }

  @Subcomponent
  interface ChildCycleComponent {
    @SuppressWarnings("dependency-cycle")
    A a();

    @SuppressWarnings("dependency-cycle")
    Object object();
  }

  interface Foo {}

  static class Bar implements Foo {
    @Inject
    Bar(Provider<Foo> fooProvider) {}
  }

  /**
   * A component with a cycle in which a {@code @Binds} binding depends on the binding that has to
   * be deferred.
   */
  @Component(modules = BindsCycleModule.class)
  interface BindsCycleComponent {
    Bar bar();
  }

  @Module
  abstract static class BindsCycleModule {
    @Binds
    abstract Foo foo(Bar bar);
  }
}

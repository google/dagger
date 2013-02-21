/*
 * Copyright (C) 2010 Google Inc.
 * Copyright (C) 2012 Square Inc.
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
package dagger;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;
import java.util.RandomAccess;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(JUnit4.class)
public final class InjectionTest {
  @Test public void basicInjection() {
    class TestEntryPoint {
      @Inject Provider<G> gProvider;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class TestModule {
      @Provides E provideE(F f) {
        return new E(f);
      }
      @Provides F provideF() {
        return new F();
      }
    }

    TestEntryPoint entryPoint = new TestEntryPoint();
    ObjectGraph.create(new TestModule()).inject(entryPoint);
    G g = entryPoint.gProvider.get();
    assertThat(g.a).isNotNull();
    assertThat(g.b).isNotNull();
    assertThat(g.c).isNotNull();
    assertThat(g.d).isNotNull();
    assertThat(g.e).isNotNull();
    assertThat(g.e.f).isNotNull();
  }

  static class A {
    @Inject A() {}
  }

  static class B {
    @Inject B() {}
  }

  @Singleton
  static class C {
    @Inject C() {}
  }

  @Singleton
  static class D {
    @Inject D() {}
  }

  static class E {
    F f;
    E(F f) {
      this.f = f;
    }
  }

  static class F {}

  static class G {
    @Inject A a;
    @Inject B b;
    C c;
    D d;
    @Inject E e;
    @Inject G(C c, D d) {
      this.c = c;
      this.d = d;
    }
  }

  @Test public void providerInjection() {
    class TestEntryPoint {
      @Inject Provider<A> aProvider;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class TestModule {
    }

    TestEntryPoint entryPoint = new TestEntryPoint();
    ObjectGraph.create(new TestModule()).inject(entryPoint);

    assertThat(entryPoint.aProvider.get()).isNotNull();
    assertThat(entryPoint.aProvider.get()).isNotNull();
    assertThat(entryPoint.aProvider.get()).isNotSameAs(entryPoint.aProvider.get());
  }


  @Test public void singletons() {
    class TestEntryPoint {
      @Inject Provider<F> fProvider;
      @Inject Provider<I> iProvider;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class TestModule {
      @Provides @Singleton F provideF() {
        return new F();
      }
    }

    TestEntryPoint entryPoint = new TestEntryPoint();
    ObjectGraph.create(new TestModule()).inject(entryPoint);
    assertThat(entryPoint.fProvider.get()).isSameAs(entryPoint.fProvider.get());
    assertThat(entryPoint.iProvider.get()).isSameAs(entryPoint.iProvider.get());
  }

  @Singleton
  static class I {
    @Inject I() {}
  }

  @Test public void bindingAnnotations() {
    final A one = new A();
    final A two = new A();

    class TestEntryPoint {
      @Inject A a;
      @Inject @Named("one") A aOne;
      @Inject @Named("two") A aTwo;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class TestModule {
      @Provides @Named("one") A getOne() {
        return one;
      }
      @Provides @Named("two") A getTwo() {
        return two;
      }
    }

    TestEntryPoint entryPoint = new TestEntryPoint();
    ObjectGraph.create(new TestModule()).inject(entryPoint);
    assertThat(entryPoint.a).isNotNull();
    assertThat(one).isSameAs(entryPoint.aOne);
    assertThat(two).isSameAs(entryPoint.aTwo);
  }

  @Test public void singletonBindingAnnotationAndProvider() {
    class TestEntryPoint {
      @Inject Provider<L> lProvider;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class TestModule {
      A a1;
      A a2;

      @Provides @Singleton @Named("one") F provideF(Provider<A> aProvider) {
        a1 = aProvider.get();
        a2 = aProvider.get();
        return new F();
      }
    }

    TestEntryPoint entryPoint = new TestEntryPoint();
    TestModule module = new TestModule();
    ObjectGraph.create(module).inject(entryPoint);
    entryPoint.lProvider.get();

    assertThat(module.a1).isNotNull();
    assertThat(module.a2).isNotNull();
    assertThat(module.a1).isNotSameAs(module.a2);
    assertThat(entryPoint.lProvider.get()).isSameAs(entryPoint.lProvider.get());
  }

  @Singleton
  public static class L {
    @Inject @Named("one") F f;
    @Inject Provider<L> lProvider;
  }

  @Test public void singletonInGraph() {
    class TestEntryPoint {
      @Inject N n1;
      @Inject N n2;
      @Inject F f1;
      @Inject F f2;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class TestModule {
      @Provides @Singleton F provideF() {
        return new F();
      }
    }

    TestEntryPoint entryPoint = new TestEntryPoint();
    ObjectGraph.create(new TestModule()).inject(entryPoint);

    assertThat(entryPoint.f1).isSameAs(entryPoint.f2);
    assertThat(entryPoint.f1).isSameAs(entryPoint.n1.f1);
    assertThat(entryPoint.f1).isSameAs(entryPoint.n1.f2);
    assertThat(entryPoint.f1).isSameAs(entryPoint.n2.f1);
    assertThat(entryPoint.f1).isSameAs(entryPoint.n2.f2);
    assertThat(entryPoint.f1).isSameAs(entryPoint.n1.fProvider.get());
    assertThat(entryPoint.f1).isSameAs(entryPoint.n2.fProvider.get());
  }

  public static class N {
    @Inject F f1;
    @Inject F f2;
    @Inject Provider<F> fProvider;
  }

  @Test public void noJitBindingsForAnnotations() {
    class TestEntryPoint {
      @Inject @Named("a") A a;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class TestModule {
    }

    ObjectGraph graph = ObjectGraph.create(new TestModule());
    try {
      graph.validate();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void injectableSupertypes() {
    class TestEntryPoint {
      @Inject Q q;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class TestModule {
      @Provides F provideF() {
        return new F();
      }
    }

    TestEntryPoint entryPoint = new TestEntryPoint();
    ObjectGraph.create(new TestModule()).inject(entryPoint);
    assertThat(entryPoint.q.f).isNotNull();
  }

  @Test public void uninjectableSupertypes() {
    class TestEntryPoint {
      @Inject T t;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class TestModule {
    }

    TestEntryPoint entryPoint = new TestEntryPoint();
    ObjectGraph.create(new TestModule()).inject(entryPoint);
    assertThat(entryPoint.t).isNotNull();
  }

  public static class P {
    @Inject F f;
  }

  public static class Q extends P {
    @Inject Q() {}
  }

  static class S {
  }

  public static class T extends S {
    @Inject T() {}
  }

  @Test public void singletonsAreNotEager() {
    class TestEntryPoint {
      @Inject Provider<A> aProvider;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class TestModule {
      boolean sInjected = false;

      @Provides F provideF(R r) {
        return new F();
      }

      @Provides @Singleton S provideS() {
        sInjected = true;
        return new S();
      }
    }

    R.injected = false;
    TestEntryPoint entryPoint = new TestEntryPoint();
    TestModule module = new TestModule();
    ObjectGraph.create(module).inject(entryPoint);

    assertThat(R.injected).isFalse();
    assertThat(module.sInjected).isFalse();
  }

  @Singleton
  static class R {
    static boolean injected = false;
    @Inject R() {
      injected = true;
    }
  }

  @Test public void providerMethodsConflict() {
    @Module
    class TestModule {
      @Provides A provideA1() {
        throw new AssertionError();
      }
      @Provides A provideA2() {
        throw new AssertionError();
      }
    }

    try {
      ObjectGraph.create(new TestModule());
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void singletonsInjectedOnlyIntoProviders() {
    class TestEntryPoint {
      @Inject Provider<A> aProvider;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class TestModule {
      @Provides @Singleton A provideA() {
        return new A();
      }
    }

    TestEntryPoint entryPoint = new TestEntryPoint();
    ObjectGraph.create(new TestModule()).inject(entryPoint);
    assertThat(entryPoint.aProvider.get()).isSameAs(entryPoint.aProvider.get());
  }

  @Test public void moduleOverrides() {
    class TestEntryPoint {
      @Inject Provider<E> eProvider;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class BaseModule {
      @Provides F provideF() {
        throw new AssertionError();
      }
      @Provides E provideE(F f) {
        return new E(f);
      }
    }

    @Module(overrides = true)
    class OverridesModule {
      @Provides F provideF() {
        return new F();
      }
    }

    TestEntryPoint entryPoint = new TestEntryPoint();
    ObjectGraph.create(new BaseModule(), new OverridesModule()).inject(entryPoint);
    E e = entryPoint.eProvider.get();
    assertThat(e).isNotNull();
    assertThat(e.f).isNotNull();
  }

  @Test public void noJitBindingsForInterfaces() {
    class TestEntryPoint {
      @Inject RandomAccess randomAccess;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class TestModule {
    }

    ObjectGraph graph = ObjectGraph.create(new TestModule());
    try {
      graph.validate();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void noProvideBindingsForAbstractClasses() {
    class TestEntryPoint {
      @Inject AbstractList<?> abstractList;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class TestModule {
    }

    ObjectGraph graph = ObjectGraph.create(new TestModule());
    try {
      graph.validate();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  static class ExtendsParameterizedType extends AbstractList<Integer> {
    @Inject String string;
    @Override public Integer get(int i) {
      throw new AssertionError();
    }
    @Override public int size() {
      throw new AssertionError();
    }
  }

  /**
   * We've had bugs where we look for the wrong keys when a class extends a
   * parameterized class. Explicitly test that we can inject such classes.
   */
  @Test public void extendsParameterizedType() {
    class TestEntryPoint {
      @Inject ExtendsParameterizedType extendsParameterizedType;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class TestModule {
      @Provides String provideString() {
        return "injected";
      }
    }

    TestEntryPoint entryPoint = new TestEntryPoint();
    ObjectGraph.create(new TestModule()).inject(entryPoint);
    assertThat(entryPoint.extendsParameterizedType.string).isEqualTo("injected");
  }

  @Test public void injectParameterizedType() {
    class TestEntryPoint {
      @Inject List<String> listOfStrings;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class TestModule {
      @Provides List<String> provideList() {
        return Arrays.asList("a", "b");
      }
    }

    TestEntryPoint entryPoint = new TestEntryPoint();
    ObjectGraph.create(new TestModule()).inject(entryPoint);
    assertThat(entryPoint.listOfStrings).isEqualTo(Arrays.asList("a", "b"));
  }

  @Test public void injectWildcardType() {
    class TestEntryPoint {
      @Inject List<? extends Number> listOfNumbers;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class TestModule {
      @Provides List<? extends Number> provideList() {
        return Arrays.asList(1, 2);
      }
    }

    try {
      ObjectGraph.create(new TestModule());
      fail();
    } catch (UnsupportedOperationException expected) {
    }
  }

  static class Parameterized<T> {
      @Inject String string;
    }

  @Test public void noConstructorInjectionsForClassesWithTypeParameters() {

    class TestEntryPoint {
      @Inject Parameterized<Long> parameterized;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class TestModule {
      @Provides String provideString() {
        return "injected";
      }
    }

    ObjectGraph graph = ObjectGraph.create(new TestModule());
    try {
      graph.validate();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void moduleWithNoProvidesMethods() {
    @Module
    class TestModule {
    }

    ObjectGraph.create(new TestModule());
  }

  @Test public void getInstance() {
    final AtomicInteger next = new AtomicInteger(0);

    @Module(entryPoints = Integer.class)
    class TestModule {
      @Provides Integer provideInteger() {
        return next.getAndIncrement();
      }
    }

    ObjectGraph graph = ObjectGraph.create(new TestModule());
    assertEquals(0, (int) graph.get(Integer.class));
    assertEquals(1, (int) graph.get(Integer.class));
  }

  @Test public void getInstanceRequiresEntryPoint() {
    @Module
    class TestModule {
      @Provides Integer provideInteger() {
        throw new AssertionError();
      }
    }

    ObjectGraph graph = ObjectGraph.create(new TestModule());
    try {
      graph.get(Integer.class);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void getInstanceOfPrimitive() {
    @Module(entryPoints = int.class)
    class TestModule {
      @Provides int provideInt() {
        return 1;
      }
    }

    ObjectGraph graph = ObjectGraph.create(new TestModule());
    assertEquals(1, (int) graph.get(int.class));
  }

  @Test public void getInstanceOfArray() {
    @Module(entryPoints = int[].class)
    class TestModule {
      @Provides int[] provideIntArray() {
        return new int[] { 1, 2, 3 };
      }
    }

    ObjectGraph graph = ObjectGraph.create(new TestModule());
    assertEquals("[1, 2, 3]", Arrays.toString(graph.get(int[].class)));
  }

  @Test public void getInstanceAndInjectMembersUseDifferentKeys() {
    class BoundTwoWays {
      @Inject String s;
    }

    @Module(entryPoints = BoundTwoWays.class)
    class TestModule {
      @Provides
      BoundTwoWays provideBoundTwoWays() {
        BoundTwoWays result = new BoundTwoWays();
        result.s = "Pepsi";
        return result;
      }

      @Provides String provideString() {
        return "Coke";
      }
    }

    ObjectGraph graph = ObjectGraph.create(new TestModule());
    BoundTwoWays provided = graph.get(BoundTwoWays.class);
    assertEquals("Pepsi", provided.s);

    BoundTwoWays membersInjected = new BoundTwoWays();
    graph.inject(membersInjected);
    assertEquals("Coke", membersInjected.s);
  }

  static class NoInjections {
    NoInjections(Void noDefaultConstructorEither) {
    }
  }

  @Test public void entryPointNeedsNoInjectAnnotation() {
    @Module(entryPoints = NoInjections.class)
    class TestModule {
    }

    ObjectGraph.create(new TestModule()).validate();
  }

  static class InjectMembersOnly {
    InjectMembersOnly(Void noInjectableConstructor) {
    }
    @Inject String string;
  }

  @Test public void cannotGetOnMembersOnlyInjectionPoint() {
    @Module(entryPoints = InjectMembersOnly.class)
    class TestModule {
      @Provides String provideString() {
        return "injected";
      }
    }

    ObjectGraph graph = ObjectGraph.create(new TestModule());
    try {
      graph.get(InjectMembersOnly.class);
      fail();
    } catch (IllegalStateException expected) {
    }

    InjectMembersOnly instance = new InjectMembersOnly(null);
    graph.inject(instance);
    assertThat(instance.string).isEqualTo("injected");
  }

  @Test public void nonEntryPointNeedsInjectAnnotation() {
    @Module
    class TestModule {
      @Provides String provideString(NoInjections noInjections) {
        throw new AssertionError();
      }
    }

    ObjectGraph graph = ObjectGraph.create(new TestModule());
    try {
      graph.validate();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  static class TwoAtInjectConstructors {
    @Inject TwoAtInjectConstructors() {
    }
    @Inject TwoAtInjectConstructors(String s) {
    }
  }

  @Test public void twoAtInjectConstructorsIsRejected() {
    @Module(entryPoints = TwoAtInjectConstructors.class)
    class TestModule {
      @Provides String provideString() {
        throw new AssertionError();
      }
    }

    ObjectGraph graph = ObjectGraph.create(new TestModule());
    try {
      graph.validate();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void runtimeProvidesMethodsExceptionsAreNotWrapped() {
    class TestEntryPoint {
      @Inject String string;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class TestModule {
      @Provides String provideString() {
        throw new ClassCastException("foo");
      }
    }

    try {
      ObjectGraph.create(new TestModule()).inject(new TestEntryPoint());
      fail();
    } catch (ClassCastException e) {
      assertThat(e.getMessage()).isEqualTo("foo");
    }
  }

  static class ThrowsOnConstruction {
    @Inject ThrowsOnConstruction() {
      throw new ClassCastException("foo");
    }
  }

  @Test public void runtimeConstructorExceptionsAreNotWrapped() {
    @Module(entryPoints = ThrowsOnConstruction.class)
    class TestModule {
    }

    try {
      ObjectGraph.create(new TestModule()).get(ThrowsOnConstruction.class);
      fail();
    } catch (ClassCastException e) {
      assertThat(e.getMessage()).isEqualTo("foo");
    }
  }
}

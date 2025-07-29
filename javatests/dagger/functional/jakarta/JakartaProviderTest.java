/*
 * Copyright (C) 2024 The Dagger Authors.
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

package dagger.functional.jakarta;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Optional;
import dagger.Binds;
import dagger.BindsOptionalOf;
import dagger.Component;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;
import dagger.multibindings.LazyClassKey;
import dagger.multibindings.StringKey;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Qualifier;
import jakarta.inject.Scope;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class JakartaProviderTest {

  @Scope
  public @interface TestScope {}

  @Qualifier
  public @interface TestQualifier {}

  @TestScope
  @Component(modules = TestModule.class)
  interface TestComponent {
    Provider<Foo> getJakartaFoo();

    javax.inject.Provider<Foo> getJavaxFoo();

    Provider<Bar> getJakartaBar();

    javax.inject.Provider<Bar> getJavaxBar();

    @TestQualifier
    Provider<Foo> getJakartaQualifiedFoo();

    @TestQualifier
    javax.inject.Provider<Foo> getJavaxQualifiedFoo();

    @TestQualifier
    Provider<Bar> getJakartaQualifiedBar();

    @TestQualifier
    javax.inject.Provider<Bar> getJavaxQualifiedBar();

    InjectUsages injectUsages();

    Map<String, Provider<Bar>> getJakartaProviderMap();

    Map<String, javax.inject.Provider<Bar>> getJavaxProviderMap();

    Map<Class<?>, Provider<Bar>> getJakartaProviderClassMap();

    Map<Class<?>, javax.inject.Provider<Bar>> getJavaxProviderClassMap();

    Map<String, Provider<Lazy<Bar>>> getJakartaProviderLazyMap();

    Map<String, javax.inject.Provider<Lazy<Bar>>> getJavaxProviderLazyMap();

    Map<Long, Provider<Long>> getManuallyProvidedJakartaMap();

    Map<Long, javax.inject.Provider<Long>> getManuallyProvidedJavaxMap();

    Optional<Provider<String>> getPresentOptionalJakartaProvider();

    Optional<javax.inject.Provider<String>> getPresentOptionalJavaxProvider();

    Optional<Provider<Long>> getEmptyOptionalJakartaProvider();

    Optional<javax.inject.Provider<Long>> getEmptyOptionalJavaxProvider();
  }

  public static final class Foo {
    @Inject
    Foo() {}
  }

  @TestScope
  public static final class Bar {
    @Inject
    Bar() {}
  }

  // Scoped as this forces the generated code to use a Provider instead of inlining
  // in default mode
  @TestScope
  public static final class InjectUsages {
    Provider<Bar> jakartaBar;
    Provider<Bar> jakartaQualifiedBar;
    javax.inject.Provider<Bar> javaxQualifiedBar;
    Map<String, javax.inject.Provider<Bar>> javaxProviderMap;
    Map<String, Provider<Bar>> jakartaProviderMap;
    Map<Class<?>, javax.inject.Provider<Bar>> javaxProviderClassMap;
    Map<Class<?>, Provider<Bar>> jakartaProviderClassMap;

    @Inject
    InjectUsages(Provider<Bar> jakartaBar) {
      this.jakartaBar = jakartaBar;
    }

    @Inject javax.inject.Provider<Bar> javaxBar;

    @Inject
    void injectBar(
        Provider<Bar> jakartaQualifiedBar,
        javax.inject.Provider<Bar> javaxQualifiedBar,
        Map<String, javax.inject.Provider<Bar>> javaxProviderMap,
        Map<String, Provider<Bar>> jakartaProviderMap,
        Map<Class<?>, javax.inject.Provider<Bar>> javaxProviderClassMap,
        Map<Class<?>, Provider<Bar>> jakartaProviderClassMap) {
      this.jakartaQualifiedBar = jakartaQualifiedBar;
      this.javaxQualifiedBar = javaxQualifiedBar;
      this.javaxProviderMap = javaxProviderMap;
      this.jakartaProviderMap = jakartaProviderMap;
      this.javaxProviderClassMap = javaxProviderClassMap;
      this.jakartaProviderClassMap = jakartaProviderClassMap;
    }
  }

  @Module
  abstract static class TestModule {
    @Provides
    @TestQualifier
    static Foo provideFoo(
        Provider<Foo> fooProvider, javax.inject.Provider<Foo> unusedOtherFooProvider) {
      return fooProvider.get();
    }

    @Provides
    @TestQualifier
    @TestScope
    static Bar provideBar(
        Provider<Bar> unusedBarProvider, javax.inject.Provider<Bar> otherBarProvider) {
      // Use the other one in this case just to vary it from Foo
      return otherBarProvider.get();
    }

    @Binds
    @IntoMap
    @StringKey("bar")
    abstract Bar bindBarIntoMap(Bar bar);

    @Binds
    @IntoMap
    @LazyClassKey(Bar.class)
    abstract Bar bindBarIntoClassMap(Bar bar);

    // TODO(b/65118638): Use @Binds @IntoMap Lazy<T> once that works properly.
    @Provides
    @IntoMap
    @StringKey("bar")
    static Lazy<Bar> provideLazyIntoMap(Lazy<Bar> bar) {
      return bar;
    }

    // Manually provide two Provider maps to make sure they don't conflict.
    @Provides
    static Map<Long, Provider<Long>> manuallyProvidedJakartaMap() {
      Map<Long, Provider<Long>> map = new HashMap<>();
      map.put(9L, null);
      return map;
    }

    @Provides
    static Map<Long, javax.inject.Provider<Long>> manuallyProvidedJavaxMap() {
      Map<Long, javax.inject.Provider<Long>> map = new HashMap<>();
      map.put(0L, null);
      return map;
    }

    @BindsOptionalOf
    abstract String bindOptionalString();

    @Provides
    static String provideString() {
      return "present";
    }

    @BindsOptionalOf
    abstract Long bindOptionalLong();
  }

  @Test
  public void testJakartaProviders() {
    TestComponent testComponent = DaggerJakartaProviderTest_TestComponent.create();

    assertThat(testComponent.getJakartaFoo().get()).isNotNull();
    assertThat(testComponent.getJavaxFoo().get()).isNotNull();

    assertThat(testComponent.getJakartaBar().get())
        .isSameInstanceAs(testComponent.getJavaxBar().get());

    assertThat(testComponent.getJakartaQualifiedFoo().get()).isNotNull();
    assertThat(testComponent.getJavaxQualifiedFoo().get()).isNotNull();

    assertThat(testComponent.getJakartaQualifiedBar().get())
        .isSameInstanceAs(testComponent.getJavaxQualifiedBar().get());
    assertThat(testComponent.getJakartaBar().get())
        .isSameInstanceAs(testComponent.getJakartaQualifiedBar().get());

    InjectUsages injectUsages = testComponent.injectUsages();

    assertThat(injectUsages.jakartaBar.get()).isSameInstanceAs(injectUsages.javaxBar.get());
    assertThat(injectUsages.jakartaQualifiedBar.get())
        .isSameInstanceAs(injectUsages.javaxQualifiedBar.get());
    assertThat(injectUsages.jakartaBar.get())
        .isSameInstanceAs(injectUsages.jakartaQualifiedBar.get());

    assertThat(testComponent.getJakartaProviderMap().get("bar").get()).isSameInstanceAs(
        testComponent.getJavaxProviderMap().get("bar").get());

    assertThat(testComponent.getJakartaProviderClassMap().get(Bar.class).get()).isSameInstanceAs(
        testComponent.getJavaxProviderClassMap().get(Bar.class).get());

    assertThat(testComponent.getJakartaProviderLazyMap().get("bar").get().get()).isSameInstanceAs(
        testComponent.getJavaxProviderLazyMap().get("bar").get().get());

    assertThat(injectUsages.jakartaProviderMap.get("bar").get()).isSameInstanceAs(
        injectUsages.javaxProviderMap.get("bar").get());

    assertThat(injectUsages.jakartaProviderClassMap.get(Bar.class).get()).isSameInstanceAs(
        injectUsages.javaxProviderClassMap.get(Bar.class).get());

    Map<Long, Provider<Long>> manualJakartaMap = testComponent.getManuallyProvidedJakartaMap();
    assertThat(manualJakartaMap.keySet()).containsExactly(9L);

    Map<Long, javax.inject.Provider<Long>> manualJavaxMap =
        testComponent.getManuallyProvidedJavaxMap();
    assertThat(manualJavaxMap.keySet()).containsExactly(0L);

    assertThat(testComponent.getPresentOptionalJakartaProvider().get().get()).isEqualTo("present");
    assertThat(testComponent.getPresentOptionalJavaxProvider().get().get()).isEqualTo("present");
    assertThat(testComponent.getEmptyOptionalJakartaProvider().isPresent()).isFalse();
    assertThat(testComponent.getEmptyOptionalJavaxProvider().isPresent()).isFalse();
  }
}

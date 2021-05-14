/*
 * Copyright (C) 2021 The Dagger Authors.
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
import static com.google.common.truth.Truth8.assertThat;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dagger.Binds;
import dagger.BindsInstance;
import dagger.BindsOptionalOf;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.multibindings.IntoSet;
import dagger.multibindings.Multibinds;
import java.lang.annotation.Retention;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Qualifier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests that qualified assisted types can be provided and injected as normal types. */
@RunWith(JUnit4.class)
public final class AssistedFactoryAsQualifiedBindingTest {

  @Qualifier
  @Retention(RUNTIME)
  @interface AsComponentDependency {}

  @Qualifier
  @Retention(RUNTIME)
  @interface AsProvides {}

  @Qualifier
  @Retention(RUNTIME)
  @interface AsBinds {}

  @Qualifier
  @Retention(RUNTIME)
  @interface AsOptional {}

  @Qualifier
  @Retention(RUNTIME)
  @interface AsMultibinding {}

  @Component(modules = BarFactoryModule.class)
  interface TestComponent {
    Foo foo();

    @Component.Factory
    interface Factory {
      TestComponent create(
          @BindsInstance @AsComponentDependency Bar bar,
          @BindsInstance @AsComponentDependency BarFactory barFactory);
    }
  }

  @Module
  interface BarFactoryModule {

    @Provides
    @AsProvides
    static Bar providesBar(@AsComponentDependency Bar bar) {
      return bar;
    }

    @Provides
    @AsProvides
    static BarFactory providesBarFactory(@AsComponentDependency BarFactory barFactory) {
      return barFactory;
    }

    @Binds
    @AsBinds
    Bar bindsBar(@AsComponentDependency Bar bar);

    @Binds
    @AsBinds
    BarFactory bindsBarFactory(@AsComponentDependency BarFactory barFactory);

    @BindsOptionalOf
    @AsOptional
    Bar optionalBar();

    @BindsOptionalOf
    @AsOptional
    BarFactory optionalBarFactory();

    @Provides
    @AsOptional
    static Bar providesOptionalBar(@AsComponentDependency Bar bar) {
      return bar;
    }

    @Provides
    @AsOptional
    static BarFactory providesOptionalBarFactory(@AsComponentDependency BarFactory barFactory) {
      return barFactory;
    }

    @Multibinds
    @AsMultibinding
    Set<Bar> barSet();

    @Multibinds
    @AsMultibinding
    Set<BarFactory> barFactorySet();

    @Provides
    @IntoSet
    @AsMultibinding
    static Bar providesMultibindingBar(@AsComponentDependency Bar bar) {
      return bar;
    }

    @Provides
    @IntoSet
    @AsMultibinding
    static BarFactory providesMultibindingBarFactory(@AsComponentDependency BarFactory barFactory) {
      return barFactory;
    }

    @Multibinds
    Set<Bar> unqualifiedBarSet();

    @Multibinds
    Set<BarFactory> unqualifiedBarFactorySet();

    @Provides
    @IntoSet
    static Bar providesUnqualifiedMultibindingBar(@AsComponentDependency Bar bar) {
      return bar;
    }

    @Provides
    @IntoSet
    static BarFactory providesUnqualifiedMultibindingBarFactory(
        @AsComponentDependency BarFactory barFactory) {
      return barFactory;
    }
  }

  static class Foo {
    private final BarFactory barFactory;
    private final Bar barAsComponentDependency;
    private final BarFactory barFactoryAsComponentDependency;
    private final Bar barAsProvides;
    private final BarFactory barFactoryAsProvides;
    private final Bar barAsBinds;
    private final BarFactory barFactoryAsBinds;
    private final Optional<Bar> optionalBar;
    private final Optional<BarFactory> optionalBarFactory;
    private final Set<Bar> barSet;
    private final Set<BarFactory> barFactorySet;
    private final Set<Bar> unqualifiedBarSet;
    private final Set<BarFactory> unqualifiedBarFactorySet;

    @Inject
    Foo(
        BarFactory barFactory,
        @AsComponentDependency Bar barAsComponentDependency,
        @AsComponentDependency BarFactory barFactoryAsComponentDependency,
        @AsProvides Bar barAsProvides,
        @AsProvides BarFactory barFactoryAsProvides,
        @AsBinds Bar barAsBinds,
        @AsBinds BarFactory barFactoryAsBinds,
        @AsOptional Optional<Bar> optionalBar,
        @AsOptional Optional<BarFactory> optionalBarFactory,
        @AsMultibinding Set<Bar> barSet,
        @AsMultibinding Set<BarFactory> barFactorySet,
        Set<Bar> unqualifiedBarSet,
        Set<BarFactory> unqualifiedBarFactorySet) {
      this.barFactory = barFactory;
      this.barAsComponentDependency = barAsComponentDependency;
      this.barFactoryAsComponentDependency = barFactoryAsComponentDependency;
      this.barAsProvides = barAsProvides;
      this.barFactoryAsProvides = barFactoryAsProvides;
      this.barAsBinds = barAsBinds;
      this.barFactoryAsBinds = barFactoryAsBinds;
      this.optionalBar = optionalBar;
      this.optionalBarFactory = optionalBarFactory;
      this.barSet = barSet;
      this.barFactorySet = barFactorySet;
      this.unqualifiedBarSet = unqualifiedBarSet;
      this.unqualifiedBarFactorySet = unqualifiedBarFactorySet;
    }
  }

  static class Bar {
    @AssistedInject
    Bar() {}
  }

  @AssistedFactory
  interface BarFactory {
    Bar create();
  }

  @Test
  public void testFoo() {
    Bar bar = new Bar();
    BarFactory barFactory = () -> bar;
    Foo foo =
        DaggerAssistedFactoryAsQualifiedBindingTest_TestComponent.factory()
            .create(bar, barFactory)
            .foo();

    // Test we can inject the "real" BarFactory implemented by Dagger
    assertThat(foo.barFactory).isNotNull();
    assertThat(foo.barFactory).isNotEqualTo(barFactory);
    assertThat(foo.barFactory.create()).isNotEqualTo(bar);

    // Test injection of a qualified Bar/BarFactory with custom @BindsInstance implementation
    assertThat(foo.barAsComponentDependency).isEqualTo(bar);
    assertThat(foo.barFactoryAsComponentDependency).isEqualTo(barFactory);

    // Test injection of a qualified Bar/BarFactory with custom @Provides implementation
    assertThat(foo.barAsProvides).isEqualTo(bar);
    assertThat(foo.barFactoryAsProvides).isEqualTo(barFactory);

    // Test injection of a qualified Bar/BarFactory with custom @Binds implementation
    assertThat(foo.barAsBinds).isEqualTo(bar);
    assertThat(foo.barFactoryAsBinds).isEqualTo(barFactory);

    // Test injection of a qualified Bar/BarFactory with custom @BindsOptionalOf implementation
    assertThat(foo.optionalBar).isPresent();
    assertThat(foo.optionalBar).hasValue(bar);
    assertThat(foo.optionalBarFactory).isPresent();
    assertThat(foo.optionalBarFactory).hasValue(barFactory);

    // Test injection of a qualified Bar/BarFactory as multibinding
    assertThat(foo.barSet).containsExactly(bar);
    assertThat(foo.barFactorySet).containsExactly(barFactory);

    // Test injection of a unqualified Bar/BarFactory as multibinding
    assertThat(foo.unqualifiedBarSet).containsExactly(bar);
    assertThat(foo.unqualifiedBarFactorySet).containsExactly(barFactory);
  }
}

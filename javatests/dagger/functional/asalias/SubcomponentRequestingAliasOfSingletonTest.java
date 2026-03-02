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

package dagger.functional.asalias;

import static com.google.common.truth.Truth.assertThat;

import dagger.AsAlias;
import dagger.Binds;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.Subcomponent;
import dagger.multibindings.IntoSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SubcomponentRequestingAliasOfSingletonTest {
  interface X {}

  interface Foo {
    Set<X> getXs();
  }

  @Singleton
  static final class FooImpl implements Foo {
    final Set<X> xs;

    @Inject
    FooImpl(Set<X> xs) {
      this.xs = xs;
    }

    @Override
    public Set<X> getXs() {
      return xs;
    }
  }

  @Module
  abstract static class ParentModule {
    @Provides
    @IntoSet
    static X provideX1() {
      return new X() {};
    }

    @AsAlias
    @Binds
    abstract Foo bindFoo(FooImpl fooImpl);
  }

  @Module
  interface SubcomponentModule {
    @Provides
    @IntoSet
    static X provideX2() {
      return new X() {};
    }
  }

  @Singleton
  @Component(modules = {ParentModule.class})
  interface ParentComponent {
    Foo getFoo();

    ChildComponent childComponent();
  }

  @Subcomponent(modules = {SubcomponentModule.class})
  interface ChildComponent {
    Foo getFoo();

    Set<X> getXs();
  }

  @Test
  public void subcomponentRequestingAliasOfSingletonWithSetDependency() {
    ParentComponent parentComponent =
        DaggerSubcomponentRequestingAliasOfSingletonTest_ParentComponent.create();
    ChildComponent subcomponent = parentComponent.childComponent();
    assertThat(parentComponent.getFoo().getXs()).hasSize(1);
    // Even though ChildComponent contributes to Set<X>, Bar is @Singleton so it gets
    // constructed with Set<X> from ParentComponent once.
    assertThat(subcomponent.getFoo().getXs()).hasSize(1);
    // Requesting Set<X> directly from ChildComponent should include contributions from
    // ChildComponent, so it has size 2.
    assertThat(subcomponent.getXs()).hasSize(2);
  }
}

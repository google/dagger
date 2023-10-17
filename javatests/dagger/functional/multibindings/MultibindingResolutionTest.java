/*
 * Copyright (C) 2023 The Dagger Authors.
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

package dagger.functional.multibindings;

import static com.google.common.truth.Truth.assertThat;

import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.Subcomponent;
import dagger.multibindings.IntoSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

// This is a regression test for b/290683637.
@RunWith(JUnit4.class)
public final class MultibindingResolutionTest {
  @Component(modules = ParentModule.class)
  interface ParentComponent {
    A getA();

    Child1Component getChild1Component();

    Child2Component getChild2Component();
  }

  @Module
  interface ParentModule {
    @Provides
    @IntoSet
    static X provideX() {
      return new ParentX();
    }
  }

  @Subcomponent(modules = ChildModule.class)
  interface Child1Component {
    B getB();
    A getA();
  }

  // Technically, the original bug only occurred when the entry points were declared in the order
  // B then A, as in Child1Component. However, since that depends on the order we resolved entry
  // points (an implementation detail that can change) we create another component with the opposite
  // order.
  @Subcomponent(modules = ChildModule.class)
  interface Child2Component {
    A getA();
    B getB();
  }

  @Module
  interface ChildModule {
    @Provides
    @IntoSet
    static X provideX() {
      return new ChildX();
    }
  }

  interface X {}

  static final class ParentX implements X {}

  static final class ChildX implements X {}

  static final class A {
    final B b;
    final C c;

    @Inject
    A(B b, C c) {
      this.b = b;
      this.c = c;
    }
  }

  static final class B {
    final Provider<A> aProvider;

    @Inject
    B(Provider<A> aProvider) {
      this.aProvider = aProvider;
    }
  }

  static final class C {
    final Set<X> multibindingContributions;

    @Inject
    C(Set<X> multibindingContributions) {
      this.multibindingContributions = multibindingContributions;
    }
  }

  private ParentComponent parentComponent;

  @Before
  public void setup() {
    parentComponent = DaggerMultibindingResolutionTest_ParentComponent.create();
  }

  @Test
  public void testParent() {
    assertThat(parentComponent.getA().c.multibindingContributions).hasSize(1);
  }

  @Test
  public void testChild1() {
    Child1Component child1Component = parentComponent.getChild1Component();
    assertThat(child1Component.getB().aProvider.get().c.multibindingContributions).hasSize(2);
  }

  @Test
  public void testChild2() {
    Child2Component child2Component = parentComponent.getChild2Component();
    assertThat(child2Component.getB().aProvider.get().c.multibindingContributions).hasSize(2);
  }
}

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

import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.Subcomponent;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.multibindings.IntoSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AssistedFactoryWithMultibindingsTest {
  @Component(modules = ParentModule.class)
  interface ParentComponent {
    // Factory for assisted injection binding with multi binding contribution.
    MultibindingFooFactory multibindingFooFactory();

    ChildComponent.Builder childComponent();
  }

  static final class AssistedDep {}

  static final class MultibindingFoo {
    private final AssistedDep assistedDep;
    private final Set<String> stringSet;

    @AssistedInject
    MultibindingFoo(@Assisted AssistedDep assistedDep, Set<String> stringSet) {
      this.assistedDep = assistedDep;
      this.stringSet = stringSet;
    }

    AssistedDep assistedDep() {
      return assistedDep;
    }

    Set<String> stringSet() {
      return stringSet;
    }
  }

  @Subcomponent(modules = ChildModule.class)
  static interface ChildComponent {
    MultibindingFooFactory multibindingFooFactory();

    @Subcomponent.Builder
    interface Builder {
      ChildComponent build();
    }
  }

  @Module(subcomponents = ChildComponent.class)
  static class ParentModule {
    @Provides
    @IntoSet
    String parentString() {
      return "parent";
    }
  }

  @Module
  static class ChildModule {
    @Provides
    @IntoSet
    String childString() {
      return "child";
    }
  }

  @AssistedFactory
  interface MultibindingFooFactory {
    MultibindingFoo createFoo(AssistedDep factoryAssistedDep1);
  }

  @Test
  public void testAssistedFactoryWithMultibinding() {
    AssistedDep assistedDep1 = new AssistedDep();
    ParentComponent parent = DaggerAssistedFactoryWithMultibindingsTest_ParentComponent.create();
    ChildComponent child = parent.childComponent().build();
    MultibindingFoo foo1 = parent.multibindingFooFactory().createFoo(assistedDep1);
    MultibindingFoo foo2 = child.multibindingFooFactory().createFoo(assistedDep1);
    assertThat(foo1.assistedDep()).isEqualTo(foo2.assistedDep);
    assertThat(foo1.stringSet()).containsExactly("parent");
    assertThat(foo2.stringSet()).containsExactly("child", "parent");
  }
}

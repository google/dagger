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
import dagger.Subcomponent;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ChainOfAsAliasTest {
  interface A {}

  interface B extends A {}

  interface C extends B {}

  static class CImpl implements C {
    @Inject
    CImpl() {}
  }

  @Module
  interface ChainModule {
    @Binds
    @AsAlias
    A bindA(B b);

    @Binds
    @AsAlias
    B bindB(C c);

    @Binds
    @AsAlias
    C bindC(CImpl cImpl);
  }

  @Singleton
  @Component(modules = {ChainModule.class})
  interface ParentComponent {
    ChildComponent childComponent();
  }

  @Subcomponent
  interface ChildComponent {
    A getA();
  }

  @Test
  public void chainOfAsAlias() {
    ParentComponent component = DaggerChainOfAsAliasTest_ParentComponent.create();
    ChildComponent subcomponent = component.childComponent();
    assertThat(subcomponent.getA()).isNotNull();
    assertThat(subcomponent.getA()).isInstanceOf(CImpl.class);
  }
}

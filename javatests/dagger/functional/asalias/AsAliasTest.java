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
public final class AsAliasTest {
  interface Iface {}

  static class IfaceImpl implements Iface {
    @Inject
    IfaceImpl() {}
  }

  @Module
  interface IfaceModule {
    @Binds
    @AsAlias
    Iface bind(IfaceImpl impl);
  }

  @Singleton
  @Component(modules = {IfaceModule.class})
  interface ParentComponent {
    ChildComponent childComponent();
  }

  @Subcomponent
  interface ChildComponent {
    Iface getIface();
  }

  @Test
  public void asAliasInParentComponentWithImplDepsInChildComponent_bindsIfaceImpl() {
    ParentComponent component = DaggerAsAliasTest_ParentComponent.create();
    ChildComponent subcomponent = component.childComponent();
    assertThat(subcomponent.getIface()).isNotNull();
    assertThat(subcomponent.getIface()).isInstanceOf(IfaceImpl.class);
  }
}

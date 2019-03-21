/*
 * Copyright (C) 2019 The Dagger Authors.
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

package dagger.functional.aot;

import static com.google.common.truth.Truth.assertThat;

import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.Subcomponent;
import dagger.multibindings.IntoMap;
import dagger.multibindings.StringKey;
import java.util.Map;
import javax.inject.Provider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests that framework instances of map bindings are properly instantiated in ahead-of-time mode
 * when contributions are made in 3 or more implementations.
 */
@RunWith(JUnit4.class)
public final class MapFrameworkInstanceWithContributionsInMultipleImplementationsTest {
  @Subcomponent(modules = LeafModule.class)
  interface Leaf {
    Provider<Map<String, String>> providerOfMapOfValues();
    Provider<Map<String, Provider<String>>> providerOfMapOfProviders();
  }

  @Module
  interface LeafModule {
    @Provides
    @IntoMap
    @StringKey("a")
    static String fromLeaf() {
      return "a";
    }
  }

  @Subcomponent(modules = AncestorModule.class)
  interface Ancestor {
    Leaf leaf();
  }

  @Module
  interface AncestorModule {
    @Provides
    @IntoMap
    @StringKey("b")
    static String fromAncestor() {
      return "b";
    }
  }

  @Component(modules = RootModule.class)
  interface Root {
    Ancestor ancestor();
  }

  @Module
  interface RootModule {
    @Provides
    @IntoMap
    @StringKey("c")
    static String fromRoot() {
      return "c";
    }
  }

  @Test
  public void mapFactoryCanBeInstantiatedAcrossComponentImplementations() {
    Leaf leaf =
        DaggerMapFrameworkInstanceWithContributionsInMultipleImplementationsTest_Root.create()
            .ancestor()
            .leaf();
    assertThat(leaf.providerOfMapOfValues().get()).hasSize(3);
    assertThat(leaf.providerOfMapOfProviders().get()).hasSize(3);
  }
}

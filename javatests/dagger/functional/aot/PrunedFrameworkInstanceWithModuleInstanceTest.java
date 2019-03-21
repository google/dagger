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

import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.Subcomponent;
import javax.inject.Inject;
import javax.inject.Provider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PrunedFrameworkInstanceWithModuleInstanceTest {
  static class Pruned {}

  static class InjectsPruned {
    @Inject
    InjectsPruned(Provider<Pruned> pruned) {}
  }

  @Module
  static class InstanceStateModule {
    @Provides
    /* intentionally not static */ Pruned pruned() {
      return new Pruned();
    }
  }

  @Subcomponent(modules = InstanceStateModule.class)
  interface LeafWithoutCreator {
    InjectsPruned injectsPruned();
  }

  @Subcomponent(modules = InstanceStateModule.class)
  interface LeafWithCreator {
    InjectsPruned injectsPruned();

    @Subcomponent.Builder
    interface Builder {
      Builder module(InstanceStateModule module);
      LeafWithCreator build();
    }
  }

  @Module
  interface RootModule {
    @Provides
    static InjectsPruned pruneBindingWithInstanceState() {
      return new InjectsPruned(null);
    }
  }

  @Component(modules = RootModule.class)
  interface Root {
    LeafWithoutCreator leafWithoutCreator(InstanceStateModule pruned);
    LeafWithCreator.Builder leafWithCreator();
  }

  @Test
  public void prunedBindingWithModuleInstance_doesntThrowDuringInitialization() {
    Root root = DaggerPrunedFrameworkInstanceWithModuleInstanceTest_Root.create();

    Object unused = root.leafWithoutCreator(new InstanceStateModule()).injectsPruned();
    unused = root.leafWithCreator().module(new InstanceStateModule()).build().injectsPruned();
  }
}

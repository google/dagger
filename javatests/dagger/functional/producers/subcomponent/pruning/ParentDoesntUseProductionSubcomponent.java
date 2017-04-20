/*
 * Copyright (C) 2016 The Dagger Authors.
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

package dagger.functional.producers.subcomponent.pruning;

import com.google.common.util.concurrent.ListenableFuture;
import dagger.multibindings.IntoSet;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import dagger.producers.ProductionComponent;
import dagger.producers.ProductionSubcomponent;
import java.util.Set;
import javax.inject.Qualifier;

/**
 * Supporting types for {@code ProductionSubcomponentOnlyRequestedBySiblingTest}. {@link ChildA} is
 * a direct child of the top level component, but is only requested within its sibling, not directly
 * from its parent.
 */
@ProductionComponent(
  modules = {
    ParentDoesntUseProductionSubcomponent.ParentModule.class,
    dagger.functional.producers.ExecutorModule.class
  }
)
interface ParentDoesntUseProductionSubcomponent {

  ChildB.Builder childBBuilder();

  @ProductionSubcomponent(modules = ChildAModule.class)
  interface ChildA {
    @ProductionSubcomponent.Builder
    interface Builder {
      ChildA build();
    }

    ListenableFuture<Set<Class<?>>> componentHierarchy();
  }

  @ProductionSubcomponent(modules = ChildBModule.class)
  interface ChildB {
    @ProductionSubcomponent.Builder
    interface Builder {
      ChildB build();
    }

    ListenableFuture<Set<Class<?>>> componentHierarchy();

    @FromChildA
    ListenableFuture<Set<Class<?>>> componentHierarchyFromChildA();
  }

  @ProducerModule(subcomponents = {ChildA.class, ChildB.class})
  class ParentModule {
    @Produces
    @IntoSet
    static Class<?> produceComponentType() {
      return ParentDoesntUseProductionSubcomponent.class;
    }
  }

  @ProducerModule
  class ChildAModule {
    @Produces
    @IntoSet
    static Class<?> produceComponentType() {
      return ChildA.class;
    }
  }

  @ProducerModule
  class ChildBModule {
    @Produces
    @IntoSet
    static Class<?> produceComponentType() {
      return ChildB.class;
    }

    @Produces
    @FromChildA
    Set<Class<?>> fromChildA(ChildA.Builder childABuilder) throws Exception {
      return childABuilder.build().componentHierarchy().get();
    }
  }

  @Qualifier
  @interface FromChildA {}
}

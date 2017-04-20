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

package dagger.functional.subcomponent.pruning;

import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.Subcomponent;
import dagger.multibindings.IntoSet;
import java.util.Set;
import javax.inject.Qualifier;

/**
 * Supporting types for {@link SubcomponentOnlyRequestedBySiblingTest}. {@link ChildA} is a direct
 * child of the top level component, but is only requested within its sibling, not directly from its
 * parent.
 */
@Component(modules = ParentDoesntUseSubcomponent.ParentModule.class)
interface ParentDoesntUseSubcomponent {

  ChildB.Builder childBBuilder();

  @Subcomponent(modules = ChildAModule.class)
  interface ChildA {
    @Subcomponent.Builder
    interface Builder {
      ChildA build();
    }

    Set<Class<?>> componentHierarchy();
  }

  @Subcomponent(modules = ChildBModule.class)
  interface ChildB {
    @Subcomponent.Builder
    interface Builder {
      ChildB build();
    }

    Set<Class<?>> componentHierarchy();

    @FromChildA
    Set<Class<?>> componentHierarchyFromChildA();
  }

  @Module(subcomponents = {ChildA.class, ChildB.class})
  class ParentModule {
    @Provides
    @IntoSet
    static Class<?> provideComponentType() {
      return ParentDoesntUseSubcomponent.class;
    }
  }

  @Module
  class ChildAModule {
    @Provides
    @IntoSet
    static Class<?> provideComponentType() {
      return ChildA.class;
    }
  }

  @Module
  class ChildBModule {
    @Provides
    @IntoSet
    static Class<?> provideComponentType() {
      return ChildB.class;
    }

    @Provides
    @FromChildA
    Set<Class<?>> fromChildA(ChildA.Builder childABuilder) {
      return childABuilder.build().componentHierarchy();
    }
  }

  @Qualifier
  @interface FromChildA {}
}

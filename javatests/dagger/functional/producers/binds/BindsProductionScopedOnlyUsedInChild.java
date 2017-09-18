/*
 * Copyright (C) 2017 The Dagger Authors.
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

package dagger.functional.producers.binds;

import dagger.Binds;
import dagger.BindsInstance;
import dagger.Module;
import dagger.Provides;
import dagger.producers.ProductionComponent;
import dagger.producers.ProductionScope;
import dagger.producers.ProductionSubcomponent;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Qualifier;

final class BindsProductionScopedOnlyUsedInChild {
  interface Scoped {}

  static class Unscoped implements Scoped {}

  @Module
  abstract static class ParentModule {
    @Provides
    static Unscoped unscoped(AtomicInteger counter) {
      counter.incrementAndGet();
      return new Unscoped();
    }

    @Binds
    @ProductionScope
    abstract Scoped to(Unscoped unscoped);
  }

  @ProductionComponent(modules = ParentModule.class)
  interface Parent {
    Child child();

    @ProductionComponent.Builder
    interface Builder {
      @BindsInstance
      Builder counter(AtomicInteger atomicInteger);

      Parent build();
    }
  }

  @Module
  abstract static class ChildModule {
    @Binds
    @ProductionScope
    @InChild
    abstract Scoped to(Unscoped unscoped);
  }

  @ProductionSubcomponent(modules = ChildModule.class)
  interface Child {
    Scoped scopedInParent();
    @InChild Scoped scopedInChild();
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Qualifier
  @interface InChild {}
}

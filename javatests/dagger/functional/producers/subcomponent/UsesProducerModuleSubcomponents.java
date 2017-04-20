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

package dagger.functional.producers.subcomponent;

import com.google.common.util.concurrent.ListenableFuture;
import dagger.functional.producers.ExecutorModule;
import dagger.multibindings.IntoSet;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import dagger.producers.ProductionComponent;
import dagger.producers.ProductionSubcomponent;
import java.util.Set;
import javax.inject.Qualifier;

/** Supporting types for {@code ProducerModuleWithSubcomponentsTest}. */
@ProductionComponent(
  modules = UsesProducerModuleSubcomponents.ProducerModuleWithSubcomponents.class
)
public interface UsesProducerModuleSubcomponents {

  ListenableFuture<Set<String>> strings();

  @FromChild
  ListenableFuture<Set<String>> stringsFromChild();

  @ProducerModule(
    subcomponents = Child.class,
    includes = {AlsoIncludesSubcomponents.class, ExecutorModule.class}
  )
  class ProducerModuleWithSubcomponents {
    @Produces
    @IntoSet
    static String produceStringInParent() {
      return "from parent";
    }

    @Produces
    @FromChild
    static Set<String> stringsFromChild(Child.Builder childBuilder) throws Exception {
      return childBuilder.build().strings().get();
    }
  }

  @ProducerModule(subcomponents = Child.class)
  class AlsoIncludesSubcomponents {}

  @ProductionSubcomponent(modules = ChildModule.class)
  interface Child {
    ListenableFuture<Set<String>> strings();

    @ProductionSubcomponent.Builder
    interface Builder {
      Child build();
    }
  }

  @ProducerModule
  class ChildModule {
    @Produces
    @IntoSet
    static String produceStringInChild() {
      return "from child";
    }
  }

  @Qualifier
  @interface FromChild {}

  @ProducerModule(includes = ProducerModuleWithSubcomponents.class)
  class OnlyIncludesProducerModuleWithSubcomponents {}

  @ProductionComponent(modules = OnlyIncludesProducerModuleWithSubcomponents.class)
  interface ParentIncludesProductionSubcomponentTransitively
      extends UsesProducerModuleSubcomponents {}
}

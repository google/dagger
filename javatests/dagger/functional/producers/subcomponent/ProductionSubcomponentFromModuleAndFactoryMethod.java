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

import dagger.Module;
import dagger.Subcomponent;
import dagger.functional.producers.ExecutorModule;
import dagger.producers.ProducerModule;
import dagger.producers.ProductionComponent;
import dagger.producers.ProductionSubcomponent;

/**
 * Tests for {@link Subcomponent}s which are defined with {@link Module#subcomponents()} and are
 * also requested as component factory methods.
 */
public class ProductionSubcomponentFromModuleAndFactoryMethod {
  @ProductionSubcomponent
  interface Sub {
    @ProductionSubcomponent.Builder
    interface Builder {
      Sub sub();
    }
  }

  @ProducerModule(subcomponents = Sub.class)
  static class ModuleWithSubcomponent {}

  @ProductionComponent(modules = {ModuleWithSubcomponent.class, ExecutorModule.class})
  interface ExposesBuilder {
    Sub.Builder subcomponentBuilder();
  }
}

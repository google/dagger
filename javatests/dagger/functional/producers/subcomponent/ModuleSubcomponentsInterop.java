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

import dagger.Component;
import dagger.Module;
import dagger.Subcomponent;
import dagger.producers.ProducerModule;
import dagger.producers.ProductionComponent;
import dagger.producers.ProductionSubcomponent;

final class ModuleSubcomponentsInterop {
  @Component(modules = ProvisionTestModule.class)
  interface ProvisionParent {
    ProductionChild.Builder productionChild();
  }

  @Module(subcomponents = ProductionChild.class)
  static class ProvisionTestModule {}

  @ProductionSubcomponent
  interface ProductionChild {
    @ProductionSubcomponent.Builder
    interface Builder {
      ProductionChild build();
    }
  }

  @ProductionComponent(modules = ProductionTestModule.class)
  interface ProductionParent {
    ProvisionChild.Builder provisionBuilder();
  }

  @ProducerModule(subcomponents = ProvisionChild.class)
  static class ProductionTestModule {}

  @Subcomponent
  interface ProvisionChild {
    @Subcomponent.Builder
    interface Builder {
      ProvisionChild build();
    }
  }
}

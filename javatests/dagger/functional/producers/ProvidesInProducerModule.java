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

package dagger.functional.producers;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import dagger.Provides;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import dagger.producers.Production;
import dagger.producers.ProductionComponent;
import java.util.concurrent.Executor;

final class ProvidesInProducerModule {
  @ProducerModule
  static class OnlyModule {
    @Provides
    @Production
    static Executor provideExecutor() {
      return MoreExecutors.directExecutor();
    }

    @Produces
    static String produceString() {
      return "produced";
    }
  }

  @ProductionComponent(modules = OnlyModule.class)
  interface C {
    ListenableFuture<String> string();
  }
}

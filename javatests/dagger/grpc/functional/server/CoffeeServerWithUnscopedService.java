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

package dagger.grpc.functional.server;

import dagger.Binds;
import dagger.Component;
import dagger.Module;
import dagger.grpc.functional.server.CoffeeServerWithUnscopedService.UnscopedServiceModule;
import dagger.grpc.functional.server.CountingInterceptor.CountingInterceptorModule;
import dagger.grpc.server.InProcessServerModule;
import javax.inject.Singleton;

@Singleton
@Component(
  modules = {
    InProcessServerModule.class,
    UnscopedServiceModule.class,
    CountingInterceptorModule.class
  }
)
abstract class CoffeeServerWithUnscopedService extends CoffeeServer<CoffeeServerWithUnscopedService>
    implements FriendlyBaristaServiceDefinition {

  @Component.Builder
  interface Builder extends CoffeeServer.Builder<CoffeeServerWithUnscopedService> {}

  @Module(includes = FriendlyBaristaUnscopedGrpcServiceModule.class)
  abstract static class UnscopedServiceModule {
    @Binds
    abstract FriendlyBaristaServiceDefinition friendlyBaristaServiceDefinition(
        CoffeeServerWithUnscopedService testServer);
  }
}

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

import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.Subcomponent;
import dagger.grpc.functional.server.CoffeeServerWithCallScopeService.CallScopeServiceModule;
import dagger.grpc.functional.server.CountingInterceptor.CountingInterceptorModule;
import dagger.grpc.server.CallScoped;
import dagger.grpc.server.GrpcCallMetadataModule;
import dagger.grpc.server.InProcessServerModule;
import javax.inject.Singleton;

@Singleton
@Component(modules = {InProcessServerModule.class, CallScopeServiceModule.class})
abstract class CoffeeServerWithCallScopeService
    extends CoffeeServer<CoffeeServerWithCallScopeService> {

  @Component.Builder
  interface Builder extends CoffeeServer.Builder<CoffeeServerWithCallScopeService> {}

  abstract BaristaCallScope baristaCallScope(GrpcCallMetadataModule callMetadataModule);

  @CallScoped
  @Subcomponent(
    modules = {
      GrpcCallMetadataModule.class,
      FriendlyBaristaGrpcServiceModule.class,
      CountingInterceptorModule.class
    }
  )
  interface BaristaCallScope extends FriendlyBaristaServiceDefinition {}

  @Module(includes = FriendlyBaristaGrpcProxyModule.class)
  static class CallScopeServiceModule {
    @Provides
    static FriendlyBaristaServiceDefinition.Factory friendlyBaristaServiceDefinitionFactory(
        final CoffeeServerWithCallScopeService testServer) {
      return new FriendlyBaristaServiceDefinition.Factory() {
        @Override
        public FriendlyBaristaServiceDefinition grpcService(
            GrpcCallMetadataModule grpcCallMetadataModule) {
          return testServer.baristaCallScope(grpcCallMetadataModule);
        }
      };
    }
  }
}

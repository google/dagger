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

import dagger.grpc.server.InProcessServerModule;
import io.grpc.Server;
import java.io.IOException;

abstract class CoffeeServer<T extends CoffeeServer<T>> {
  protected abstract Server server();

  public void start() throws IOException {
    server().start();
  }

  public void shutdown() {
    server().shutdownNow();
  }

  abstract CountingInterceptor countingInterceptor();

  interface Builder<T extends CoffeeServer<T>> {
    Builder<T> inProcessServerModule(InProcessServerModule serverModule);

    T build();
  }
}

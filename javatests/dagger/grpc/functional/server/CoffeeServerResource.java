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
import java.io.IOException;
import org.junit.rules.ExternalResource;

final class CoffeeServerResource extends ExternalResource {
  private final String name;
  private final CoffeeServer<?> coffeeServer;

  CoffeeServerResource(String name, CoffeeServer.Builder<?> coffeeServerBuilder) {
    this.name = name;
    this.coffeeServer =
        coffeeServerBuilder.inProcessServerModule(InProcessServerModule.serverNamed(name)).build();
  }

  public String name() {
    return name;
  }

  public int methodCount(String methodName) {
    return coffeeServer.countingInterceptor().countCalls(methodName);
  }

  @Override
  protected void before() throws IOException, InterruptedException {
    coffeeServer.start();
  }

  @Override
  protected void after() {
    coffeeServer.shutdown();
  }

  @Override
  public String toString() {
    return name;
  }
}

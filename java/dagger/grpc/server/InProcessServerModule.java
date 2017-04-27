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

package dagger.grpc.server;

import static com.google.common.base.Preconditions.checkNotNull;

import dagger.Module;
import dagger.Provides;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import javax.inject.Singleton;

/**
 * Installing this module into a {@link Singleton @Singleton} component means the component can
 * provide a {@link Server} that serves {@linkplain InProcessServerBuilder in-process} requests.
 */
@Module(includes = ServerModule.class)
public final class InProcessServerModule {

  private final String name;

  private InProcessServerModule(String name) {
    this.name = checkNotNull(name);
  }

  /**
   * Creates a module that provides a server that binds to a given name
   *
   * @param name the identity of the server for clients to connect to
   */
  public static InProcessServerModule serverNamed(String name) {
    return new InProcessServerModule(name);
  }

  @Provides
  ServerBuilder<?> serverBuilder() {
    return InProcessServerBuilder.forName(name);
  }
}

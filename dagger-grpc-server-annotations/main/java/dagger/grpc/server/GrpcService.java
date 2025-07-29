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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Annotates a class that implements a gRPC service.
 *
 * <p>Generates several types when annotating a class {@code Foo}:
 *
 * <ul>
 * <li>Interfaces {@code FooComponent} and {@code FooComponent.Factory}.
 * <li>{@linkplain dagger.Module Modules} {@code FooGrpcProxyModule} and {@code
 *     FooGrpcServiceModule}.
 * </ul>
 *
 * <p>To use these types to configure a server:
 *
 * <ol>
 * <li>Create a {@linkplain dagger.Subcomponent subcomponent} that implements {@code FooComponent}
 *     and installs {@code FooGrpcServiceModule}.
 * <li>Install {@link NettyServerModule} or another {@link ServerModule} subclass and {@code
 *     FooGrpcProxyModule} into your {@link javax.inject.Singleton @Singleton} {@linkplain
 *     dagger.Component component}.
 * <li>Bind an implementation of {@code FooComponent.Factory} in your {@link
 *     javax.inject.Singleton @Singleton} {@linkplain dagger.Component component}. The
 *     implementation will typically inject the {@link javax.inject.Singleton @Singleton}
 *     {@linkplain dagger.Component component} and call subcomponent factory methods to instantiate
 *     the correct subcomponent.
 * </ol>
 */
@Documented
@Target(ElementType.TYPE)
public @interface GrpcService {
  /** The class that gRPC generates from the proto service definition. */
  Class<?> grpcClass();
}

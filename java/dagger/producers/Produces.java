/*
 * Copyright (C) 2014 The Dagger Authors.
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

package dagger.producers;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dagger.internal.Beta;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotates methods of a producer module to create a production binding. If the method returns a
 * {@link com.google.common.util.concurrent.ListenableFuture} or {@link
 * com.google.common.util.concurrent.FluentFuture}, then the parameter type of the future is bound
 * to the value that the future produces; otherwise, the return type is bound to the returned value.
 * The production component will pass dependencies to the method as parameters.
 *
 * @since 2.0
 */
@Documented
@Target(METHOD)
@Retention(RUNTIME)
@Beta
public @interface Produces {}

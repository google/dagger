/*
 * Copyright (C) 2015 The Dagger Authors.
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

/**
 * This package contains the public API for the <a href="https://dagger.dev/">Dagger 2</a> dependency
 * injection framework. By building upon <a href="https://jcp.org/en/jsr/detail?id=330">JSR 330</a>,
 * Dagger 2 provides an annotation-driven API for dependency injection whose implementation is
 * entirely generated at compile time by <a
 * href="http://en.wikipedia.org/wiki/Java_annotation#Processing">annotation processors</a>.
 *
 * <p>The entry point into the API is the {@link Component}, which annotates abstract types for
 * Dagger 2 to implement. The dependency graph is configured using annotations such as {@link
 * Module}, {@link Provides} and {@link javax.inject.Inject}.
 *
 * <p>{@code dagger.internal.codegen.ComponentProcessor} is the processor responsible for generating
 * the implementation. Dagger uses the annotation procesor {@linkplain java.util.ServiceLoader
 * service loader} to automatically configure the processor, so explict build configuration
 * shouldn't be necessary.
 */
package dagger;

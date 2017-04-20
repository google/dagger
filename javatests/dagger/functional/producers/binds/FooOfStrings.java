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

package dagger.functional.producers.binds;

/**
 * This is not marked with {@link javax.inject.Inject @Inject} in order to test that {@link
 * dagger.Binds @Binds} properly translate to {@code dagger.internal.codegen.ProductionBinding}s
 * when the right-hand-side of the method is also a production binding. We force this by adding a
 * {@link dagger.producers.Produces @Produces} method to add it to the graph instead of relying on
 * the {@code dagger.internal.codegen.ProvisionBinding} that would be created by default with an
 * {@code @Inject} constructor.
 */
final class FooOfStrings implements Foo<String> {}

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

package dagger.functional.producers.badexecutor;

import com.google.common.util.concurrent.ListenableFuture;
import dagger.functional.producers.ExecutorModule;
import dagger.producers.ProductionComponent;

/**
 * A component that contains entry points that exercise different execution paths, for verifying the
 * behavior when the executor throws a {@link java.util.concurrent.RejectedExecutionException}.
 */
@ProductionComponent(
  dependencies = ComponentDependency.class,
  modules = {ExecutorModule.class, SimpleProducerModule.class}
)
interface SimpleComponent {
  /** An entry point exposing a producer method with no args. */
  ListenableFuture<String> noArgStr();

  /** An entry point exposing a producer method that depends on another producer method. */
  ListenableFuture<Integer> singleArgInt();

  /** An entry point exposing a producer method that depends on a component dependency method. */
  ListenableFuture<Boolean> singleArgBool();

  /** An entry point exposing a component dependency method. */
  ListenableFuture<Double> doubleDep();
}

/*
 * Copyright (C) 2018 The Dagger Authors.
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

package dagger.producers.internal;

import dagger.Binds;
import dagger.Module;
import dagger.producers.Production;
import dagger.producers.ProductionScope;
import java.util.concurrent.Executor;

/**
 * Binds the {@code @ProductionImplementation Executor} binding in {@link ProductionScope} so that
 * only on instance is ever used within production components.
 */
@Module
public abstract class ProductionExecutorModule {
  @Binds
  @ProductionScope
  @ProductionImplementation
  abstract Executor productionImplementationExecutor(@Production Executor executor);

  private ProductionExecutorModule() {}
}

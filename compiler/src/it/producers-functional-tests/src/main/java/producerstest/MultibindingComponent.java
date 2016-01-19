/*
 * Copyright (C) 2015 Google, Inc.
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
package producerstest;

import com.google.common.util.concurrent.ListenableFuture;
import dagger.producers.Produced;
import dagger.producers.Producer;
import dagger.producers.ProductionComponent;
import java.util.Map;
import java.util.Set;
import producerstest.MultibindingProducerModule.PossiblyThrowingMap;
import producerstest.MultibindingProducerModule.PossiblyThrowingSet;

@ProductionComponent(modules = MultibindingProducerModule.class)
interface MultibindingComponent {
  ListenableFuture<Set<String>> strs();
  ListenableFuture<Integer> strCount();

  ListenableFuture<Set<Produced<String>>> successfulSet();

  @PossiblyThrowingSet
  ListenableFuture<Set<Produced<String>>> possiblyThrowingSet();

  ListenableFuture<Map<Integer, String>> map();

  ListenableFuture<Map<Integer, Producer<String>>> mapOfProducers();

  @PossiblyThrowingMap
  ListenableFuture<Map<Integer, String>> possiblyThrowingMap();

  @PossiblyThrowingMap
  ListenableFuture<Map<Integer, Producer<String>>> possiblyThrowingMapOfProducers();
}

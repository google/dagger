/*
 * Copyright (C) 2016 Google, Inc.
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
package producerstest.multibindings;

import com.google.common.util.concurrent.ListenableFuture;
import dagger.producers.Produced;
import dagger.producers.Producer;
import dagger.producers.ProductionComponent;
import java.util.Map;
import java.util.Set;
import producerstest.ExecutorModule;
import producerstest.multibindings.Qualifiers.ObjCount;
import producerstest.multibindings.Qualifiers.PossiblyThrowingMap;
import producerstest.multibindings.Qualifiers.PossiblyThrowingSet;

@ProductionComponent(modules = {ExecutorModule.class, MultibindingProducerModule.class})
interface MultibindingComponent {
  ListenableFuture<Set<String>> strs();
  ListenableFuture<Integer> strCount();

  ListenableFuture<Set<Produced<String>>> successfulSet();

  @PossiblyThrowingSet
  ListenableFuture<Set<Produced<String>>> possiblyThrowingSet();

  ListenableFuture<Map<Integer, String>> map();

  ListenableFuture<Map<Integer, Producer<String>>> mapOfProducer();

  ListenableFuture<Map<Integer, Produced<String>>> mapOfProduced();

  @PossiblyThrowingMap
  ListenableFuture<Map<Integer, String>> possiblyThrowingMap();

  @PossiblyThrowingMap
  ListenableFuture<Map<Integer, Producer<String>>> possiblyThrowingMapOfProducer();

  @PossiblyThrowingMap
  ListenableFuture<Map<Integer, Produced<String>>> possiblyThrowingMapOfProduced();

  ListenableFuture<Set<Object>> objs();

  ListenableFuture<Set<Produced<Object>>> producedObjs();

  ListenableFuture<Map<Object, Object>> objMap();

  ListenableFuture<Map<Object, Produced<Object>>> objMapOfProduced();

  ListenableFuture<Map<Object, Producer<Object>>> objMapOfProducer();

  @ObjCount
  ListenableFuture<Integer> objCount();
}

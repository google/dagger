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

import com.google.common.util.concurrent.ListenableFuture;
import dagger.functional.producers.binds.SimpleBindingModule.SomeQualifier;
import dagger.producers.Produced;
import dagger.producers.Producer;
import dagger.producers.ProductionComponent;
import java.util.Map;
import java.util.Set;
import javax.inject.Singleton;

@Singleton
@ProductionComponent(modules = SimpleBindingModule.class)
public interface SimpleBindsProductionComponent {
  ListenableFuture<Object> object();

  ListenableFuture<Foo<String>> fooOfStrings();

  @SomeQualifier
  ListenableFuture<Foo<String>> qualifiedFooOfStrings();

  ListenableFuture<Foo<Integer>> fooOfIntegers();

  ListenableFuture<Set<Foo<? extends Number>>> foosOfNumbers();

  ListenableFuture<Set<Object>> objects();

  ListenableFuture<Set<CharSequence>> charSequences();

  ListenableFuture<Map<Integer, Object>> integerObjectMap();

  ListenableFuture<Map<Integer, Producer<Object>>> integerProducerOfObjectMap();

  ListenableFuture<Map<Integer, Produced<Object>>> integerProducedOfObjectMap();

  @SomeQualifier ListenableFuture<Map<Integer, Object>> qualifiedIntegerObjectMap();
}

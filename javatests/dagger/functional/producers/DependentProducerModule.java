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

package dagger.functional.producers;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import java.util.List;

@ProducerModule
final class DependentProducerModule {
  @Produces
  ListenableFuture<List<String>> greetings(Integer numGreetings, String greeting) {
    List<String> greetings = ImmutableList.of(
        String.valueOf(numGreetings), greeting, Ascii.toUpperCase(greeting));
    return Futures.immediateFuture(greetings);
  }
}

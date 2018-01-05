/*
 * Copyright (C) 2017 The Dagger Authors.
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

import com.google.common.util.concurrent.ListenableFuture;
import dagger.functional.producers.GenericComponent.NongenericModule;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import dagger.producers.ProductionComponent;
import java.util.Arrays;
import java.util.List;

@ProductionComponent(modules = {ExecutorModule.class, NongenericModule.class})
interface GenericComponent {

  ListenableFuture<List<String>> list(); // b/71595104

  // b/71595104
  @ProducerModule
  abstract class GenericModule<T> {

    @Produces
    List<T> list(T t, String string) {
      return Arrays.asList(t);
    }
  }

  // b/71595104
  @ProducerModule
  class NongenericModule extends GenericModule<String> {
    @Produces
    static String string() {
      return "string";
    }
  }
}

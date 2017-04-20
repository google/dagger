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

package dagger.functional.producers.scope;

import dagger.multibindings.IntoSet;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;

/**
 * A module that provides two entries into a set; but since the inputs are scoped, the set should
 * only have one value.
 */
@ProducerModule
final class SetProducerModule {
  @Produces
  @IntoSet
  static Object setValue1(Object value) {
    return value;
  }

  @Produces
  @IntoSet
  static Object setValue2(Object value) {
    return value;
  }
}

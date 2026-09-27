/*
 * Copyright (C) 2020 The Dagger Authors.
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

package example.common;

import dagger.Lazy;
import javax.inject.Inject;

/** A coffee maker to brew the coffee. */
public class CoffeeMaker {
  private final CoffeeLogger logger;
  private final Lazy<Heater> heater; // Create a possibly costly heater only when we use it.
  private final Pump pump;

  @Inject
  CoffeeMaker(CoffeeLogger logger, Lazy<Heater> heater, Pump pump) {
    this.logger = logger;
    this.heater = heater;
    this.pump = pump;
  }

  public void brew() {
    heater.get().on();
    pump.pump();
    logger.log(" [_]P coffee! [_]P ");
    heater.get().off();
  }
}

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

import javax.inject.Inject;

/** An electric heater to heat the coffee. */
public class ElectricHeater implements Heater {

  private final CoffeeLogger logger;
  private boolean heating;

  @Inject
  ElectricHeater(CoffeeLogger logger) {
    this.logger = logger;
  }

  @Override
  public void on() {
    this.heating = true;
    logger.log("~ ~ ~ heating ~ ~ ~");
  }

  @Override
  public void off() {
    this.heating = false;
  }

  @Override
  public boolean isHot() {
    return heating;
  }
}

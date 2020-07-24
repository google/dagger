/*
 * Copyright (C) 2019 The Dagger Authors.
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

package dagger.example.atm;

import java.math.BigDecimal;

/**
 * Abstract {@link Command} that expects a single argument that can be converted to {@link
 * BigDecimal}.
 */
abstract class BigDecimalCommand extends SingleArgCommand {

  private final Outputter outputter;

  protected BigDecimalCommand(Outputter outputter) {
    this.outputter = outputter;
  }

  @Override
  protected final Result handleArg(String arg) {
    BigDecimal amount = tryParse(arg);
    if (amount == null) {
      outputter.output(arg + " is not a valid number");
    } else if (amount.signum() <= 0) {
      outputter.output("amount must be positive");
    } else {
      handleAmount(amount);
    }
    return Result.handled();
  }

  private static BigDecimal tryParse(String arg) {
    try {
      return new BigDecimal(arg);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** Handles the given (positive) {@code amount} of money. */
  protected abstract void handleAmount(BigDecimal amount);
}

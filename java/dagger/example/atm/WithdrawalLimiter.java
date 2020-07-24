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
import javax.inject.Inject;

/** Maintains the withdrawal amount available within a user session. */
@PerSession
final class WithdrawalLimiter {
  private BigDecimal remainingWithdrawalLimit;

  @Inject
  WithdrawalLimiter(@MaximumWithdrawal BigDecimal maximumWithdrawal) {
    this.remainingWithdrawalLimit = maximumWithdrawal;
  }

  void recordDeposit(BigDecimal amount) {
    remainingWithdrawalLimit = remainingWithdrawalLimit.add(amount);
  }

  void recordWithdrawal(BigDecimal amount) {
    remainingWithdrawalLimit = remainingWithdrawalLimit.subtract(amount);
  }

  BigDecimal remainingWithdrawalLimit() {
    return remainingWithdrawalLimit;
  }
}

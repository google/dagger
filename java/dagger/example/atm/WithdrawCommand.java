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

import dagger.example.atm.Database.Account;
import java.math.BigDecimal;
import javax.inject.Inject;

/** Withdraws money from the ATM. */
final class WithdrawCommand extends BigDecimalCommand {
  private final Outputter outputter;
  private final Account account;
  private final BigDecimal minimumBalance;
  private final WithdrawalLimiter withdrawalLimiter;

  @Inject
  WithdrawCommand(
      Outputter outputter,
      Account account,
      @MinimumBalance BigDecimal minimumBalance,
      WithdrawalLimiter withdrawalLimiter) {
    super(outputter);
    this.outputter = outputter;
    this.account = account;
    this.minimumBalance = minimumBalance;
    this.withdrawalLimiter = withdrawalLimiter;
  }

  @Override
  protected void handleAmount(BigDecimal amount) {
    BigDecimal remainingWithdrawalLimit = withdrawalLimiter.remainingWithdrawalLimit();
    if (amount.compareTo(remainingWithdrawalLimit) > 0) {
      outputter.output(
          String.format(
              "you may not withdraw %s; you may withdraw %s more in this session",
              amount, remainingWithdrawalLimit));
      return;
    }

    BigDecimal newBalance = account.balance().subtract(amount);
    if (newBalance.compareTo(minimumBalance) < 0) {
      outputter.output(
          String.format(
              "you don't have sufficient funds to withdraw %s. "
                  + "your balance is %s and the minimum balance is %s",
              amount, account.balance(), minimumBalance));
    } else {
      account.withdraw(amount);
      withdrawalLimiter.recordWithdrawal(amount);
      outputter.output("your new balance is: " + account.balance());
    }
  }
}

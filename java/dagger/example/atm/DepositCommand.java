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

/** Deposits money to the ATM. */
final class DepositCommand extends BigDecimalCommand {
  private final Outputter outputter;
  private final Account account;
  private final WithdrawalLimiter withdrawalLimiter;

  @Inject
  DepositCommand(Outputter outputter, Account account, WithdrawalLimiter withdrawalLimiter) {
    super(outputter);
    this.outputter = outputter;
    this.account = account;
    this.withdrawalLimiter = withdrawalLimiter;
  }

  @Override
  protected void handleAmount(BigDecimal amount) {
    account.deposit(amount);
    withdrawalLimiter.recordDeposit(amount);
    outputter.output("your new balance is: " + account.balance());
  }
}

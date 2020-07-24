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
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/** A database that stores all of its data in memory. */
@Singleton
final class InMemoryDatabase implements Database {
  private final Map<String, Account> accounts = new HashMap<>();

  @Inject
  InMemoryDatabase() {}

  @Override
  public Account getAccount(String username) {
    return accounts.computeIfAbsent(username, InMemoryAccount::new);
  }

  private static final class InMemoryAccount implements Account {
    private final String username;
    private BigDecimal balance = BigDecimal.ZERO;

    InMemoryAccount(String username) {
      this.username = username;
    }

    @Override
    public String username() {
      return username;
    }

    @Override
    public void deposit(BigDecimal amount) {
      checkNonNegative(amount, "deposit");
      balance = balance.add(amount);
    }

    @Override
    public void withdraw(BigDecimal amount) {
      checkNonNegative(amount, "withdraw");
      balance = balance.subtract(amount);
    }

    private void checkNonNegative(BigDecimal amount, String action) {
      if (amount.signum() == -1) {
        throw new IllegalArgumentException(
            String.format("Cannot %s negative amounts: %s", action, amount));
      }
    }

    @Override
    public BigDecimal balance() {
      return balance;
    }
  }
}

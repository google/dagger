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
import java.util.List;
import javax.inject.Inject;

/** Logs out the current user. */
final class LogoutCommand implements Command {

  private final Outputter outputter;
  private final Account account;

  @Inject
  LogoutCommand(Outputter outputter, Account account) {
    this.outputter = outputter;
    this.account = account;
  }

  @Override
  public Result handleInput(List<String> input) {
    if (!input.isEmpty()) {
      return Result.invalid();
    }
    outputter.output("logged out " + account.username());
    return Result.inputCompleted();
  }
}

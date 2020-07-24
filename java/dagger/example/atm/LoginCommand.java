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
import java.util.Optional;
import javax.inject.Inject;

/** Logs in a user, allowing them to interact with the ATM. */
final class LoginCommand extends SingleArgCommand {
  private final Outputter outputter;
  private final Optional<Account> account;
  private final UserCommandsRouter.Factory userCommandsFactory;

  @Inject
  LoginCommand(
      Outputter outputter,
      Optional<Account> account,
      UserCommandsRouter.Factory userCommandsFactory) {
    this.outputter = outputter;
    this.account = account;
    this.userCommandsFactory = userCommandsFactory;
  }

  @Override
  public Result handleArg(String username) {
    // If an Account binding exists, that means there is a user logged in. Don't allow a login
    // command if we already have someone logged in!
    if (account.isPresent()) {
      String loggedInUser = account.get().username();
      outputter.output(loggedInUser + " is already logged in");
      if (!loggedInUser.equals(username)) {
        outputter.output("run `logout` first before trying to log in another user");
      }
      return Result.handled();
    } else {
      UserCommandsRouter userCommands = userCommandsFactory.create(username);
      return Result.enterNestedCommandSet(userCommands.router());
    }
  }
}

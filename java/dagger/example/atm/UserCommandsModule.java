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

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;
import dagger.multibindings.StringKey;

/** Commands that are only applicable when a user is logged in. */
@Module
interface UserCommandsModule {
  @Binds
  @IntoMap
  @StringKey("deposit")
  Command deposit(DepositCommand command);

  @Binds
  @IntoMap
  @StringKey("withdraw")
  Command withdraw(WithdrawCommand command);

  @Binds
  @IntoMap
  @StringKey("logout")
  Command logout(LogoutCommand command);
}

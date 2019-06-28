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
import dagger.BindsOptionalOf;
import dagger.Module;
import dagger.example.atm.Database.Account;
import dagger.multibindings.IntoMap;
import dagger.multibindings.StringKey;

/** Installs basic commands. */
@Module
interface CommandsModule {
  @Binds
  @IntoMap
  @StringKey("hello")
  Command helloWorld(HelloWorldCommand command);

  @Binds
  @IntoMap
  @StringKey("login")
  Command login(LoginCommand command);

  /**
   * Declare an optional binding for {@link Account}. This allows other bindings to change their
   * behavior depending on whether an {@link Account} is bound in the current (sub)component.
   */
  @BindsOptionalOf
  Account loggedInAccount();
}

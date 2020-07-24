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

import dagger.example.atm.Command.Result;
import dagger.example.atm.Command.Status;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

/** Routes individual text commands to the appropriate {@link Command}(s). */
final class CommandRouter {
  private final Map<String, Command> commands;
  private final Outputter outputter;

  @Inject
  CommandRouter(Map<String, Command> commands, Outputter outputter) {
    this.commands = commands;
    this.outputter = outputter;
  }

  /**
   * Calls {@link Command#handleInput(String) command.handleInput(input)} on this router's
   * {@linkplain #commands commands}.
   */
  Result route(String input) {
    List<String> splitInput = split(input);
    if (splitInput.isEmpty()) {
      return invalidCommand(input);
    }

    String commandKey = splitInput.get(0);
    Command command = commands.get(commandKey);
    if (command == null) {
      return invalidCommand(input);
    }

    List<String> args = splitInput.subList(1, splitInput.size());
    Result result = command.handleInput(args);
    return result.status().equals(Status.INVALID) ? invalidCommand(input) : result;
  }

  private Result invalidCommand(String input) {
    outputter.output(String.format("couldn't understand \"%s\". please try again.", input));
    return Result.invalid();
  }

  private static List<String> split(String input) {
    return Arrays.asList(input.trim().split("\\s+"));
  }
}

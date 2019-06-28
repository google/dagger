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
import java.util.ArrayDeque;
import java.util.Deque;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Processes successive commands by delegating to a {@link CommandRouter}.
 *
 * <p>Whereas {@link CommandRouter} routes an input string to a particular {@link Command}, this
 * class maintains inter-command state to determine which {@link CommandRouter} should route
 * successive commands.
 *
 * <p>This class is {@link Singleton} scoped because it has mutable state ({@code
 * commandRouterStack}), and all users of {@link CommandProcessor} must use the same instance.
 */
@Singleton
final class CommandProcessor {
  private final Deque<CommandRouter> commandRouterStack = new ArrayDeque<>();

  @Inject
  CommandProcessor(CommandRouter firstCommandRouter) {
    commandRouterStack.push(firstCommandRouter);
  }

  Status process(String input) {
    if (commandRouterStack.isEmpty()) {
      throw new IllegalStateException("No command router is available!");
    }

    Result result = commandRouterStack.peek().route(input);
    switch (result.status()) {
      case INPUT_COMPLETED:
        commandRouterStack.pop();
        return commandRouterStack.isEmpty() ? Status.INPUT_COMPLETED : Status.HANDLED;
      case HANDLED:
        // TODO(ronshapiro): We currently have a case of using a subcomponent for nested commands,
        // which requires maintaining a binding indicating whether we are in the subcomponent are
        // not. We can include another example where there's a CommandRouter that is created from an
        // entirely different component, that way there are no inherited commands.
        result.nestedCommandRouter().ifPresent(commandRouterStack::push);
        // fall through
      case INVALID:
        return result.status();
    }
    throw new AssertionError(result.status());
  }
}

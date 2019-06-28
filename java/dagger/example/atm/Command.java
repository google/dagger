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

import java.util.List;
import java.util.Optional;

/** A text-based command handler. */
interface Command {
  /**
   * Processes and optionally acts upon the given {@code input}.
   *
   * @return a {@link Result} indicating how the input was handled
   */
  Result handleInput(List<String> input);

  /**
   * A command result, which has a {@link Status} and optionally a new {@link CommandRouter} that
   * will handle subsequent commands.
   */
  final class Result {
    private final Status status;
    private final Optional<CommandRouter> nestedCommandRouter;

    private Result(Status status, Optional<CommandRouter> nestedCommandRouter) {
      this.status = status;
      this.nestedCommandRouter = nestedCommandRouter;
    }

    static Result invalid() {
      return new Result(Status.INVALID, Optional.empty());
    }

    static Result handled() {
      return new Result(Status.HANDLED, Optional.empty());
    }

    static Result inputCompleted() {
      return new Result(Status.INPUT_COMPLETED, Optional.empty());
    }

    static Result enterNestedCommandSet(CommandRouter nestedCommandRouter) {
      return new Result(Status.HANDLED, Optional.of(nestedCommandRouter));
    }

    Status status() {
      return status;
    }

    Optional<CommandRouter> nestedCommandRouter() {
      return nestedCommandRouter;
    }
  }

  enum Status {
    /** The command or its arguments were invalid. */
    INVALID,

    /** The command handled the input and no other commands should attempt to handle it. */
    HANDLED,

     // TODO(ronshapiro): maybe call this TERMINATED? If so, maybe this should be called
    // ContinueStatus?
    /** The command handled the input and no further inputs should be submitted. */
    INPUT_COMPLETED,
    ;
  }
}

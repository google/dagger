---
layout: default
title: Tutorial setup
---

To explain how to use Dagger, we will build a command line
[ATM](https://en.wikipedia.org/wiki/Automated_teller_machine) application. It
will track the balance of accounts, and accept commands on the command line:

```
> deposit 20
> withdraw 10
```

Let's start with building the shell of this application, first without Dagger.
If some aspects appear to be overly complicated, bear with us, as Dagger starts
to show its power once applications grow larger.

First, we'll create a `Command` interface for each of the possible textual
commands that the ATM can handle.

```java
/** Logic to process some user input. */
interface Command {
  /**
   * String token that signifies this command should be selected (e.g.:
   * "deposit", "withdraw")
   */
  String key();

  /** Process the rest of the command's words and do something. */
  Result handleInput(List<String> input);

  /**
   * This wrapper class is introduced to make a future change easier
   * even though it looks unnecessary right now.
   */
  final class Result {
    private final Status status;

    private Result(Status status) {
      this.status = status;
    }

    static Result invalid() {
      return new Result(Status.INVALID);
    }

    static Result handled() {
      return new Result(Status.HANDLED);
    }

    Status status() {
      return status;
    }
  }

  enum Status {
    INVALID,
    HANDLED
  }
}
```

And we'll create a `CommandRouter` that can collect multiple `Command`s and
route input strings to them based on the first word in the input. To start,
we'll give it an empty map of commands.

```java
final class CommandRouter {
  private final Map<String, Command> commands = Collections.emptyMap();

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
    return result.status().equals(Status.INVALID) ?
      invalidCommand(input) : result;
  }

  private Result invalidCommand(String input) {
    System.out.println(
        String.format("couldn't understand \"%s\". please
        try again.", input));
    return Result.invalid();
  }

  // Split on whitespace
  private static List<String> split(String input) {
    return Arrays.asList(input.trim().split("\\s+"));
  }
}
```

Finally, we'll create a main method:

```java
class CommandLineAtm {
  public static void main(String[] args) {
    Scanner scanner = new Scanner(System.in);
    CommandRouter commandRouter = new CommandRouter();

    while (scanner.hasNextLine()) {
      Result unused = commandRouter.route(scanner.nextLine());
    }
  }
}
```

Congratulations! We now have a working command line ATM! It doesn't do anything
just yet, but very soon we'll change that.

<section style="text-align: center" markdown="1">
  [Previous](index) · [Next](02-initial-dagger)
</section>

---
layout: default
title: Only allow depositing after logging in
---

The current UX design of this ATM can be improved by only supporting `deposit`
commands after one has already logged in. To do that, let's first perform a
refactoring.

## Refactoring: Introducing a `CommandProcessor`

We can introduce a `CommandProcessor` that contains a [stack] of
`CommandRouter`s. Pushing onto the stack will enable a new set of commands to be
processed. Popping will return to the previous set of commands. We can modify
`Command.handleInput()` to return a `Result` that is a combination of the
`Status` enum and an optional `CommandRouter` that should be pushed on the
stack. Finally, an `INPUT_COMPLETED` enum value can be added to `Status` to
indicate when to pop the stack:

```java
interface Command {
  Result handleInput(List<String> input);

  class Result {
    private final Status status;
    private final Optional<CommandRouter> nestedCommandRouter;

    // ...

    static Result enterNestedCommandSet(CommandRouter nestedCommandRouter) {
      return new Result(Status.HANDLED, Optional.of(nestedCommandRouter));
    }
  }

  enum Status {
    INVALID, HANDLED, INPUT_COMPLETED
  }
}
```

```java
@Singleton
final class CommandProcessor {
  private final Deque<CommandRouter> commandRouterStack = new ArrayDeque<>();

  @Inject
  CommandProcessor(CommandRouter firstCommandRouter) {
    commandRouterStack.push(firstCommandRouter);
  }

  Status process(String input) {
    Result result = commandRouterStack.peek().route(input);
    if (result.status().equals(Status.INPUT_COMPLETED)) {
      commandRouterStack.pop();
      return commandRouterStack.isEmpty()
          ? Status.INPUT_COMPLETED
          : Status.HANDLED;
    }

    result.nestedCommandRouter().ifPresent(commandRouterStack::push);
    return result.status();
  }
}
```

`CommandProcessor` is marked with [`@Singleton`] to ensure that only one
`CommandRouter` stack is created.

We can refactor our [`@Component`] accordingly:

```java
class CommandLineAtm {
  public static void main(String[] args) {
    CommandProcessor commandProcessor =
        DaggerCommandLineAtm_CommandProcessorFactory.create().processor();

    while (scanner.hasNextLine()) {
      commandProcessor.process(scanner.nextLine());
    }
  }

  @Component(modules =  ... )
  interface CommandProcessorFactory {
    CommandProcessor processor();
  }
}
```

## Creating nested commands

Dagger already knows how to create a `CommandRouter` from a `Map<String,
Command>`, but those commands apply to all users. That requires specifying the
username for every command. So that we don't have to do that, let's add a
concept of a user session and commands that only apply while a user is logged
in. But how can we create two different `CommandRouter`s with different `Map`s,
one for logged out users and one for logged in users?

We can introduce a [`@Subcomponent`] to help. A [`@Subcomponent`] is similar to
a [`@Component`]: it has abstract methods that Dagger implements, and it can use
[`@Module`]s. It always has a parent component, and it can access any type that
the parent component can access. Any types it creates are hidden from the parent
component.

Let's create a [`@Subcomponent`] that adds `Command`s for a logged-in user. It
will share the same `Database` that exists in the `CommandProcessorFactory`
component, which will be useful when we want to deposit and withdraw money from
a particular user.

```java
@Subcomponent(modules = UserCommandsModule.class)
interface UserCommandsRouter {
  CommandRouter router();

  @Subcomponent.Factory
  interface Factory {
    UserCommandsRouter create(@BindsInstance Account account);
  }

  @Module(subcomponents = UserCommandsRouter.class)
  interface InstallationModule {}
}
```

There are a few things that are happening here. Let's break it down:

*   The [`@Subcomponent`] annotation defines what modules Dagger should know
    about when creating instances for this subcomponent only. Just like
    [`@Component`], it can take a list of modules: we've moved the
    `UserCommandsModule` that declares our `DepositCommand` here.
*   The `router()` method declares what object we want Dagger to create.
*   The [`@Subcomponent.Factory`] annotation annotates a factory type for this
    subcomponent. It's an interface we define.
    *   It has a single method that creates an instance of the subcomponent.
        That method has a [`@BindsInstance`] parameter, which tells Dagger that
        the `Account` instance we pass as an argument should be requestable by
        any [`@Inject`] constructor, [`@Binds`] method, or [`@Provides`] method
        in this subcomponent.
*   We have a module that declares the subcomponent. Including this module in
    _another component_ will make the [`@Subcomponent.Factory`] available there.
    That's our bridge between the two components.

If we include `UserCommandsRouter.InstallationModule` in our [`@Component`]
annotation, we can start to use it in `LoginCommand`:

```java
final class LoginCommand extends SingleArgCommand {
  private final Database database;
  private final Outputter outputter;
  private final UserCommandsRouter.Factory userCommandsRouterFactory;

  @Inject
  LoginCommand(
      Database database,
      Outputter outputter,
      UserCommandsRouter.Factory userCommandsRouterFactory) {
    this.database = database;
    this.outputter = outputter;
    this.userCommandsRouterFactory = userCommandsRouterFactory;
  }

  @Override
  public Result handleArg(String username) {
    Account account = database.getAccount(username);
    outputter.output(
        username + " is logged in with balance: " + account.balance());
    return Result.enterNestedCommandSet(
        userCommandsRouterFactory.create(account).router());
  }
}
```

Now `deposit` is only valid after logging in! We can also remove the username
from the command syntax because it is being provided already with the
[`@BindsInstance`] parameter:

```java
final class DepositCommand extends BigDecimalCommand {
  private final Account account;
  private final Outputter outputter;

  @Inject
  DepositCommand(Account account, Outputter outputter) {
    this.account = account;
    this.outputter = outputter;
  }

  @Override
  public void handleAmount(BigDecimal amount) {
    account.deposit(amount);
    outputter.output(account.username() + " now has: " + account.balance());
  }
}
```

(The abstract `BigDecimalCommand` we're using for simplicity is defined
[here][BigDecimalCommand]).

> **CONCEPTS**
>
> *   A **[`@Subcomponent`]**-annotated type (a "subcomponent") is, like a
>     [`@Component`]-annotated one, a factory for an object. Like
>     [`@Component`], it uses modules to give Dagger implementation
>     instructions. Subcomponents always have a parent component (or a parent
>     subcomponent), and any objects that are requestable in the parent are
>     requestable in the child, but not vice versa.
> *   A **[`@Subcomponent.Factory`]**-annotated type creates instances of the
>     subcomponent. An instance of it is requestable in the parent component.
>     *   There is a parallel annotation, [`@Component.Factory`], for
>         [`@Component`].
> *   **[`@BindsInstance`]** parameters let you make arbitrary objects
>     requestable by other binding methods in the component.

<section style="text-align: center" markdown="1">

[Previous](09-maintaining-state) · [Next](11-withdraw-command)

</section>

[BigDecimalCommand]: https://github.com/google/dagger/tree/master/java/dagger/example/atm/BigDecimalCommand.java

[`@BindsInstance`]: https://dagger.dev/api/latest/dagger/BindsInstance.html
[`@Binds`]: https://dagger.dev/api/latest/dagger/Binds.html
[`@Component`]: https://dagger.dev/api/latest/dagger/Component.html
[`@Component.Factory`]: https://dagger.dev/api/latest/dagger/Component.Factory.html
[`@Inject`]: http://docs.oracle.com/javaee/7/api/javax/inject/Inject.html
[`@Module`]: https://dagger.dev/api/latest/dagger/Module.html
[`@Provides`]: https://dagger.dev/api/latest/dagger/Provides.html
[`@Singleton`]: http://docs.oracle.com/javaee/7/api/javax/inject/Inject.html
[stack]: https://en.wikipedia.org/wiki/Stack_\(abstract_data_type\)
[`@Subcomponent`]: https://dagger.dev/api/latest/dagger/Subcomponent.html
[`@Subcomponent.Factory`]: https://dagger.dev/api/latest/dagger/Subcomponent.Factory.html

---
layout: default
title: Avoiding recursive logins
---

If you run the application and try to run `login first-username` followed by
`login second-username`, you'll notice that it works! How/why is this working?

The values in the `Map<String, Command>` that are available in
`CommandProcessorFactory` are `[HelloWorldCommand, LoginCommand]`. When we
created the [`@Subcomponent`] for `UserCommandsRouter`, it inherited the modules
from `CommandProcessorFactory`, and therefore also the values of `Map<String,
Command>`. So the full set of values in the `Map<String, Command>` for
`UserCommandsRouter` is `[HelloWorldCommand, LoginCommand, DepositCommand,
WithdrawCommand]`.

There is no way to _remove_ values from the map, but we have another option. We
can add an `Optional<Account>` dependency to `LoginCommand` to indicate if we're
in a state where an `Account` is available or not:

```java
final class LoginCommand extends SingleArgCommand {
  private final Database database;
  private final Outputter outputter;
  private final UserCommandsRouter.Factory userCommandsRouterFactory;
  private final Optional<Account> account;

  @Inject
  LoginCommand(
      Database database,
      Outputter outputter,
      UserCommandsRouter.Factory userCommandsRouterFactory,
      Optional<Account> account) {
    this.database = database;
    this.outputter = outputter;
    this.userCommandsRouterFactory = userCommandsRouterFactory;
    this.account = account;
  }

  @Override
  public Result handleArg(String username) {
    if (account.isPresent()) {
      // Ignore "login <foo>" commands if we already have an account
      return Result.handled();
    }
  }
}
```

In order to tell Dagger how to supply this `Optional<Account>`, let's add a
[`@BindsOptionalOf`] method to `LoginCommandModule`:

```java
@Module
interface LoginCommandModule {
  ...

  @BindsOptionalOf
  Account optionalAccount();
}
```

When Dagger sees a [`@BindsOptionalOf`] method, it will use `Optional.of(<the
account>)` if an `Account` is available; if not, it will use `Optional.empty()`
(or `Optional.absent()` if you're using the Guava version of `Optional`).

Looking back, we can now see that within `CommandProcessorFactory`, there is no
`Account`, but within `UserCommandsRouter` there is. Each will have its own
`LoginCommand` that _differs_ in this optional account, and we can therefore run
different logic in both cases!

> **CONCEPTS**
>
> *   When Dagger creates sets or maps of objects, the set or map will contain
>     all values from any parent component(s) in addition to the values that are
>     installed by bindings in the component itself.
> *   **[`@BindsOptionalOf`]** tells Dagger that it can construct instances of
>     `Optional<ReturnType>`. The presence of the `Optional` is determined by
>     whether Dagger knows how to create an instance of `ReturnType`, and it can
>     be present in a subcomponent but absent in its parent.

[Previous](13-max-withdrawal-across-commands) · [Next](15-conclusion)
{@paragraph style="text-align: center"}

[`@BindsOptionalOf`]: https://dagger.dev/api/latest/dagger/BindsOptionalOf.html
[`@Subcomponent`]: https://dagger.dev/api/latest/dagger/Subcomponent.html

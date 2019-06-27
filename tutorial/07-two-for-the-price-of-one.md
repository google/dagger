---
layout: default
title: Two commands for the price of one
---

So far, `CommandRouter` only supports a single command at a time, but we'd like
to have it support many commands.

You'll notice that if you try to add **both** modules together
(`@Component(modules = {HelloWorldModule.class, LoginCommandModule.class})`),
Dagger will report an error. The two modules conflict—they each tell Dagger how
to create a single `Command`, and Dagger doesn't know which should win.

We want `CommandRouter` to depend on _multiple_ commands instead of just one.
Since our `CommandRouter` wants a map of commands, we'll use [`@IntoMap`] to map
each `Command` our application uses to the prefix of the command:

```java
@Module
abstract class LoginCommandModule {
  @Binds
  @IntoMap
  @StringKey("login")
  abstract Command loginCommand(LoginCommand command);
}
```

```java
@Module
abstract class HelloWorldModule {
  @Binds
  @IntoMap
  @StringKey("hello")
  abstract Command helloWorldCommand(HelloWorldCommand command);
}
```

The [`@StringKey`] annotation, combined with [`@IntoMap`], tells Dagger how to
populate a `Map<String, Command>`. Note that our `Command` interface no longer
needs a `key()` method because we're telling Dagger what the key is directly.

To take advantage of this, we can switch `CommandRouter`'s constructor parameter
to `Map<String, Command>`. Notice that `Command` on its own won't work anymore.

```java
final class CommandRouter {
  private final Map<String, Command> commands;

  @Inject
  CommandRouter(Map<String, Command> commands) {
    // This map contains:
    // "hello" -> HelloWorldCommand
    // "login" -> LoginCommand
    this.commands = commands;
  }

  ...
}
```

If you run the application now, you'll see that both `hello` and `login <your
name>` both work. Make sure to update the [`@Component`] annotation to include
both modules.

> **CONCEPTS**
>
> *   **[`@IntoMap`]** allows for the creation of a map with values of a
>     specific type, with keys set using special annotations such as
>     [`@StringKey`] or [`@IntKey`]. Because keys are set via annotation, Dagger
>     ensures that multiple values are not mapped to the same key.
> *   **[`@IntoSet`]** allows for the creation of a set of types to be collected
>     together. It can be used together with [`@Binds`] and [`@Provides`]
>     methods to provide a `Set<ReturnType>`.
> *   [`@IntoMap`] and [`@IntoSet`] are both ways of introducing what is often
>     called "multibindings", where a collection contains elements from several
>     different binding methods.

<section style="text-align: center">

[Previous](06-new-command) · [Next](08-user-specific-types)

</section>

[`@Binds`]: https://dagger.dev/api/latest/dagger/Binds.html
[`@Component`]: https://dagger.dev/api/latest/dagger/Component.html
[`@IntKey`]: https://dagger.dev/api/latest/dagger/multibindings/IntKey.html
[`@IntoMap`]: https://dagger.dev/api/latest/dagger/multibindings/IntoMap.html
[`@IntoSet`]: https://dagger.dev/api/latest/dagger/multibindings/IntoSet.html
[`@Provides`]: https://dagger.dev/api/latest/dagger/Provides.html
[`@StringKey`]: https://dagger.dev/api/latest/dagger/multibindings/StringKey.html

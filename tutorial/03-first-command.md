---
layout: default
title: Adding our first `Command`
---

If you've tried to run the example so far, you'll see that it doesn't know how
to respond to any commands. Let's change that!

First, let's create an implementation of `Command`:

```java
final class HelloWorldCommand implements Command {
  @Inject
  HelloWorldCommand() {}

  @Override
  public String key() {
    return "hello";
  }

  @Override
  public Status handleInput(List<String> input) {
    if (!input.isEmpty()) {
      return Status.INVALID;
    }
    System.out.println("world!");
    return Status.HANDLED;
  }
}
```

Now let's add a parameter to `CommandRouter`'s constructor for that command:

```java
final class CommandRouter {
  private final Map<String, Command> commands = new HashMap<>();

  @Inject
  CommandRouter(HelloWorldCommand helloWorldCommand) {
    commands.put(helloWorldCommand.key(), helloWorldCommand);
  }

  ...
}
```

This parameter tells Dagger that when it creates a `CommandRouter` instance, it
should also provide a `HelloWorldCommand` instance and pass that to the
constructor: `new CommandRouter(helloWorldCommand)`. Dagger knows how to create
a `HelloWorldCommand` because it has an [`@Inject`] constructor, just like
`CommandRouter`.

If you try to run the application, you'll see that you can now type `hello` and
the application will respond `world!`. We're making progress!

> **CONCEPTS**
>
> *   Parameters to an [`@Inject`] constructor are the dependencies of the
>     class. Dagger will provide a class's dependencies to instantiate the class
>     itself. Note that this is recursive: a dependency may have dependencies of
>     its own!
> *   **Terminology:**
>     *   When discussing the relationship between these two types, one might
>         say `CommandRouter` _requests_ `HelloWorldCommand` or `CommandRouter`
>         _depends on_ `HelloWorldCommand`. Conversely, `HelloWorldCommand`'s
>         `@Inject` constructor _provides_ instances of `HelloWorldCommand`,
>         which are _requested by_ `CommandRouter`.
>     *   Sometimes people say `CommandRouter` _injects_ `HelloWorldCommand` in
>         order to emphasize that something else puts a `HelloWorldCommand`
>         object into `CommandRouter`, in contrast to `CommandRouter` itself
>         fetching or creating one. But it's also common to say that _Dagger_
>         injects `CommandRouter` because it instantiates it via its [`@Inject`]
>         constructor. These different uses of the word "injects" can get
>         confusing, so we're using the more explicit terms in this tutorial.

[Previous](02-initial-dagger) · [Next](04-depending-on-interface)
{@paragraph style="text-align: center"}

[`@Inject`]: http://docs.oracle.com/javaee/7/api/javax/inject/Inject.html

---
layout: default
title: An abstraction for output
---

Right now, `HelloWorldCommand` uses `System.out.println()` to write its output.
In the spirit of dependency injection, let's use an abstraction so that we can
remove this direct use of `System.out`. What we'll do is create an `Outputter`
type that does something with text that's written to it. Our default
implementation can still use `System.out.println()`, but this gives us
flexibility to change that later without changing `HelloWorldCommand`. For
example, our tests may use an implementation that adds the string to a
`List<String>` so we can check what was output.

Here's our `Outputter` type:

```java
interface Outputter {
  void output(String output);
}
```

And here's how we'd use it in `HelloWorldCommand`:

```java
private final Outputter outputter;

@Inject
HelloWorldCommand(Outputter outputter) {
  this.outputter = outputter;
}

@Override
public Status handleInput(List<String> input) {
  outputter.output("world!");
  return Status.HANDLED;
}
```

`Outputter` is an `interface`. We could write an implementation of it, give that
class an [`@Inject`] constructor, and then use [`@Binds`] to bind `Outputter` to
that implementation. But `Outputter` is very simple … so simple that we could
even implement it as a lambda or method reference. So instead of doing all that,
let's write a `static` method that just creates and returns an instance of
`Outputter` itself!

```java
@Module
abstract class SystemOutModule {
  @Provides
  static Outputter textOutputter() {
    return System.out::println;
  }
}
```

Here we've created another [`@Module`], but instead of a [`@Binds`] method we
have a [`@Provides`] method. A [`@Provides`] method works a lot like an
[`@Inject`] constructor: here it tells Dagger that when it needs an instance of
`Outputter`, it should _call_ `SystemOutModule.textOutputter()` to get one.

Again, we'll need to add our new module to our component definition to tell
Dagger that it should use that module for our application:

```java
class CommandLineAtm {
  ...

  @Component(modules = {HelloWorldModule.class, SystemOutModule.class})
  interface CommandRouterFactory {
    CommandRouter router();
  }
}
```

Once again, nothing has changed about the _behavior_ of our application, but
it's now easy to write unit tests for our command without causing it to actually
write to `System.out`.

> **CONCEPTS**
>
> *   **[`@Provides`]** methods are concrete methods in a module that tell
>     Dagger that when something requests an instance of the type the method
>     returns, it should call that method to get an instance. Like [`@Inject`]
>     constructors, they can have parameters: those parameters are their
>     dependencies.
> *   [`@Provides`] methods can contain arbitrary code as long as they return an
>     instance of the provided type. They need not create a new instance on each
>     invocation.
>     *   This highlights an important aspect of Dagger (and dependency
>         injection as a whole): when a type is requested, whether or not a new
>         instance is created to satisfy that request is an implementation
>         detail. Going forward, we'll use the term "provided" instead of
>         "created", as that is more accurate for what is happening.

<section style="text-align: center" markdown="1">

[Previous](04-depending-on-interface) · [Next](06-new-command)

</section>

[`@Binds`]: https://dagger.dev/api/latest/dagger/Binds.html
[`@Inject`]: http://docs.oracle.com/javaee/7/api/javax/inject/Inject.html
[`@Module`]: https://dagger.dev/api/latest/dagger/Module.html
[`@Provides`]: https://dagger.dev/api/latest/dagger/Provides.html

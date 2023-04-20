---
layout: default
title: Initial Dagger setup
---

Let's change our example to actually use Dagger to create an instance of
`CommandRouter`. We'll start by creating a [`@Component`] interface:

```java
@Component
interface CommandRouterFactory {
  CommandRouter router();
}
```

`CommandRouterFactory` is a normal factory type for `CommandRouter`s. Its
implementation would call `new CommandRouter()` instead of our main method doing
it. But instead of us writing the implementation of `CommandRouterFactory`, we
can annotate it with [`@Component`] to have Dagger _generate_ an implementation
for us: `DaggerCommandRouterFactory`. Note that it has a static `create()`
method to give us an instance to use.

```java
class CommandLineAtm {
  public static void main(String[] args) {
    Scanner scanner = new Scanner(System.in);
    CommandRouterFactory commandRouterFactory =
        DaggerCommandRouterFactory.create();
    CommandRouter commandRouter = commandRouterFactory.router();

    while (scanner.hasNextLine()) {
      Result unused = commandRouter.route(scanner.nextLine());
    }
  }
}
```

In order for Dagger to know how to create a `CommandRouter`, we also need to add
an [`@Inject`] annotation to its constructor:

```java
final class CommandRouter {
  ...
  @Inject
  CommandRouter() {}
  ...
}
```

The [`@Inject`] annotation indicates to Dagger that when we ask for a
`CommandRouter`, Dagger should call `new CommandRouter()`.

> **Aside:** See [these instructions] for how to properly add Dagger to your
> build.

[these instructions]: https://github.com/google/dagger#installation

We haven't done much special yet, but we have the bare minimum for a Dagger
application! Run the application again to see it in action.

> **CONCEPTS**
>
> *   **[`@Component`]** tells Dagger to implement an `interface` or `abstract
>     class` that creates and returns one or more application objects.
>     *   Dagger will generate a class that implements the component type. The
>         generated type will be named `DaggerYourType` (or
>         `DaggerYourType_NestedType` for nested types)
> *   **[`@Inject`]** on a constructor tells Dagger how to instantiate that
>     class. We'll see more shortly.

<section style="text-align: center" markdown="1">

[Previous](01-setup) · [Next](03-first-command)

</section>

[`@Component`]: https://dagger.dev/api/latest/dagger/Component.html
[`@Inject`]: http://docs.oracle.com/javaee/7/api/javax/inject/Inject.html

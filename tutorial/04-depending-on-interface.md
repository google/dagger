---
layout: default
title: Depending on an interface
---

But wait: why does `CommandRouter`'s constructor need a `HelloWorldCommand`
specifically? Shouldn't it be able to use any kind of `Command`?

Let's instead specify `CommandRouter`'s dependency as a plain `Command`:

```java
@Inject
CommandRouter(Command command) {
  ...
}
```

But now Dagger doesn't know how to get an instance of `Command`. If you try to
compile, Dagger reports an error. Since `Command` is an _interface_ and can't
have an [`@Inject`] constructor, we need to give Dagger more information.

To do that, we can write a method annotated with [`@Binds`]:

```java
@Module
abstract class HelloWorldModule {
  @Binds
  abstract Command helloWorldCommand(HelloWorldCommand command);
}
```

This [`@Binds`] method tells Dagger that when something depends on a `Command`,
Dagger should provide a `HelloWorldCommand` object in its place. Notice that the
return type of the method, `Command`, is the type that Dagger now knows how to
provide, and the parameter type is the type that Dagger knows to use when
something depends on `Command`.

The method is abstract because just its declaration is enough to tell Dagger
what to do. Dagger does not actually call this method or provide an
implementation for it.

Notice that the [`@Binds`] method is declared in a type that's annotated with
[`@Module`]. Modules are collections of binding methods (methods annotated with
[`@Binds`] or a few other annotations as we'll see later) that give Dagger
instructions on how to provide instances. Unlike [`@Inject`], which goes
directly on a class's constructor, [`@Binds`] methods must be inside a module.

To tell Dagger to look for that [`@Binds`] method in `HelloWorldModule`, we add
it to the [`@Component`] annotation.

```java
@Component(modules = HelloWorldModule.class)
interface CommandRouterFactory {
  CommandRouter router();
}
```

> **Aside:** You might be wondering why it is that we don't need a [`@Module`]
> to tell Dagger about the [`@Inject`]-annotated classes we need as well. The
> answer is that Dagger already knows to look at those types because they appear
> somewhere in a component or module that Dagger is using. In the case of
> `CommandRouter`, it's the return type of the `CommandRouterFactory`'s entry
> point method. And in the case of `HelloWorldModule`, it's the parameter type
> of the [`@Binds`] method we just wrote. And before that, it appeared as a
> constructor parameter to `CommandRouter`, so Dagger learned about it
> transitively when looking at `CommandRouter`.

Now when Dagger looks to create a `CommandRouter` and sees that it needs a
`Command`, it will use the instructions in `HelloWorldModule` to create one.

With this change, our application should continue to work just like it did
before, but our `CommandRouter` class is no longer forced to only work with one
kind of `Command`.

> **CONCEPTS**
>
> *   **[`@Module`]s** are classes or interfaces that act as collections of
>     instructions for Dagger on how to construct dependencies. They're called
>     modules because they are _modular_: you can mix and match modules in
>     different applications and contexts.
> *   **[`@Binds`]** methods are one way to tell Dagger how to construct an
>     instance. They are abstract methods on modules that associate one type
>     that Dagger already knows how to construct (the method's parameter) with a
>     type that Dagger doesn't yet know how to construct (the method's return
>     type).

[Previous](03-first-command) · [Next](05-abstraction-for-output)
{@paragraph style="text-align: center"}

[`@Binds`]: https://dagger.dev/api/latest/dagger/Binds.html
[`@Component`]: https://dagger.dev/api/latest/dagger/Component.html
[`@Inject`]: http://docs.oracle.com/javaee/7/api/javax/inject/Inject.html
[`@Module`]: https://dagger.dev/api/latest/dagger/Module.html

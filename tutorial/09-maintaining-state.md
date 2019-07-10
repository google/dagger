---
layout: default
title: Maintaining state
---

There's a bug in our code—do you spot it? It's subtle, so you may need to look
closely.

In order to help find it, let's first create a `deposit` command:

```java
final class DepositCommand implements Command {
  ...

  @Inject
  DepositCommand(Database database, Outputter outputter) {  ...  }

  @Override
  public Status handleInput(List<String> input) {
    if (input.size() != 2) {
      return Status.INVALID;
    }

    Account account = database.getAccount(input.get(0));
    account.deposit(new BigDecimal(input.get(1));
    outputter.output(account.username() + " now has: " + account.balance());
    return Status.HANDLED;
  }
}
```

Let's try adding this command with a [`@Binds`]&nbsp;[`@IntoMap`] method like
the commands above. Let's put it in a module called `UserCommandsModule` that
will hold all of the commands that deal with a specific user.

```java
@Module
abstract class UserCommandsModule {
  @Binds
  @IntoMap
  @StringKey("deposit")
  abstract Command depositCommand(DepositCommand command);
}
```

Run the application with the following commands:

```
deposit colin 2
login colin
```

The second command shows that `colin` has a balance of 0. How could that be?

To help make this clearer, add `System.out.println("Creating a new " + this);`
statements to the `Database`, `LoginCommand`, and `DepositCommand` constructors.
Rerun the application and you'll see that _two_ databases are being created.
Dagger by default provides one `Database` object when `LoginCommand` requests it
and another when `DepositCommand` requests it.

In order to tell Dagger that they both need to share the same instance of
`Database`, we annotate the `Database` class with
[`@Singleton`](from the `javax.inject` package). We also annotate our
[`@Component`] type with [`@Singleton`] to declare that instances of classes
annotated with [`@Singleton`] should be shared among other objects that depend
on them in this component.

```java
@Singleton
final class Database { ... }

@Singleton
@Component
interface CommandRouterFactory {
  ...
}
```

Try rerunning again. Now the login and deposit commands share a single
`Database` instance.

> **CONCEPTS**
>
> *   **[`@Singleton`]** instructs Dagger to create only one instance of the
>     type for each instance of the component. It can be used on the class
>     declaration of a type that has an [`@Inject`] constructor, or on a
>     [`@Binds`] or [`@Provides`] method.
> *   It's not yet clear why you have to annotate the component with
>     [`@Singleton`] as well, but it will become clearer later.

<section style="text-align: center" markdown="1">

[Previous](08-user-specific-types) · [Next](10-deposit-after-login)

</section>

[`@Binds`]: https://dagger.dev/api/latest/dagger/Binds.html
[`@Component`]: https://dagger.dev/api/latest/dagger/Component.html
[`@Inject`]: http://docs.oracle.com/javaee/7/api/javax/inject/Inject.html
[`@IntoMap`]: https://dagger.dev/api/latest/dagger/multibindings/IntoMap.html
[`@Provides`]: https://dagger.dev/api/latest/dagger/Provides.html
[`@Singleton`]: http://docs.oracle.com/javaee/7/api/javax/inject/Singleton.html

---
layout: default
title: Adding a withdraw command
---

Let's add a command for withdrawing funds from your account:

```java
final class WithdrawCommand extends BigDecimalCommand {
  ...

  @Inject
  WithdrawCommand(Outputter outputter, Account account) {  ...  }

  @Override
  public void handleAmount(BigDecimal amount) {
    BigDecimal newBalance = account.balance().subtract(amount);
    if (newBalance.signum() < 0) {
      // output error
      return;
    } else {
      account.withdraw(amount);
      outputter.output("your new balance is: " + account.balance());
    }
  }
}
```

And add it to our `UserCommandsModule`, since this is another command that needs
a user to be logged in.

Now, let's say we want to make some of the values configurable:

*   Rather than just not allowing the user to withdraw more than they have in
    their account, set a minimum balance and don't let them withdraw an amount
    that would make them go below the minimum balance.
*   Set a maximum amount that can be withdrawn in a single transaction.

There are various ways you could do this, but for this tutorial, we want to
request each of those values in `WithdrawCommand`'s constructor.

Let's add parameters for the two values to the command's constructor and update
the withdrawal logic to check them:

```java
final class WithdrawCommand extends BigDecimalCommand {
  ...

  @Inject
  WithdrawCommand(
    Outputter outputter,
    Account account,
    BigDecimal minimumBalance,
    BigDecimal maximumWithdrawal) {
    ...
  }

  @Override
  public void handleAmount(BigDecimal amount) {
    if (amount.compareTo(maximumWithdrawal) > 0) {
      // output error
      return;
    }

    BigDecimal newBalance = account.balance().subtract(amount);
    if (newBalance.compareTo(minimumBalance) < 0) {
      // output error
    } else {
      account.withdraw(amount);
      outputter.output("your new balance is: " + account.balance());
    }
  }
}
```

Now let's create a new module with [`@Provides`] methods for the two values:

```java
@Module
interface AmountsModule {
  @Provides
  static BigDecimal minimumBalance() {
    return BigDecimal.ZERO;
  }

  @Provides
  static BigDecimal maximumWithdrawal() {
    return new BigDecimal(1000);
  }
}
```

And add the module to our `CommandProcessorFactory` component:

```java
@Component(modules = {..., AmountsModule.class})
interface CommandProcessorFactory {
  ...
}
```

If you try to compile now, you'll find that Dagger raises an error: there are
two binding methods for `BigDecimal` and it doesn't know which it should use.

To differentiate between different things of the same Java type in a case like
this, we use _qualifiers_. A qualifier is an annotation that can be used to give
Dagger information it can use to distinguish between instances of the same type.
Qualifiers are annotations that are themselves annotated with [`@Qualifier`].
Here's how we might define annotations for qualifying our `BigDecimal` values:

```java
@Qualifier
@Documented
@Retention(RUNTIME)
@interface MinimumBalance {}

// @MaximumWithdrawal defined the same way
```

And here's how we'll apply them in our module:

```java
@Module
interface AmountsModule {
  @Provides
  @MinimumBalance
  static BigDecimal minimumBalance() {
    return BigDecimal.ZERO;
  }

  @Provides
  @MaximumWithdrawal
  static BigDecimal maximumWithdrawal() {
    return new BigDecimal(1000);
  }
}
```

Now we can update `WithdrawCommand`'s constructor to depend on the qualified
`BigDecimal`s rather than just plain `BigDecimal`.

```java
final class WithdrawCommand implements Command {
  ...

  @Inject
  WithdrawCommand(
    Outputter outputter,
    Account account,
    @MinimumBalance BigDecimal minimumBalance,
    @MaximumWithdrawal BigDecimal maximumWithdrawal) { ... }

  ...
}
```

The code should compile again and we can make changes in `AmountsModule` to
adjust the minimum balance and maximum withdrawal amounts if desired.

> **CONCEPTS**
>
> *   **[`@Qualifier`]** annotations are used to differentiate between instances
>     of the same type that are unrelated.
>     *   Contrast this with [`@IntoSet`] and [`@IntoMap`], where the collected
>         objects are used together.
> *   Qualifiers are often, but certainly not always, used with common data
>     types such as primitive types and `String`, which may be used in many
>     places in a program for very different reasons.

<section style="text-align: center" markdown="1">

[Previous](10-deposit-after-login) · [Next](12-logging-out)

</section>

[`@IntoMap`]: https://dagger.dev/api/latest/dagger/multibindings/IntoMap.html
[`@IntoSet`]: https://dagger.dev/api/latest/dagger/multibindings/IntoSet.html
[`@Provides`]: https://dagger.dev/api/latest/dagger/Provides.html
[`@Qualifier`]: http://docs.oracle.com/javaee/7/api/javax/inject/Inject.html

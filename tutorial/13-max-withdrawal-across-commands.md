---
layout: default
title: Enforcing the maximum withdrawal across commands
---

We've configured the maximum withdrawal, but multiple "withdraw \<maximum>"
commands can be executed back-to-back. What if we wanted to enforce that all
withdraw commands never exceed the maximum? Furthermore, depositing money to the
ATM should increase the maximum withdrawal for that session.

We can add a stateful object, `WithdrawalLimiter` to keep track of the initial
maximum and all changes to the balance within a session:

```java
final class WithdrawalLimiter {
  private BigDecimal remainingWithdrawalLimit;

  @Inject
  WithdrawalLimiter(@MaximumWithdrawal BigDecimal maximumWithdrawal) {
    this.remainingWithdrawalLimit = maximumWithdrawal;
  }

  void recordDeposit(BigDecimal amount) { ... }
  void recordWithdrawal(BigDecimal amount) { ... }
}
```

We can add the `WithdrawalLimiter` to the [`@Inject`] constructors of
`DepositCommand` and `WithdrawCommand`, calling `recordWithdrawal()` and
`recordDeposit()` appropriately after a successful command.

```java
// in WithdrawCommand
BigDecimal remainingWithdrawalLimit = withdrawalLimiter.remainingWithdrawalLimit();
if (amount.compareTo(remainingWithdrawalLimit) > 0) {
  outputter.output(
      String.format(
          "you may not withdraw %s; you may withdraw %s more in this session",
          amount, remainingWithdrawalLimit));
}

...

account.withdraw(amount);
withdrawalLimiter.recordWithdrawal(amount);

// in DepositCommand
account.deposit(amount);
withdrawalLimiter.recordDeposit(amount);
```

If you try that, you'll notice one small issue: the `WithdrawalLimiter` used by
each command is not the same; updates from `DepositCommand` will not propagate
to `WithdrawCommand`.

We previously solved a similar issue by annotating `Database` with
[`@Singleton`]. In this case, [`@Singleton`] isn't applicable because we want
one `WithdrawalLimiter` per login. Instead, we can define a new annotation:

```java
@Scope
@Documented
@Retention(RUNTIME)
@interface PerSession {}
```

Annotating both `WithdrawalLimiter` and `UserCommandsRouter` with `@PerSession`
indicates to Dagger that a single `WithdrawalLimiter` should be created for
every instance of `UserCommandsRouter`.

```java
@PerSession
final class WithdrawalLimiter { ... }

@PerSession
@Subcomponent(...)
interface UserCommandsRouter { ... }
```

Rerun the application to see that deposits can now correctly update the maximum
withdrawal for the current session. Logout and log back in to see that for each
new session, a new maximum withdrawal exists.

Note the difference between the [`@Singleton`] `Database` and the `@PerSession`
`WithdrawalLimiter`. There is one `Database` instance shared among all the
objects in the single `CommandProcessorFactory` instance, but there is a
separate instance of `WithdrawalLimiter` for each instance of
`UserCommandsRouter`.

> **CONCEPTS**
>
> *   A **[`@Scope`]** annotation instructs Dagger to provide one shared
>     instance for all the requests for that type within an instance of the
>     (sub)component that shares the same annotation.
>     *   Note that [`@Singleton`], which we described previously, is really
>         just another scope annotation!
> *   The lifetime of a scoped instance is directly related to the lifetime of
>     the component with that scope.
> *   Note that the name of the scope is meaningless; even multiple instances of
>     a [`@Singleton`]-annotated type can be created if multiple
>     [`@Singleton`]-annotated [`@Component`]s are instantiated in a single JVM.

[Previous](12-logging-out) · [Next](14-avoiding-recursive-logins)
{@paragraph style="text-align: center"}

[`@Component`]: https://dagger.dev/api/latest/dagger/Component.html
[`@Inject`]: http://docs.oracle.com/javaee/7/api/javax/inject/Inject.html
[`@Scope`]: https://dagger.dev/api/latest/dagger/Scope.html
[`@Singleton`]: http://docs.oracle.com/javaee/7/api/javax/inject/Singleton.html

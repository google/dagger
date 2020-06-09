---
layout: default
title: Hilt Testing Philosophy
---

## Overview

This page aims to explain the testing practices that Hilt is built upon. A lot
of the APIs and functionality in Hilt (and certain lack of functionality as
well) were created on an unstated philosophy of what makes a good test. The
notion of a good test is not universally agreed upon though, so this document
aims to clarify the Hilt team’s testing philosophy.

## What to test

Hilt encourages testing as much as possible from an outside user’s perspective.
An outside user’s perspective can mean many things. It could mean the actual
users of your app or service, but it can also be more scoped down to the users
of your API or class.

The key part is that tests shouldn’t encode implementation details. Relying on
implementation details, like checking that an internal method has been called,
causes the test to be brittle. If a refactoring changes the name of an internal
method, a good test should not have to be updated. The only changes that should
break existing tests are those that are changing your user-visible behavior.

## Using real dependencies

The Hilt testing philosophy doesn't prescribe strict rules such as every class
must have its own test. In fact, usually such a rule would violate the above
principle of testing from the user's perspective. Tests should be only as small
as necessary to make them convenient to write and run (e.g. small enough to be
fast or not resource intensive). All else being equal, tests should, in this
order, prefer to:

*   Use the real code for a dependency
*   Use a standard fake provided by the library
*   Use a mock as a last resort

However, there are trade offs. Using real dependencies/real DI in tests may be
prohibitively difficult for one or both of the following reasons:

*   Setting up and instantiating the real dependency/real DI is too much
    boilerplate or repeated code.
*   Using the real dependency introduces a performance tradeoff (like needing to
    start a backend server).

Hilt was built to solve the first issue of set up (more on that below).
Performance can be an issue but is often not a problem for most dependencies.
This likely is only an issue when using dependencies with significant I/O. So,
if a test can be written more conveniently and robustly by using more real
dependencies without significantly degrading performance, it should be written
using those dependencies. For those classes that due come with large negative
effects in tests, Hilt provides a means to switch out the bindings.

Using more real dependencies has significant advantages:

*   Real dependencies are more likely to catch real problems. They cannot get
    out of date like mocks can.
*   Combined with the above principle of testing from the user’s perspective,
    you likely need to write fewer tests for the same coverage.
*   A test breakage is more likely to indicate a real problem instead of a
    misconfigured fake or mock (and conversely a test passing is more likely to
    mean code actually works).
*   Using more real dependencies often goes along with the above principle of
    testing from the user’s perspective since they will likely not be able to
    swap your dependencies.

If the real dependency is not possible to use though, a standard fake provided
by the library is usually the next best option. A standardized fake is better
than a mock because it is more likely to be in sync with the production code if
it is maintained by the library authors and thus provides more robust coverage.
For these reasons, mocks are typically a last resort.

## Hilt, DI, and testing

With those foundations explained, we now get into the specifics of Hilt, DI, and
testing. In line with the philosophy of using real objects, Hilt’s answer is to
use dependency injection / Dagger in tests. This is more realistic because
objects are created as they would be in production code. It means that tests are
not any more brittle than production code would be and it makes it easier to use
real objects. In fact, for types that have `@Inject` constructors, it is
actually easier and less code to follow this advice and use the real code than
it is to configure and bind a mock.

Unfortunately, this kind of testing without Hilt has traditionally been
difficult in practice due to the boilerplate and extra work to set up Dagger in
the tests. However, Hilt generates the boilerplate for you and has a clear story
for setting up different configurations of bindings for tests when you do need a
fake or a mock. With Hilt, this issue should no longer be a deterrent to writing
tests with Dagger and therefore easily using real dependencies.

## Downsides of the alternative

The alternative of not using Dagger in unit tests is actually pretty common
advice. This, unfortunately, ends up having significant drawbacks, though it is
understandable advice given the difficulty of using Dagger in tests without
Hilt.

For example, let’s say we have a Foo class that we want to test:

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
final class Foo {
  @Inject Foo(Bar bar) {...}
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
class Foo @Inject constructor(bar: Bar) {
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

In this case, if not using Dagger, the test would directly instantiate `Foo` by
just calling the constructor. At first glance this seems like a very simple and
reasonable thing to do; however, things start to unravel as you try to supply an
instance of `Bar` into `Foo`'s constructor.

### Direct instantiation encourages mocks

From the previously discussed testing philosophy, we should prefer to get a real
`Bar` class. However, how should we do that? This actually is just a recursion
of getting a real `Foo` class to test: you would have to instantiate it yourself
and if `Bar` has dependencies of its own, then that would require similarly
instantiating those. In order to not go too deep you would likely need to start
using a fake or a mock, not because of the effects on speed or performance of
the test, but simply to avoid too much brittle boilerplate that causes
maintenance problems. This is not a good reason to use a fake or a mock, and yet
you are forced to do so anyway.

An alternative, as discussed above, is to use a standard fake, which may help
cut dependencies and reduce the maintenance burden of direct instantiation.
However, even that is not always that simple. Many times a good fake will
similarly have dependencies it needs. For example, a `FakeBar` may end up
needing to take in a `FakeClock` if the real `Bar` took a `Clock`. This is
because a `FakeClock` is often a coordination point between different classes.
(Imagine if `Foo` had another dependency `Baz` that also used a clock, you would
want the `FakeBaz` to use the same `FakeClock` instance so things are
coordinated when time is advanced). Managing these dependencies can quickly get
out of hand.

This usually leads test authors to mocks. The mock solves the issue of tediously
following these dependency chains, but has significant drawbacks in that it can
easily get out of date silently and make the test useless in its overall goal of
finding real bugs. Because no one checks the mock behavior besides the test
author, this usually means that after enough time, there is a decent likelihood
that the test is no longer testing a useful scenario.

### Direct instantiation encodes implementation details in the test

Direct instantiation also breaks the philosophy of not encoding implementation
details in a test because the constructor call encodes details of its
dependencies. If `Bar` were an `@Inject` constructor type, there is no reason a
user of `Foo` needs to know about the existence of the `Bar` class as it could
easily be an implementation detail from refactoring logic in `Foo` into another
class private to the library.

To illustrate this point, consider if `Foo` had two dependencies like `Foo(Bar,
Baz)`. In Dagger, switching the order of these parameters on the `@Inject`
constructor is a no-op. Yet if we were to test `Foo` via direct instantiation,
we’d still have to update the test. Similarly, adding a usage of a new `@Inject`
class or an optional binding would similarly be an invisible change for
production users of the class, yet the test would still need to be updated.

## Summary

Hilt was designed to fix the downside of using Dagger in tests in order to allow
easy testing with real dependencies. Tests written using Hilt will have a better
overall experience if they follow these principles.

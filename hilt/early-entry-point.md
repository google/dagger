---
layout: default
title: Early entry points
---

The [`@EarlyEntryPoint`] annotation provides an escape hatch when a Hilt
entry point needs to be created before the singleton component is available in
a Hilt test.

Note that, although [`@EarlyEntryPoint`] and [`EarlyEntryPoints`] are
mostly used in production code, they only have an effect during Hilt tests. In
production, these entry points behave the same as [`@EntryPoint`] and
[`EntryPoints`], respectively.

## Background

In a [Hilt test], the singleton component's lifetime is scoped to the
lifetime of a test case rather than the lifetime of the [`Application`].
This is useful to prevent leaking state across test cases, but it makes
it impossible to access entry points from a component outside of a test case.

To get a better understanding of why/when this becomes an issue, let's look at
a typical lifecycle of an Android Gradle instrumentation test.

```
# Typical Application lifecycle during an Android Gradle instrumentation test
- Application created
    - Application.onCreate() called
    - Test1 created
        - SingletonComponent created
        - testCase1() called
    - Test1 created
        - SingletonComponent created
        - testCase2() called
    ...
    - Test2 created
        - SingletonComponent created
        - testCase1() called
    - Test2 created
        - SingletonComponent created
        - testCase2() called
    ...
- Application destroyed
```

As the lifecycle above shows, `Application#onCreate()` is called before any
SingletonComponent can be created, so calling an entry point from
`Application#onCreate()` is not possible. (For the same reason, there are
similar issues with calling entry points from `ContentProvider#onCreate()`).

While these cases should be rare, sometimes they are unavoidable. This is
where `@EarlyEntryPoint` comes in.

## Usage

Annotating an entry point with [`@EarlyEntryPoint`] instead of [`@EntryPoint`]
allows the entry point to be called at any point during the lifecyle of a test
application. (Note that an [`@EarlyEntryPoint`] can only be installed in the
[`SingletonComponent`]). For example:

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@EarlyEntryPoint
@InstallIn(SingletonComponent.class)
public interface FooEntryPoint {
  Foo foo();
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@EarlyEntryPoint
@InstallIn(SingletonComponent::class)
interface FooEntryPoint {
  fun foo(): Foo
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

Once annotated with `@EarlyEntryPoint`, all usages of the entry point must
go through [`EarlyEntryPoints#get()`] (rather than [`EntryPoints#get()`]) to
get an instance of the entry point. This requirement makes it clear at the call
site which component will be used during a Hilt test. For example:

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
// A base application used in a Hilt test that injects objects in onCreate
public abstract class BaseTestApplication extends Application {
  @Override
  public void onCreate() {
    super.onCreate();

    // Entry points annotated with @EarlyEntryPoint must use
    // EarlyEntryPoints rather than EntryPoints.
    foo = EarlyEntryPoints.get(this, FooEntryPoint.class).foo();
  }
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
// A base application used in a Hilt test that injects objects in onCreate
public abstract class BaseTestApplication: Application {
  override fun onCreate() {
    super.onCreate()

    // Entry points annotated with @EarlyEntryPoint must use
    // EarlyEntryPoints rather than EntryPoints.
    foo = EarlyEntryPoints.get(this, FooEntryPoint::class).foo()
  }
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

## Caveats

The component used with `EarlyEntryPoints` does not share any state with the
singleton component used for a given test case. Even `@Singleton` scoped
bindings will ***not*** be shared.

The component used with `EarlyEntryPoints` does not have access to any
test-specific bindings (i.e. bindings created within a specific test class such
as [`@BindValue`](testing.md#bind-value) or a
[nested module](testing.md#nested-modules)).

Finally, the component used with `EarlyEntryPoints` lives for the lifetime
of the application, so it can leak state across multiple test cases (e.g. in
Android Gradle instrumentation tests).

## When ***not*** to use EarlyEntryPoint

Most usages of `@EarlyEntryPoint` are needed to allow calling entry
points from within `Application#onCreate()` or `ContentProvider#onCreate()`.
However, before switching to `@EarlyEntryPoint`, try the alternatives listed
below.

### Entry points for Application getter methods

If the entry point is used to initialize a field that will later be returned in
a getter method, consider removing the field and getter method and replacing it
with a `@Singleton` scoped binding that other classes can inject directly
rather than going through the application class.

If the getter method is required (e.g. the application must extend an interface
that requires it to be overriden) then try replacing the field with a
`@Singleton` scoped binding and calling `EntryPoints.get()` lazily from the
getter method.

### Entry points for initialization/configuration

If the entry point is used to perform initialization/configuration (e.g.
setting up a logger or prefetching data) then first consider whether this work
is necessary for your tests. Most tests, e.g. tests for activities and fragments
should not be dependent on this initialization to work properly, since
activities and fragments should generally be designed to be reusable in other
applications.

If your test needs the initialization/configuration, consider whether it's okay
to only run the initialization/configuration once and share any state of that
run between tests. If that's not okay, then you may need to consider moving the
logic into a [`TestRule`] instead.

[`@EarlyEntryPoint`]:(https://dagger.dev/api/latest/dagger/hilt/android/EarlyEntryPoint.html)
[`EarlyEntryPoints`]:(https://dagger.dev/api/latest/dagger/hilt/android/EarlyEntryPoints.html)
[entry point]:(https://dagger.dev/api/latest/dagger/hilt/EntryPoint.html)
[Hilt test]:(https://dagger.dev/api/latest/dagger/hilt/android/testing/HiltAndroidTest.html)
[`Application`]:(https://developer.android.com/reference/android/app/Application.html)
[`EarlyEntryPoints#get()`]:(https://dagger.dev/api/latest/dagger/hilt/android/EarlyEntryPoint.html)
[`EntryPoints#get()`]:(https://dagger.dev/api/latest/dagger/hilt/EntryPoints.html)
[`TestRule`]:(https://junit.org/junit4/javadoc/4.12/index.html?org/junit/rules/TestRule.html)
[`@EntryPoint`]:(https://dagger.dev/api/latest/dagger/hilt/EntryPoint.html)
[`EntryPoints`]:(https://dagger.dev/api/latest/dagger/hilt/EntryPoints.html)
[`SingletonComponent`]:(https://dagger.dev/api/latest/dagger/hilt/components/SingletonComponent.html)

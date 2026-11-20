---
layout: default
title: Entry Points
---

## What is an entry point?

An entry point is the boundary where you can get Dagger-provided objects from
code that cannot use Dagger to inject its dependencies. It is the point where
code first enters into the graph of objects managed by Dagger.

If you're already familiar with Dagger components, an entry point is just an
interface that the Hilt generated component will extend.

## When do you need an entry point?

You will need an entry point when interfacing with non-Dagger libraries or
Android components that are not yet supported in Hilt and need to get access to
Dagger objects.

In general though, most entry points will be at Android instantiated locations
like the activities, fragments, etc.
[`@AndroidEntryPoint`](android-entry-point.md) is a specialized tool to handle
the definition of entry points and access to the entry points (among other
things) for these classes. Since this is already handled specially for those
Android classes, for the following docs, we'll assume the entry point is needed
in some other type of class.

## How to use an entry point?

### Create an EntryPoint

To create an entry point, define an interface with an accessor method for each
binding type needed (including its qualifier) and mark the interface with the
[`@EntryPoint`](https://dagger.dev/api/latest/dagger/hilt/EntryPoint.html)
annotation. Then add `@InstallIn` to specify the component in which to install
the entry point.

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@EntryPoint
@InstallIn(SingletonComponent.class)
public interface FooBarInterface {
  @Foo Bar bar();
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@EntryPoint
@InstallIn(SingletonComponent::class)
interface FooBarInterface {
  @Foo fun bar(): Bar
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

### Access an EntryPoint {#access-an-entrypoint}

To access an entry point, use the `EntryPoints` class passing as a parameter the
component instance or the `@AndroidEntryPoint` object which acts as a component
holder. Make sure the component you pass in matches the `@InstallIn` annotation
on the
[`@EntryPoint`](https://dagger.dev/api/latest/dagger/hilt/EntryPoint.html)
interface that you pass in as well.

Using the entry point interface we defined above:

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
Bar bar = EntryPoints.get(applicationContext, FooBarInterface.class).bar();
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
val bar = EntryPoints.get(applicationContext, FooBarInterface::class.java).bar()
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

<!-- TODO(kuanyingchou): Add link back for EntryPointAccessors once we have KDoc
or Dokka ready. -->
Additionally, the methods in `EntryPointAccessors` are more appropriate and type
safe for retrieving entry points from the standard Android components.

## Best practice: where to define an entry point interface?

If implementing a class instantiated from a non-Hilt library and a Foo class is
needed from Dagger, should the entry point interface be defined with the using
class or with Foo?

In general, the answer is that the entry point should be defined with the using
class since that class is the reason for needing the entry point interface, not
Foo. If that class later needs more dependencies, extra methods can easily be
added to the entry point interface to get them. Essentialy, the entry point
interface acts in place of the `@Inject` constructor for that class. If instead
the entry point were defined with `Foo`, then other people may be confused about
if they should inject `Foo` or use the entry point interface. It would also
result in more entry point interfaces being added if other dependencies are
needed in the future.

#### Best practice

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
public final MyClass extends NonHiltLibraryClass {
  // No @Inject because this isn't instantiated in a Dagger context
  public MyClass() {}

  @EntryPoint
  @InstallIn(SingletonComponent.class)
  public interface MyClassInterface {
    Foo foo();

    Bar bar();
  }

  void doSomething(Context context) {
    MyClassInterface myClassInterface =
        EntryPoints.get(applicationContext, MyClassInterface.class);
    Foo foo = myClassInterface.foo();
    Bar bar = myClassInterface.bar();
  }
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
// No @Inject because this isn't instantiated in a Dagger context public
class MyClass : NonHiltLibraryClass() {

  @EntryPoint
  @InstallIn(SingletonComponent::class)
  interface MyClassInterface {
    fun foo(): Foo

    fun bar(): Bar
  }

  fun doSomething(context: Context) {
    val myClassInterface =
        EntryPoints.get(applicationContext, MyClassInterface::class.java)
    val foo = myClassInterface.foo()
    val bar = myClassInterface.bar()
  }
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

#### Bad practice

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@Module
@InstallIn(SingletonComponent.class)
public final class FooModule {
  @Provides
  Foo provideFoo() {
    return new Foo();
  }

  @EntryPoint
  @InstallIn(SingletonComponent.class)
  public interface FooInterface {
    Foo foo();
  }
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object FooModule {
  @Provides
  fun provideFoo(): Foo {
    return Foo()
  }

  @EntryPoint
  @InstallIn(SingletonComponent::class)
  interface FooInterface {
    fun foo(): Foo
  }
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

## Visibility

All types returned from an entry point's method **must be public.** This is
because the generated Dagger component, which is often not in the same package,
must implement the entry point method.

## Pruning unused entry points

Hilt treats every `@EntryPoint` method as used because Dagger cannot tell at
compile time whether a method will be called. However, when an entry point
method is only ever used by a specific set of bindings, [`@OnlyUsedBy`] lets you
declare those callers so Dagger can prune the method whenever those bindings are
unused:

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@Singleton
public final class AuthManager {
  @EntryPoint
  @InstallIn(ActivityComponent.class)
  interface ActivityEntryPoint {
    @OnlyUsedBy(AuthManager.class)
    AuthUiHelper authUiHelper();
  }

  @Inject
  AuthManager() {}

  public void startAuth(Activity activity) {
    EntryPoints.get(activity, ActivityEntryPoint.class)
        .authUiHelper()
        .launch();
  }
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@Singleton
class AuthManager @Inject constructor() {
  @EntryPoint
  @InstallIn(ActivityComponent::class)
  internal interface ActivityEntryPoint {
    @OnlyUsedBy(AuthManager::class)
    fun authUiHelper(): AuthUiHelper
  }

  fun startAuth(activity: Activity) {
    EntryPoints.get(activity, ActivityEntryPoint::class.java)
        .authUiHelper()
        .launch()
  }
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

See [Prunable component methods][prunable-component-methods] for more details,
warnings, and [`@BindingIdentifier`] usage.

[`@BindingIdentifier`]: https://dagger.dev/api/latest/dagger/pruning/BindingIdentifier.html
[`@OnlyUsedBy`]: https://dagger.dev/api/latest/dagger/pruning/OnlyUsedBy.html
[prunable-component-methods]: https://dagger.dev/dev-guide/basic-usage.html#prunable-component-methods

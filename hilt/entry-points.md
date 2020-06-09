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
@InstallIn(ApplicationComponent.class)
public interface FooBarInterface {
  @Foo Bar getBar();
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@EntryPoint
@InstallIn(ApplicationComponent::class)
interface FooBarInterface {
  @Foo fun getBar(): Bar
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

### Access an EntryPoint

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
Bar bar = EntryPoints.get(applicationContext, FooBarInterface.class).getBar();
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
val bar = EntryPoints.get(applicationContext, FooBarInterface::class.java).getBar()
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

Additionally, the methods in
[`EntryPointAccessors`](https://dagger.dev/api/latest/dagger/hilt/android/EntryPointAccessors.html)
are more appropriate and type safe for retrieving entry points from the standard
Android components.

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
  @InstallIn(ApplicationComponent.class)
  public interface MyClassInterface {
    Foo getFoo();

    Bar getBar();
  }

  void doSomething(Context context) {
    MyClassInterface myClassInterface =
        EntryPoints.get(applicationContext, MyClassInterface.class);
    Foo foo = myClassInterface.getFoo();
    Bar bar = myClassInterface.getBar();
  }
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
// No @Inject because this isn't instantiated in a Dagger context public 
class MyClass : NonHiltLibraryClass() {

  @EntryPoint
  @InstallIn(ApplicationComponent::class)
  interface MyClassInterface {
    fun getFoo(): Foo

    fun getBar(): Bar
  }

  fun doSomething(context: Context) {
    MyClassInterface myClassInterface =
        EntryPoints.get(applicationContext, MyClassInterface::class.java)
    val foo = myClassInterface.getFoo()
    val bar = myClassInterface.getBar()
  }
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

#### Bad practice

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@Module
@InstallIn(ApplicationComponent.class)
public final class FooModule {
  @Provides
  Foo provideFoo() {
    return new Foo();
  }

  @EntryPoint
  @InstallIn(ApplicationComponent.class)
  public interface FooInterface {
    Foo getFoo();
  }
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@Module
@InstallIn(ApplicationComponent::class)
object FooModule {
  @Provides
  fun provideFoo(): Foo {
    return Foo()
  }

  @EntryPoint
  @InstallIn(ApplicationComponent::class)
  interface FooInterface {
    fun getFoo(): Foo
  }
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

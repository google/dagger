---
layout: default
title: Custom Components
---

## Is a custom component needed?

Hilt has predefined components for Android that are managed for you. However,
there may be situations where the standard Hilt components do not match the
object lifetimes or needs of a particular feature. In these cases, you may want
a custom component. However, before creating a custom component, consider if you
really need one as not every place where you can logically add a custom
component deserves one.

For example, consider a background task. The task has a reasonably well-defined
lifetime that could make sense for a scope. Also, if there were a request object
for that task, binding that into Dagger may save some work passing that around
as a parameter. However, for most background tasks, a component really isn't
necessary and only adds complexity where simply passing a couple objects on the
call stack is simpler and sufficient. Before commiting to adding a custom
component, consider the following drawbacks.

Adding a custom component has the following drawbacks:

*   Each component/scope adds cognitive overhead.
*   They can complicate the graph with combinatorics (e.g. if the component is a
    child of the `ViewComponent` conceptually, two components likely need to be
    added for `ViewComponent` and `ViewWithFragmentComponent`).
*   Components can have only one parent. The component hierarchy can't form a
    diamond. Creating more components increases the likelihood of getting into a
    situation where a diamond dependency is needed. Unfortunately, there is no
    good solution to this diamond problem and it can be difficult to predict and
    avoid.
*   Custom components work against standardization. The more custom components
    are used, the harder it is for shared libraries.

With those in mind, these are some criteria you should use for deciding if a
custom component is needed:

*   The component has a well-defined lifetime associated with it.
*   The concept of the component is well-understood and widely applicable. Hilt
    components are global to the app so the concepts should be applicable
    everywhere. Being globally understood also combats some of the issues with
    cognitive overhead.
*   Consider if a non-Hilt (regular Dagger) component is sufficient. For
    components with a limited purpose sometimes it is better to use a non-Hilt
    component. For example, consider a production component that represents a
    single background task. Hilt components excel in situations where code needs
    to be contributed from possibly disjoint/modular code. If your component
    isn't really meant to be extensible, it may not be a good match for a Hilt
    custom component.


### Custom component limitations

Custom component definitions currently have some limitations:

*   Components must be a direct or indirect child of the `ApplicationComponent`.
*   Components may not be inserted between any of the standard components. For
    example, a component cannot be added between the `ActivityComponent` and the
    `FragmentComponent`.

## Adding a custom Hilt component

To create a custom Hilt component, create a class annotated with
[`@DefineComponent`](https://dagger.dev/api/latest/dagger/hilt/DefineComponent.html).
This will be the class used in `@InstallIn` annotations.

The parent of your component should be defined in the value of the
`@DefineComponent` annotation. Your `@DefineComponent` class can also be
annotated with a scope annotation to allow scoping objects to this component.

For example:

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@DefineComponent(parent = ApplicationComponent.class)
interface MyCustomComponent {}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@DefineComponent(parent = ApplicationComponent::class)
interface MyCustomComponent
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

A builder interface must also be defined. This interface will be injectable from
the parent component and will be the interface for creating new instances of
your component. As these are custom components, once instances are built, it
will be your job to hold on to or release component instances at the appropriate
time.

Builder interfaces are defined by marking an interface with
`@DefineComponent.Builder`. Builders must have a method that returns the
`@DefineComponent` type. They may also have additional methods (like
`@BindsInstance` methods) that a normal Dagger
[component builder](https://dagger.dev/api/latest/dagger/Component.Builder.html)
may have.

For example:

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@DefineComponent.Builder
interface MyCustomComponentBuilder {
  MyCustomComponentBuilder fooSeedData(@BindsInstance Foo foo);
  MyCustomComponent build();
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@DefineComponent.Builder
interface MyCustomComponentBuilder {
  fun fooSeedData(@BindsInstance foo: Foo): MyCustomComponentBuilder
  fun build(): MyCustomComponent
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

While the `@DefineComponent.Builder` class can be nested within the
`@DefineComponent`, it is usually better as a separate class. It may be
separated into a different class as long as it is a transitive dependency of
the `@HiltAndroidApp` application or `@HiltAndroidTest` test. Since the
`@DefineComponent` class is referenced in many places via `@InstallIn`, it may
be better to separate the builder so that dependencies in the builder do not
become transitive dependencies of every module installed in the component.

For the same reason of avoiding excessive dependencies, methods are not allowed
on the `@DefineComponent` interface. Instead, Dagger objects should be accessed
via [entry points](entry-points.md).

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@EntryPoint
@InstallIn(MyCustomComponent.class)
interface MyCustomEntryPoint {
  Bar getBar();
}

public final class CustomComponentManager {
  private final MyCustomComponentBuilder componentBuilder;

  @Inject CustomComponentManager(MyCustomComponentBuilder componentBuilder) {
    this.componentBuilder = componentBuilder;
  }

  void doSomething(Foo foo) {
    MyCustomComponent component = componentBuilder.fooSeedData(foo).build();
    Bar bar = EntryPoints.get(component, MyCustomEntryPoint.class).getBar();

    // Don't forget to hold on to the component instance if you need to!
  }
```
{: .c-codeselector__code .c-codeselector__code_java }

```kotlin
@EntryPoint
@InstallIn(MyCustomComponent::class)
interface MyCustomEntryPoint {
  fun getBar(): Bar
}

class CustomComponentManager @Inject constructor(
    componentBuilder: MyCustomComponentBuilder) {

  fun doSomething(foo: Foo) {
    val component = componentBuilder.fooSeedData(foo).build();
    val bar = EntryPoints.get(component, MyCustomEntryPoint::class.java).getBar()

    // Don't forget to hold on to the component instance if you need to!
  }
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

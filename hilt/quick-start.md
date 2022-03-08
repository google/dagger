---
layout: default
title: Quick Start Guide
---

## Introduction

Hilt makes it easy to add dependency injection to your Android app. This
tutorial will guide you through bootstrapping an existing app to use Hilt.

For more on the basic concepts of Hilt's components, check out
[Hilt Components](components.md).

## Gradle vs non-Gradle users {#gradle-plugin}

For Gradle users, the [Hilt Gradle plugin](gradle-setup.md#hilt-gradle-plugin)
makes usages of some Hilt annotations easier by avoiding references to Hilt
generated classes.

Without the Gradle plugin, the base class must be specified in the annotation
and the annotated class must extend the generated class:

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@HiltAndroidApp(MultiDexApplication.class)
public final class MyApplication extends Hilt_MyApplication {}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@HiltAndroidApp(MultiDexApplication::class)
class MyApplication : Hilt_MyApplication()
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

With the Gradle plugin the annotated class can extend the base class directly:

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@HiltAndroidApp
public final class MyApplication extends MultiDexApplication {}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@HiltAndroidApp
class MyApplication : MultiDexApplication()
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

Further examples assume usage of the Hilt Gradle plugin.

## Hilt Application

All apps using Hilt _must_ contain an
[`Application`](https://developer.android.com/reference/android/app/Application.html)
class annotated with
[`@HiltAndroidApp`](https://dagger.dev/api/latest/dagger/hilt/android/HiltAndroidApp.html).
`@HiltAndroidApp` kicks off the code generation of the
[Hilt components](components.md) and also generates a base class for your
application that uses those generated components. Because the code generation
needs access to all of your modules, the target that compiles your `Application`
class also needs to have all of your Dagger modules in its transitive
dependencies.

Just like other Hilt [Android entry points](android-entry-point.md),
Applications are members injected as well. This means you can use injected
fields in the Application after `super.onCreate()` has been called.

**Note:** Since all injected fields are created at the same time in `onCreate`,
if an object is only needed later or conditionally, remember that you can use a
`Provider` to defer injection. Especially in the `Application` class which is on
the critical startup path, avoiding unnecessary injections can be important to
performance.
{: .c-callouts__note }

For example, take the class called `MyApplication` that extends
`MyBaseApplication` and has a member variable `Bar`:

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
public final class MyApplication extends MyBaseApplication {
  @Inject Bar bar;

  @Override public void onCreate() {
    super.onCreate();

    MyComponent myComponent =
        DaggerMyComponent
            .builder()
            ...
            .build();

    myComponent.inject(this);
  }
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
class MyApplication : MyBaseApplication() {
  @Inject lateinit var bar: Bar

  override fun onCreate() {
    super.onCreate()

    val myComponent =
        DaggerMyComponent
            .builder()
            ...
            .build()

    myComponent.inject(this)
  }
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

With Hilt's members injection, the above code becomes:

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@HiltAndroidApp
public final class MyApplication extends MyBaseApplication {
  @Inject Bar bar;

  @Override public void onCreate() {
    super.onCreate(); // Injection happens in super.onCreate()
    // Use bar
  }
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@HiltAndroidApp
class MyApplication : MyBaseApplication() {
  @Inject lateinit var bar: Bar

  override fun onCreate() {
    super.onCreate() // Injection happens in super.onCreate()
    // Use bar
  }
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

For more details, see [Hilt Application](application.md).

## @AndroidEntryPoint

Once you have enabled members injection in your `Application`, you can start
enabling members injection in your other Android classes using the
[`@AndroidEntryPoint`](https://dagger.dev/api/latest/dagger/hilt/android/AndroidEntryPoint.html)
annotation. You can use `@AndroidEntryPoint` on the following types:

1.  Activity
1.  Fragment
1.  View
1.  Service
1.  BroadcastReceiver

Note that ViewModels are supported via a separate API
[`@HiltViewModel`](view-model.md). The following example shows how to add the
annotation to an activity, but the process is the same for other types.

To enable members injection in your activity, annotate your class with
[`@AndroidEntryPoint`](https://dagger.dev/api/latest/dagger/hilt/android/AndroidEntryPoint.html).

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@AndroidEntryPoint
public final class MyActivity extends MyBaseActivity {
  @Inject Bar bar; // Bindings in SingletonComponent or ActivityComponent

  @Override
  public void onCreate() {
    // Injection happens in super.onCreate().
    super.onCreate();

    // Do something with bar ...
  }
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@AndroidEntryPoint
class MyActivity : MyBaseActivity() {
  @Inject lateinit var bar: Bar // Bindings in SingletonComponent or ActivityComponent

  override fun onCreate() {
    // Injection happens in super.onCreate().
    super.onCreate()

    // Do something with bar ...
  }
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

**Note:** Hilt currently only supports activities that extend [`ComponentActivity`](https://developer.android.com/reference/androidx/activity/ComponentActivity) and
fragments that extend androidx library
[`Fragment`](https://developer.android.com/reference/androidx/fragment/app/Fragment),
not the (now deprecated)
[`Fragment`](https://developer.android.com/reference/android/app/Fragment) in
the Android platform.
{: .c-callouts__note }

For more details, see [@AndroidEntryPoint](android-entry-point.md).

## Hilt Modules

Hilt modules are standard Dagger modules that have an additional `@InstallIn`
annotation that determines which
[Hilt component(s)](components.md#hilt-components) to install the module into.

When the Hilt components are generated, the modules annotated with `@InstallIn`
will be installed into the corresponding component or subcomponent via
`@Component#modules` or `@Subcomponent#modules` respectively. Just
like in Dagger, installing a module into a component allows that binding to be
accessed as a dependency of other bindings in that component or any child
component(s) below it in the
[component hierarchy](components.md#component-hierarchy). They can also be
accessed from the corresponding `@AndroidEntryPoint` classes. Being installed in
a component also allows that binding to be scoped to that component.

### Using `@InstallIn`

A module is installed in a [Hilt Component](components.md) by annotating the
module with the
[`@InstallIn`](https://dagger.dev/api/latest/dagger/hilt/InstallIn.html)
annotation. These annotations are required on all Dagger modules when using
Hilt, but this check may be optionally
[disabled](flags.md#disable-install-in-check).

**Note:** If a module does not have an `@InstallIn` annotation, the module will
not be part of the component and may result in compilation errors.
{: .c-callouts__note }

Specify which Hilt Component to install the module in by passing in the
appropriate [Component](components.md) type(s) to the `@InstallIn` annotation.
For example, to install a module so that anything in the application can use it,
use `SingletonComponent`:

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@Module
@InstallIn(SingletonComponent.class) // Installs FooModule in the generate SingletonComponent.
final class FooModule {
  @Provides
  static Bar provideBar() {...}
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@Module
@InstallIn(SingletonComponent::class) // Installs FooModule in the generate SingletonComponent.
internal object FooModule {
  @Provides
  fun provideBar(): Bar {...}
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

For more details, see [Hilt Modules](modules.md).

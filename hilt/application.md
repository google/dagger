---
layout: default
title: Hilt Application
---

**Note:** Examples on this page assume usage of the Gradle plugin. If you are **not**
using the plugin, please read this [page](gradle-setup.md#hilt-gradle-plugin) for details.
{: .c-callouts__note }

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
if an object is only needed later or conditionally, remember that you can use
a `Provider` to defer injection. Especially in the `Application` class which is
on the critical startup path, avoiding unnecessary injections can be important
to performance.
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

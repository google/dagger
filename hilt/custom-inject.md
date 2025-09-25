---
layout: default
title: Custom inject
---

## @CustomInject

In special circumstances, you may find that Hilt's default behavior of injecting
the `Application` class in `super.onCreate` is not suitable for your
application. For example, there may be things that you want to do before
injecting fields. In this case, you can use
[`@CustomInject`](https://dagger.dev/api/latest/dagger/hilt/android/migration/CustomInject.html)
to control if/when the application is injected.

When you annotate your `@HiltAndroidApp` application class with `@CustomInject`,
the injection no longer happens in `onCreate`. You can then use
[`CustomInjection`](https://dagger.dev/api/latest/dagger/hilt/android/migration/CustomInjection.html)
to inject your application at a time of your choosing.

**Note:** If you are not using the Gradle plugin and extend
the generated Hilt base class directly, you can also just call the
`customInject()` method which is on the generated base class.
{: .c-callouts__note }

Be aware that injection only injects the fields of the application and so is not
required or necessary if there are no `@Inject` fields in your application or
its base classes. Also note that this does not prevent the `SingletonComponent`
from being instantiated. If other code requests the `SingletonComponent` like an
`@AndroidEntryPoint` class being created, the `SingletonComponent` will still be
created on demand.

## Example

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@CustomInject
@HiltAndroidApp
public final class MyApplication extends Application {
  @Inject Foo foo;

  @Override
  public void onCreate() {
    // Injection would normally happen in this super.onCreate() call, but won't
    // now because this is using CustomInject.
    super.onCreate();
    doSomethingBeforeInjection();
    // This call now injects the fields in the Application, like the foo field above.
    CustomInjection.inject(this);
  }
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@CustomInject
@HiltAndroidApp
class MyApplication : Application() {
  @Inject lateinit var foo: Foo

  override fun onCreate() {
    // Injection would normally happen in this super.onCreate() call, but won't
    // now because this is using CustomInject.
    super.onCreate()
    doSomethingBeforeInjection()
    // This call now injects the fields in the Application, like the foo field above.
    CustomInjection.inject(this)
  }
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

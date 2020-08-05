---
layout: default
title: Dagger & Android
redirect_from:
  - /android
---

One of the primary advantages of Dagger 2 over most other dependency injection
frameworks is that its strictly generated implementation (no reflection) means
that it can be used in Android applications. However, there _are_ still some
considerations to be made when using Dagger within Android applications.

## Philosophy

While code written for Android is Java source, it is often quite different in
terms of style.  Typically, such differences exist to accomodate the unique
[performance][android-performance] considerations of a mobile platform.

But many of the patterns commonly applied to code intended for Android are
contrary to those applied to other Java code.  Even much of the advice in
[Effective Java][effective-java] is considered inappropriate for Android.

In order to achieve the goals of both idiomatic and portable code, Dagger
relies on [ProGuard] to post-process the compiled bytecode.  This allows Dagger
to emit source that looks and feels natural on both the server and Android,
while using the different toolchains to produce bytecode that executes
efficiently in both environments.  Moreover, Dagger has an explicit goal to
ensure that the Java source that it generates is consistently compatible with
ProGuard optimizations.

Of course, not all issues can be addressed in that manner, but it is the primary
mechanism by which Android-specific compatibility will be provided.

### tl;dr

Dagger assumes that users on Android will use R8 or ProGuard.

## Why Dagger on Android is hard

One of the central difficulties of writing an Android application using Dagger
is that many Android framework classes are instantiated by the OS itself, like
`Activity` and `Fragment`, but Dagger works best if it can create all the
injected objects. Instead, you have to perform members injection in a lifecycle
method. This means many classes end up looking like:

```java
public class FrombulationActivity extends Activity {
  @Inject Frombulator frombulator;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // DO THIS FIRST. Otherwise frombulator might be null!
    ((SomeApplicationBaseType) getContext().getApplicationContext())
        .getApplicationComponent()
        .newActivityComponentBuilder()
        .activity(this)
        .build()
        .inject(this);
    // ... now you can write the exciting code
  }
}
```

This has a few problems:

1. Copy-pasting code makes it hard to refactor later on. As more and more
   developers copy-paste that block, fewer will know what it actually does.

2. More fundamentally, it requires the type requesting injection
   (`FrombulationActivity`) to know about its injector. Even if this is done
   through interfaces instead of concrete types, it breaks a core principle of
   dependency injection: a class shouldn't know anything about how it is
   injected.

## `dagger.android`

The classes in [`dagger.android`] offer one approach to simplify the above
problems. This requires learning some extra APIs and concepts but gives you
reduced boilerplate and injection in your Android classes at the right place in
the lifecycle.

Another approach is to just use the normal Dagger APIs and follow guides such as
the one
[here](https://developer.android.com/training/dependency-injection/dagger-android).
This may be simpler to understand but comes with the downside of having to write
extra boilerplate manually.


The Jetpack and Dagger teams are working together on a
[new initiative](https://medium.com/androiddevelopers/dependency-injection-guidance-on-android-ads-2019-b0b56d774bc2)
for Dagger on Android that hopes to be a large shift from the current status
quo. While it is unfortunately not ready yet, this may be something to consider
when choosing how to use Dagger in your Android projects today.

### Injecting `Activity` objects

1.  Install [`AndroidInjectionModule`] in your application component to ensure
    that all bindings necessary for these base types are available.

2.  Start off by writing a `@Subcomponent` that implements
    [`AndroidInjector<YourActivity>`][AndroidInjector], with a
    `@Subcomponent.Factory` that extends
    [`AndroidInjector.Factory<YourActivity>`][AndroidInjector.Factory]:

    ```java
    @Subcomponent(modules = ...)
    public interface YourActivitySubcomponent extends AndroidInjector<YourActivity> {
      @Subcomponent.Factory
      public interface Factory extends AndroidInjector.Factory<YourActivity> {}
    }
    ```

3.  After defining the subcomponent, add it to your component hierarchy by
    defining a module that binds the subcomponent factory and adding it to the
    component that injects your `Application`:

    ```java
    @Module(subcomponents = YourActivitySubcomponent.class)
    abstract class YourActivityModule {
      @Binds
      @IntoMap
      @ClassKey(YourActivity.class)
      abstract AndroidInjector.Factory<?>
          bindYourAndroidInjectorFactory(YourActivitySubcomponent.Factory factory);
    }

    @Component(modules = {..., YourActivityModule.class})
    interface YourApplicationComponent {
      void inject(YourApplication application);
    }
    ```

    Pro-tip: If your subcomponent and its factory have no other methods or
    supertypes other than the ones mentioned in step #2, you can use
    [`@ContributesAndroidInjector`] to generate them for you. Instead of steps 2
    and 3, add an `abstract` module method that returns your activity, annotate
    it with `@ContributesAndroidInjector`, and specify the modules you want to
    install into the subcomponent. If the subcomponent needs scopes, apply the
    scope annotations to the method as well.

    ```java
    @ActivityScope
    @ContributesAndroidInjector(modules = { /* modules to install into the subcomponent */ })
    abstract YourActivity contributeYourAndroidInjector();
    ```

4.  Next, make your `Application` implement [`HasAndroidInjector`]
    and `@Inject` a
    [`DispatchingAndroidInjector<Object>`][DispatchingAndroidInjector] to
    return from the `androidInjector()` method:

    ```java
    public class YourApplication extends Application implements HasAndroidInjector {
      @Inject DispatchingAndroidInjector<Object> dispatchingAndroidInjector;

      @Override
      public void onCreate() {
        super.onCreate();
        DaggerYourApplicationComponent.create()
            .inject(this);
      }

      @Override
      public AndroidInjector<Object> androidInjector() {
        return dispatchingAndroidInjector;
      }
    }
    ```

5.  Finally, in your `Activity.onCreate()` method, call
    [`AndroidInjection.inject(this)`][AndroidInjection.inject(Activity)]
    *before* calling `super.onCreate();`:

    ```java
    public class YourActivity extends Activity {
      public void onCreate(Bundle savedInstanceState) {
        AndroidInjection.inject(this);
        super.onCreate(savedInstanceState);
      }
    }
    ```

6.  Congratulations!

#### How did that work?

`AndroidInjection.inject()` gets a `DispatchingAndroidInjector<Object>` from
the `Application` and passes your activity to `inject(Activity)`. The
`DispatchingAndroidInjector` looks up the `AndroidInjector.Factory` for your
activity’s class (which is `YourActivitySubcomponent.Factory`), creates the
`AndroidInjector` (which is `YourActivitySubcomponent`), and passes your
activity to `inject(YourActivity)`.

### Injecting `Fragment` objects

Injecting a `Fragment` is just as simple as injecting an `Activity`. Define your
subcomponent in the same way.

Instead of injecting in `onCreate()` as is done for `Activity`
types, [inject `Fragment`s to in `onAttach()`](#when-to-inject).

Unlike the modules defined for `Activity`s, you have a choice of where to
install modules for `Fragment`s. You can make your `Fragment` component a
subcomponent of another `Fragment` component, an `Activity` component, or the
`Application` component — it all depends on which other bindings your `Fragment`
requires. After deciding on the component location, make the corresponding type
implement `HasAndroidInjector` (if it doesn't already). For example, if your `Fragment`
needs bindings from `YourActivitySubcomponent`, your code will look something
like this:

```java
public class YourActivity extends Activity
    implements HasAndroidInjector {
  @Inject DispatchingAndroidInjector<Object> androidInjector;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    AndroidInjection.inject(this);
    super.onCreate(savedInstanceState);
    // ...
  }

  @Override
  public AndroidInjector<Object> androidInjector() {
    return androidInjector;
  }
}

public class YourFragment extends Fragment {
  @Inject SomeDependency someDep;

  @Override
  public void onAttach(Activity activity) {
    AndroidInjection.inject(this);
    super.onAttach(activity);
    // ...
  }
}

@Subcomponent(modules = ...)
public interface YourFragmentSubcomponent extends AndroidInjector<YourFragment> {
  @Subcomponent.Factory
  public interface Factory extends AndroidInjector.Factory<YourFragment> {}
}

@Module(subcomponents = YourFragmentSubcomponent.class)
abstract class YourFragmentModule {
  @Binds
  @IntoMap
  @ClassKey(YourFragment.class)
  abstract AndroidInjector.Factory<?>
      bindYourFragmentInjectorFactory(YourFragmentSubcomponent.Factory factory);
}

@Subcomponent(modules = { YourFragmentModule.class, ... }
public interface YourActivityOrYourApplicationComponent { ... }
```

### Base Framework Types

Because `DispatchingAndroidInjector` looks up the appropriate
`AndroidInjector.Factory` by the class at runtime, a base class can implement
`HasAndroidInjector` as well as call `AndroidInjection.inject()`. All each
subclass needs to do is bind a corresponding `@Subcomponent`. Dagger provides a
few base types that do this, such as [`DaggerActivity`] and [`DaggerFragment`],
if you don't have a complicated class hierarchy. Dagger also provides a
[`DaggerApplication`] for the same purpose — all you need to do is to extend it
and override the `applicationInjector()` method to return the component that
should inject the `Application`.

The following types are also included:
  - [`DaggerService`] and [`DaggerIntentService`]
  - [`DaggerBroadcastReceiver`]
  - [`DaggerContentProvider`]

**Note:** [`DaggerBroadcastReceiver`] should only be used when the
`BroadcastReceiver` is registered in the `AndroidManifest.xml`. When the
`BroadcastReceiver` is created in your own code, prefer constructor injection
instead.
{: .c-callouts__note }

### Support libraries

For users of the Android support library, parallel types exist in the
`dagger.android.support` package.

> TODO(ronshapiro): we should begin to split this up by androidx packages

### How do I get it?

Add the following to your build.gradle:

```groovy
dependencies {
  compile 'com.google.dagger:dagger-android:2.x'
  compile 'com.google.dagger:dagger-android-support:2.x' // if you use the support libraries
  annotationProcessor 'com.google.dagger:dagger-android-processor:2.x'
  annotationProcessor 'com.google.dagger:dagger-compiler:2.x'
}
```


<a name="when-to-inject"></a>

## When to inject

Constructor injection is preferred whenever possible because `javac` will ensure
that no field is referenced before it has been set, which helps avoid
`NullPointerException`s. When members injection is required (as discussed
above), prefer to inject as early as possible. For this reason, `DaggerActivity`
calls `AndroidInjection.inject()` immediately in `onCreate()`, before calling
`super.onCreate()`, and `DaggerFragment` does the same in `onAttach()`, which
also prevents inconsistencies if the `Fragment` is reattached.

It is crucial to call `AndroidInjection.inject()` before `super.onCreate()` in
an `Activity`, since the call to `super` attaches `Fragment`s from the previous
activity instance during configuration change, which in turn injects the
`Fragment`s. In order for the `Fragment` injection to succeed, the `Activity`
must already be injected. For users of [ErrorProne], it is a
compiler error to call `AndroidInjection.inject()` after `super.onCreate()`.

## FAQ

### Scoping `AndroidInjector.Factory`

`AndroidInjector.Factory` is intended to be a stateless interface so that
implementors don't have to worry about managing state related to the object
which will be injected. When `DispatchingAndroidInjector` requests a
`AndroidInjector.Factory`, it does so through a `Provider` so that it doesn't
explicitly retain any instances of the factory. Because some implementations may
retain an instance of the `Activity`/`Fragment`/etc that is being injected, it
is a compile-time error to apply a scope to the methods which provide them. If
you are positive that your `AndroidInjector.Factory` does not retain an instance
to the injected object, you may suppress this error by applying
`@SuppressWarnings("dagger.android.ScopedInjectorFactory")` to your module
method.

<!-- References -->

[AndroidInjection.inject(Activity)]: https://dagger.dev/api/latest/dagger/android/AndroidInjection.html#inject-android.app.Activity-
[AndroidInjector]: https://dagger.dev/api/latest/dagger/android/AndroidInjector.html
[AndroidInjector.Factory]: https://dagger.dev/api/latest/dagger/android/AndroidInjector.Factory.html
[android-performance]: http://developer.android.com/training/best-performance.html
[`AndroidInjectionModule`]: https://dagger.dev/api/latest/dagger/android/AndroidInjectionModule.html
[`@ContributesAndroidInjector`]: https://dagger.dev/api/latest/dagger/android/ContributesAndroidInjector.html
[`dagger.android`]: https://dagger.dev/api/latest/dagger/android/package-summary.html
[`DaggerActivity`]: https://dagger.dev/api/latest/dagger/android/DaggerActivity.html
[`DaggerApplication`]: https://dagger.dev/api/latest/dagger/android/DaggerApplication.html
[`DaggerBroadcastReceiver`]: https://dagger.dev/api/latest/dagger/android/DaggerBroadcastReceiver.html
[`DaggerContentProvider`]: https://dagger.dev/api/latest/dagger/android/DaggerContentProvider.html
[`DaggerFragment`]: https://dagger.dev/api/latest/dagger/android/support/DaggerFragment.html
[`DaggerIntentService`]: https://dagger.dev/api/latest/dagger/android/DaggerIntentService.html
[`DaggerService`]: https://dagger.dev/api/latest/dagger/android/DaggerService.html
[DispatchingAndroidInjector]: https://dagger.dev/api/latest/dagger/android/DispatchingAndroidInjector.html
[effective-java]: https://books.google.com/books?id=ka2VUBqHiWkC
[ErrorProne]: https://github.com/google/error-prone
[`HasAndroidInjector`]: https://dagger.dev/api/latest/dagger/android/HasAndroidInjector.html
[ProGuard]: http://proguard.sourceforge.net/


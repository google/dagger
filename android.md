---
layout: default
title: Dagger & Android
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
efficiently in both environements.  Moreover, Dagger has an explicit goal to
ensure that the Java source that it generates is consistently compatible with
ProGuard optimizations.

Of course, not all issues can be addressed in that manner, but it is the primary
mechanism by which Android-specific compatbility will be provided.

### tl;dr

Dagger assumes that users on Android will use ProGuard.

## Recommended ProGuard Settings

Watch this space for ProGuard settings that are relevant to applications using
Dagger.

## `dagger.android`

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
   dependency injection: a class shouldn't know anything about how it isinjected.

The classes in [`dagger.android`] offer one approach to simplify this pattern.

### Injecting `Activity` objects

1. Install [`AndroidInjectionModule`] in your application component to ensure that
   all bindings necessary for these base types are available.

2. Start off by writing a `@Subcomponent` that implements
   [`AndroidInjector<YourActivity>`][AndroidInjector], with a
   `@Subcomponent.Builder` that extends
   [`AndroidInjector.Builder<YourActivity>`][AndroidInjector.Builder]:

    ```java
    @Subcomponent(modules = ...)
    public interface YourActivitySubcomponent extends AndroidInjector<YourActivity> {
      @Subcomponent.Builder
      public abstract class Builder extends AndroidInjector.Builder<YourActivity> {}
    }
    ```

3. After defining the subcomponent, add it to your component hierarchy by
   defining a module that binds the subcomponent builder and adding it to the
   component that injects your `Application`:

    ```java
    @Module(subcomponents = YourActivitySubcomponent.class)
    abstract class YourActivityModule {
      @Binds
      @IntoMap
      @ActivityKey(YourActivity.class)
      abstract AndroidInjector.Factory<? extends Activity>
          bindYourActivityInjectorFactory(YourActivitySubcomponent.Builder builder);
    }

    @Component(modules = {..., YourActivityModule.class})
    interface YourApplicationComponent {}
    ```

4. Next, make your `Application` implement [`HasDispatchingActivityInjector`]
   and `@Inject` a
   [`DispatchingAndroidInjector<Activity>`][DispatchingAndroidInjector] to
   return from the `activityInjector()` method:

    ```java
    public class YourApplication extends Application implements HasDispatchingActivityInjector {
      @Inject DispatchingAndroidInjector<Activity> dispatchingActivityInjector;

      @Override
      public void onCreate() {
        super.onCreate();
        DaggerYourApplicationComponent.create()
            .inject(this);
      }

      @Override
      public DispatchingAndroidInjector<Activity> activityInjector() {
        return dispatchingActivityInjector;
      }
    }
    ```

5. Finally, in your `Activity.onCreate()` method, call
   [`AndroidInjection.inject(this)`][AndroidInjection.inject(Activity)]:

    ```java
    public class YourActivity extends Activity {
      public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidInjection.inject(this);
      }
    }
    ```

6. Congratulations!

#### How did that work?

`AndroidInjection.inject()` gets a `DispatchingAndroidInjector<Activity>` from
the `Application` and passes your activity to `inject(Activity)`. The
`DispatchingAndroidInjector` looks up the `AndroidInjector.Factory` for your
activity’s class (which is `YourActivitySubcomponent.Builder`), creates the
`AndroidInjector` (which is `YourActivitySubcomponent`), and passes your
activity to `inject(YourActivity)`.

### Injecting `Fragment` objects

Injecting a `Fragment` is just as simple as injecting an `Activity`. Define your
subcomponent in the same way, replacing `Activity` type parameters with
`Fragment`, `@ActivityKey` with `@FragmentKey`, and
`HasDispatchingActivityInjector` with [`HasDispatchingFragmentInjector`].

Instead of injecting in `onCreate()` as is done for `Activity`
types, [inject `Fragment`s to in `onAttach()`](#when-to-inject).

Unlike the modules defined for `Activity`s, you have a choice of where to
install modules for `Fragment`s. You can make your `Fragment` component a
subcomponent of another `Fragment` component, an `Activity` component, or the
`Application` component — it all depends on which other bindings your `Fragment`
requires. After deciding on the component location, make the corresponding type
implement `HasDispatchingFragmentInjector`. For example, if your `Fragment`
needs bindings from `YourActivitySubcomponent`, your code will look something
like this:

```java
public class YourActivity extends Activity
    implements HasDispatchingFragmentInjector {
  @Inject DispatchingFragmentInjector<Fragment> fragmentInjector;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    AndroidInjection.inject(this);
    // ...
  }

  @Override
  public DispatchingAndroidInjector<Activity> activityInjector() {
    return fragmentInjector;
  }
}

public class YourFragment extends Fragment {
  @Inject SomeDependency someDep;

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    AndroidInjection.inject(this);
    // ...
  }
}

@Subcomponent(modules = ...)
public interface YourFragmentSubcomponent extends AndroidInjector<YourFragment> {
  @Subcomponent.Builder
  public abstract class Builder extends AndroidInjector.Builder<YourFragment> {}
}

@Module(subcomponents = YourFragmentSubcomponent.class)
abstract class YourFragmentModule {
  @Binds
  @IntoMap
  @FragmentKey(YourFragment.class)
  abstract AndroidInjector.Factory<? extends Fragment>
      bindYourFragmentInjectorFactory(YourFragmentSubcomponent.Builder builder);
}

@Subcomponent(modules = { YourFragmentModule.class, ... }
public interface YourActivityOrYourApplicationComponent { ... }
```

### Base Framework Types

Because `DispatchingAndroidInjector` looks up the appropriate
`AndroidInjector.Factory` by the class at runtime, a base class can implement
`HasDispatchingActivityInjector` and `HasDispatchingFragmentInjector` as well as
call `AndroidInjection.inject()`. All each subclass needs to do is bind a
corresponding `@Subcomponent`. Dagger provides a few base types that do this,
[`DaggerActivity`] and [`DaggerFragment`], if you don't have a complicated
activity/fragment class hierarchy. Dagger also provides a [`DaggerApplication`]
for the same purpose — all you need to do is to extend it and override the
`applicationInjector()` method to return the component that should inject the
`Application`.

### Support libraries

For users of the Android support library, parallel types exist in the
`dagger.android.support` package. Note that while support `Fragment` users have
to bind `AndroidInjector.Factory<? extends android.support.v4.app.Fragment>`,
AppCompat users should continue to implement `AndroidInjector.Factory<? extends
Activity>` and not `<? extends AppCompatActivity>` (or `FragmentActivity`).

<a name="when-to-inject"></a>

## When to inject

Constructor injection is preferred whenever possible because `javac` will ensure
that no field is referenced before it has been set, which helps avoid
`NullPointerException`s. When members injection is required (as discussed
above), prefer to inject as early as possible. For this reason, `DaggerActivity`
calls `AndroidInjection.inject()` directly after `super.onCreate()`, and
`DaggerFragment` does the same in `super.onAttach()`, which also prevents
inconsistencies if the `Fragment` is reattached.

<!-- References -->

[AndroidInjection.inject(Activity)]: https://google.github.io/dagger/api/latest/dagger/android/AndroidInjection.html#inject-Activity-
[android-performance]: http://developer.android.com/training/best-performance.html
[`AndroidInjectionModule`]: https://google.github.io/dagger/api/latest/dagger/android/AndroidInjectionModule.html
[`dagger.android`]: https://google.github.io/dagger/api/latest/dagger/android/package-summary.html
[DaggerActivity]: https://google.github.io/dagger/api/latest/dagger/android/DaggerActivity.html
[DaggerApplication]: https://google.github.io/dagger/api/latest/dagger/android/DaggerApplication.html
[DaggerFragment]: https://google.github.io/dagger/api/latest/dagger/android/DaggerFragment.html
[DispatchingAndroidInjector]: https://google.github.io/dagger/api/latest/dagger/android/DispatchingAndroidInjector.html
[effective-java]: https://books.google.com/books?id=ka2VUBqHiWkC
[`HasDispatchingActivityInjector`]: https://google.github.io/dagger/api/latest/dagger/android/HasDispatchingActivityInjector.html
[`HasDispatchingFragmentInjector`]: https://google.github.io/dagger/api/latest/dagger/android/HasDispatchingFragmentInjector.html
[ProGuard]: http://proguard.sourceforge.net/


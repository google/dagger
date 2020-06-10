---
layout: default
title: Migrating to Hilt
---

Migrating to Hilt can vary widely in difficulty depending on the state of your
codebase and which practices or patterns your codebase follows. This page offers
advice on some common issues migrating apps may encounter. This page assumes
that you already generally understand the basic Hilt APIs. If that is not the
case, take a look at our [Quick Start](quick-start.md) guide for Hilt first. This
page also assumes a general understanding of Dagger, which should be the case
since this page is only useful for those migrating a codebase that already uses
Dagger. If your codebase does not use Dagger, add Hilt to your app by going
through the [Quick Start](quick-start.md) guide as this guide only deals with
migrations from non-Hilt Dagger setups.

_Refactoring tip_: Whenever you modify the code of a class, check that the
unused or no longer existing imports are removed from the file.

# Table of Contents
{:.no_toc}

* This is a placeholder list that will be replaced with the table of contents
{:toc}


## 0. Plan your migration

When migrating to Hilt, you'll want to organize your work into steps. This guide
should lay out the general approach that should work for most cases, but every
migration will be different. The recommended approach is to start at the
`Application` or `@Singleton` component and incrementally grow from there. After
`Application` and `@Singleton`, migrate activities and then fragments after
that. This should generally be doable as an incremental migration. Even if you
have a relatively small codebase, doing the migration incrementally will give
you a chance to build in between steps to sanity check your progress.

### Compare component hierarchies

The first thing to do is to compare your current component hierarchy to the one
in [Hilt](components.md). You’ll want to decide which components map to which
Hilt component. Hopefully these should be relatively straightforward, but if
there is not a clear mapping, you can keep custom components as manual Dagger
components. These components can be children of the Hilt components. However,
Hilt does not allow inserting components into the hierarchy (e.g. changing the
parent of a Hilt component). See the custom components
[section](#custom-components) of the guide below. **The rest of this guide
assumes a migration where the components all map directly to Hilt components.**

Also, if your code uses component dependencies, you should read the component
dependencies
[section](#component-dependencies-for-components-that-map-to-hilt-components)
below first as well. **The rest of this guide assumes usage of subcomponents.**

If you are using the dagger.android `@ContributesAndroidInjector` and are unsure
about your component hierarchy, then your hierarchy should roughly match the
Hilt components.

### Be aware of when Hilt injects classes

You can find out when Hilt injects classes for each Android class
[here](components.md#component-lifetimes). These hopefully should be similar to
where your code currently injects, but if not, be aware in case it causes any
differences in your code.

### Migration Overview

At the end of the migration, the code should be changed as follows:

*   All `@Component`/`@Subcomponent` (or if using dagger.android
    `@ContributesAndroidInjector`) usages should be removed.
*   All `@Module` classes should be annotated with `@InstallIn`.
*   All `Application`/`Activity`/`Fragment`/`View`/`Service`/`BroadcastReceiver`
    classes should be annotated with `@AndroidEntryPoint`.
*   Any code instantiating or propagating components (like interfaces on your
    Activity to expose the component) should be removed.
*   All `dagger.android` references should be removed.

## 1. Migrate the `Application`

The first thing to change will be to migrate your `Application` and `@Singleton`
component to the generated Hilt `ApplicationComponent`. To do this, we’ll first
want to make sure that everything that is installed in your current component is
installed in the Hilt `ApplicationComponent`.

### Migrating a `Component`

To migrate the Application, we need to migrate everything in the pre-existing
`@Singleton` component to the `ApplicationComponent`.

#### a. Handle the modules

First, we should install all of the modules into the `ApplicationComponent`.
This can be done by annotating each module currently installed in your component
with `@InstallIn(ApplicationComponent.class)`. If there are a lot of modules,
instead of changing all of those now, you can create and install a single
aggregator `@Module` class that includes all of the current modules.

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
// Starting with this component
@Component(modules = {
    FooModule.class,
    BarModule.class,
    ...
})
interface MySingletonComponent {
}

// Becomes the following classes
@InstallIn(ApplicationComponent.class)
@Module(includes = {
    FooModule.class,
    BarModule.class,
    ...
})
interface AggregatorModule {}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
// Starting with this component
@Component(modules = [
    FooModule::class,
    BarModule::class,
    ...
])
interface MySingletonComponent {
}

// Becomes the following classes
@InstallIn(ApplicationComponent::class)
@Module(includes = [
    FooModule::class,
    BarModule::class,
    ...
])
interface AggregatorModule {}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

**Warning:** Modules that are not annotated with `@InstallIn` are not used by Hilt.
Hilt by default raises an error when unannotated modules are found, but this
error can be [disabled](compiler-options.md#disable-install-in-check).
{: .c-callouts__warning }

#### b. Handle any extended interfaces or methods

A similar process can be used for any interfaces your current component extends
using [`@EntryPoint`].

Interfaces on components are generally used to either add inject methods or get
access to types like bindings or subcomponents. In Hilt many of these won't be
needed once the migration is complete because Hilt will generate them for you or
they will be replaced by Hilt tools. For the migration though, this section will
describe how to preserve current behavior so that code continues to work. You
should be looking at all of these methods though and evaluating if they are
still needed as the migration continues.

##### Moving everything with [`@EntryPoint`]

Annotate any interface your component extends with [`@EntryPoint`] and
`@InstallIn(ApplicationComponent.class)`. If there are many interfaces, create a
single aggregator interface to collect them all just like the modules. Any
method defined directly on the component interface can be moved to either the
aggregator interface or one the aggregator extends.

Example:

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
// Starting with this component
@Component
@Singleton
interface MySingletonComponent extends FooInjector, BarInjector {
    void inject(MyApplication myApplication);

    Foo getFoo();
}

// Becomes the following class
@InstallIn(ApplicationComponent.class)
@EntryPoint
interface AggregatorEntryPoint extends FooInjector, BarInjector {
  // This is moved as an example, but further below we will see that inject
  // methods for the Application can just be removed.
  void inject(MyApplication myApplication);

  Foo getFoo();
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
// Starting with this component
@Component
@Singleton
interface MySingletonComponent : FooInjector, BarInjector {
    fun inject(myApplication: MyApplication)

    fun getFoo() : Foo
}

// Becomes the following class
@InstallIn(ApplicationComponent::class)
@EntryPoint
interface AggregatorEntryPoint : FooInjector, BarInjector {
  // This is moved as an example, but further below we will see that inject
  // methods for the Application can just be removed.
  fun inject(myApplication: MyApplication)

  fun getFoo() : Foo
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

##### Inject methods

Hilt handles injecting your `Application` class under the hood, so if you had
any inject methods for the `Application`, those can be removed. Inject methods
for other Android types should also eventually be removed as those are later
migrated to use `@AndroidEntryPoint`.

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@Component
@Singleton
interface MySingletonComponent {
  // Hilt takes care of Application injection for you, so this can be deleted.
  void inject(MyApplication myApplication);

  // This can be deleted once FooActivity is migrated to use @AndroidEntryPoint
  void inject(FooActivity fooActivity);
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@Component
@Singleton
interface MySingletonComponent {
  // Hilt takes care of Application injection for you, so this can be deleted.
  fun inject(myApplication: MyApplication)

  // This can be deleted once FooActivity is migrated to use @AndroidEntryPoint
  fun inject(fooActivity: FooActivity)
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

##### Accessing the interfaces

Your code likely has a method where you returned the component either directly
or as one of the interface types so that other code could get access to inject
methods or accessor methods. To keep this code working as you migrate, you can
get a reference by using the
[`EntryPoints`](entry-points.md#access-an-entrypoint) class. As your migration
continues, you should be able to remove these methods and have calling code use
the Hilt [`EntryPoints`](entry-points.md) API directly.

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
// If you started with code like this:
public final class MyApplication extends Application {
  MySingletonComponent component() {
    return component;
  }
}

// After adding the aggregator entry point, it will look like the following:

@InstallIn(ApplicationComponent.class)
@EntryPoint
interface AggregatorEntryPoint extends LegacyInterface, ... {
}

@HiltAndroidApp
public final class MyApplication extends Application {
  // The return type changed the the AggregatorEntryPoint, but that should be
  // okay as this implements all the interfaces the old component used to.
  AggregatorEntryPoint component() {
    // Use EntryPoints to get an instance of the AggregatorEntryPoint.
    return EntryPoints.get(this, AggregatorEntryPoint.class);
  }
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
// If you started with code like this:
class MyApplication : Application() {
  fun component(): MySingletonComponent {
    return component
  }
}

// After adding the aggregator entry point, it will look like the following:

@InstallIn(ApplicationComponent::class)
@EntryPoint
interface AggregatorEntryPoint : LegacyInterface, ... {
}

@HiltAndroidApp
class MyApplication : Application() {
  // The return type changed the the AggregatorEntryPoint, but that should be
  // okay as this implements all the interfaces the old component used to.
  fun component(): AggregatorEntryPoint {
    // Use EntryPoints to get an instance of the AggregatorEntryPoint.
    return EntryPoints.get(this, AggregatorEntryPoint::class.java)
  }
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

#### c. Scopes

When migrating a component to Hilt, you’ll also need to migrate your bindings to
use the Hilt scope annotations. In the case of the `ApplicationComponent`, this
is `@Singleton`. You can find which annotations correspond to which component
[here](components.md#component-lifetimes). If you aren’t using `@Singleton` and
have your own scoping annotation, you can tell Hilt that your annotation is
equivalent to a Hilt scoping annotation using [scope aliases](scope-aliases.md).
This will allow you to migrate and remove your scoping annotation at your
leisure later in the process.

#### d. Handling component arguments

Hilt components cannot take component arguments because the initialization of
the component is hidden from users. Usually, this is used to get an application
instance (or for other components an activity/fragment instance) into the Dagger
graph. For these cases, you should switch to using the predefined bindings in
Hilt that are listed [here](components.md#component-bindings).

If your component has any other arguments either through module instances passed
to the builder or `@BindsInstance`, read this [section](#component-arguments) on
handling those. Once you handle those, you can just remove your
`@Component.Builder` interface as will be unused.

#### e. Cleaning up aggregators

If you used an aggregator module or entry point, you will eventually need to go
back and remove the aggregator module and entry point class. You can do this by
individually annotating all of the included modules and implemented interfaces
with the same `@InstallIn` annotation used on the aggregator.

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@InstallIn(ApplicationComponent.class)
@Module(includes = {FooModule.class, ...})
interface AggregatorModule {
}

// Remove FooModule from the list above and annotate it directly

@InstallIn(ApplicationComponent.class)
@Module
interface FooModule {
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@InstallIn(ApplicationComponent::class)
@Module(includes = [FooModule::class, ...])
interface AggregatorModule {
}

// Remove FooModule from the list above and annotate it directly

@InstallIn(ApplicationComponent::class)
@Module
interface FooModule {
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

### Adding Hilt to the `Application`

Now you can just annotate your `Application` with `@HiltAndroidApp` as described
in our [Quick Start](quick-start.md) guide. Apart from that, it should be empty of
any code related to building or storing an instance of your component. You can
delete your `@Component` class and `@Component.Builder` class if you haven’t
already.

### dagger.android Application

If your `Application` either extends from `DaggerApplication` or implements
`HasAndroidInjector`, keep this code until all your dagger.android
activities/fragments have been also migrated. This will likely be one of the
final steps of your migration. These parts of dagger.android are there for
making sure getting dependencies works (e.g. when an `Activity` tries to inject
itself). The difference is now they are being satisfied by the Hilt
`ApplicationComponent` instead of the component removed in the above steps.

For example, a migrated dagger.android `Application` that supports both Hilt
activities and dagger.android activities may look like this:

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@HiltAndroidApp
public final class MyApplication implements HasAndroidInjector {
  @Inject DispatchingAndroidInjector<Object> dispatchingAndroidInjector;

  @Override
  public AndroidInjector<Object> androidInjector() {
    return dispatchingAndroidInjector;
  }
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@HiltAndroidApp
class MyApplication : HasAndroidInjector {
  @Inject lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<Object>

  override fun androidInjector() = dispatchingAndroidInjector
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

Or if you were using `DaggerApplication` before you can do the following. The
[`@EntryPoint`] class is to make the Dagger component implement
`AndroidInjector<MyApplication>`. This is likely what your previous Dagger
component was doing before.

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@HiltAndroidApp
public final class MyApplication extends DaggerApplication {
  @EntryPoint
  @InstallIn(ApplicationComponent.class)
  interface ApplicationInjector extends AndroidInjector<MyApplication> {
  }

  @Override
  public AndroidInjector<MyApplication> applicationInjector() {
    return EntryPoints.get(this, ApplicationInjector.class);
  }
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@HiltAndroidApp
class MyApplication : DaggerApplication() {
  @EntryPoint
  @InstallIn(ApplicationComponent::class)
  interface ApplicationInjector : AndroidInjector<MyApplication>

  override fun applicationInjector(): AndroidInjector<MyApplication> {
    return EntryPoints.get(this, ApplicationInjector::class.java)
  }
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

When you have migrated all of the other dagger.android usages and are ready to
remove this code, simply extend from `Application` and remove the overridden
methods and the `DispatchingAndroidInjector` classes.

### Check your build

You should be able to stop and build/run your app successfully at this point.
Your app is successfully using Hilt for the `ApplicationComponent`.

## 2. Migrate Activities and Fragments (and other classes)

Now that the application supports Hilt, you should be able to start migrating
your activities and then fragments to Hilt. While migrating your app, it is okay
to have `@AndroidEntryPoint` activities and non-`@AndroidEntryPoint` activities
together. The same is true for fragments within an activity. The only
restriction with mixing Hilt with non-Hilt code is on the parent. Hilt
activities need to be attached to Hilt applications. Hilt fragments must be
attached to Hilt activities. We recommend doing all the activities before doing
any of the fragments, but if that is problematic there is a tool to help relax
that constraint with [optional injection](optional-inject.md).

Migrating activities and fragments are going to be pretty similar to the
application component in terms of mechanics. You should take all the modules
from your current component and install them in the proper component with an
`@InstallIn` module. Similarly, take all of the current component's extended
interfaces and install them in the proper component with an `@InstallIn`
[entry point](entry-points.md). Go back to [this section](#migrating-a-component)
above for details, but also read below on some of the extra consideration that
must be taken for activities and fragments.

**Note:** If you are using dagger.android's `@ContributesAndroidInjector`, then when
following [this section](#migrating-a-component) on migrating a component the
modules in `@ContributesAndroidInjector` are the modules you need to migrate.
You do not have any interfaces to migrate with [`@EntryPoint`].
{: .c-callouts__note }

### Be aware of differences with monolithic components

One of the design decisions of Hilt is to use a single component for all of the
activities and a single component for all of the fragments. If you’re
interested, you can read about the reasons [here](monolithic.md). The reason
this is important is that if you had a separate component for each activity (as
is the default in dagger.android), you will be merging the components into a
single component when migrating to Hilt. Depending on your code base, you could
run into problems.

The two most frequent issues are:

#### Conflicting bindings

This occurs if you defined the same binding key differently in two activities.
When they are merged, you get a duplicate binding. This is a limitation of the
global binding key space of Hilt and you’ll need to redefine that binding to
have a single definition. Usually this isn’t too bad and is done by basing logic
off of the injected activity. See the section on
[component arguments](#component-arguments) for examples.

#### Depending on the specific activity type

Because of the merged component, bindings for a `FooActivity` or `BarActivity`
often won’t make sense anymore since when the component is used for a
`BarActivity` (or any other activity), a `FooActivity` binding won’t be able to
be satisfied. Usually code doesn’t really rely on the actual child type of the
activity and just needs an Activity or common subtype like `FragmentActivity`.
Code using the child type needs to be refactored to use a more generic type. If
you need a common subtype that isn’t automatically provided by Hilt, you can
provide a binding with a cast (example [here](#component-arguments)), but be
careful!

Example of replacing a usage with a common subtype:

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
// This class only uses the activity to get the FragmentManager. It can instead
// use the non-specific FragmentActivity class.
final class Foo {
  private final FooActivity activity;

  @Inject Foo(FooActivity activity) {
    this.activity = activity;
  }

  void doSomething() {
    activity.getSupportFragmentManager()...
  }
}

// Changed to FragmentActivity when migrating to Hilt class Foo @Inject
final class Foo {
  private final FragmentActivity activity;

  @Inject Foo(FragmentActivity activity) {
    this.activity = activity;
  }

  void doSomething() {
    activity.getSupportFragmentManager()...
  }
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
// This class only uses the activity to get the FragmentManager. It can instead
// use the non-specific FragmentActivity class.
class Foo @Inject constructor(private val activity: FooActivity) {
  fun doSomething() {
    activity.getSupportFragmentManager()...
  }
}

// Changed to FragmentActivity when migrating to Hilt class Foo @Inject
class Foo @Inject constructor(private val activity: FragmentActivity) {
  fun doSomething() {
    activity.getSupportFragmentManager()...
  }
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

### Adding Hilt to the Activity/Fragment

Now you can just annotate your `Activity` or `Fragment` with
`@AndroidEntryPoint` as described in our [Quick Start](quick-start.md) guide. Base
classes, even if they perform field injection, don't need to be annotated
(unless there is a situation where they are instantiated directly as the
childmost class).

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@AndroidEntryPoint
public final class FooActivity extends AppCompatActivity {
  @Inject Foo foo;
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@AndroidEntryPoint
class FooActivity : AppCompatActivity() {
  @Inject lateinit var foo: Foo
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

**Note:** Even if your activity doesn't need field injection, if there are fragments
attached to it that use `@AndroidEntryPoint`, you must migrate the activity to
use `@AndroidEntryPoint` as well.
{: .c-callouts__note }

### Dagger

Now you can remove any component initialization code or injection interfaces if
you have them.

### dagger.android

If you are using `@ContributesAndroidInjector` for this class, you can remove
that now. You can also remove any calls to
`AndroidInjection`/`AndroidSupportInjection` if you have them. If your class
implements `HasAndroidInjector`, and it is not the parent of any non-Hilt
fragments or views, you can remove that code now.

If your Activity or Fragment either extends from `DaggerAppCompatActivity`,
`DaggerFragment`, or similar classes, these need to be removed and replaced with
non-Dagger equivalents (like `AppCompatActivity` or a regular `Fragment`). If
you have any child fragments or views that are still using dagger.android,
you’ll need to implement `HasAndroidInjector` by injecting a
`DispatchingAndroidInjector` (see example below).

When you have migrated all of the children off of dagger.android, come back
later to remove the `HasAndroidInjector` code.

#### A simple dagger.android example

The following example shows migrating an activity while still allowing it to
support both Hilt and dagger.android fragments.

Initial state:

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
public final class MyActivity extends DaggerAppCompatActivity {
  @Inject Foo foo;
}

@Module
interface MyActivityModule {
    // If you have a scope annotation, see the section on scope aliases
    @ContributesAndroidInjector(modules = { FooModule.class, ... })
    MyActivity bindMyActivity()
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
class MyActivity : DaggerAppCompatActivity() {
  @Inject lateinit var foo: Foo
}

@Module
interface MyActivityModule {
    // If you have a scope annotation, see the section on scope aliases
    @ContributesAndroidInjector(modules = [ FooModule::class, ... ])
    fun bindMyActivity(): MyActivity
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

Intermediate state that allows both Hilt and dagger.android fragments:

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@AndroidEntryPoint
public final class MyActivity extends AppCompatActivity
    implements HasAndroidInjector {
  @Inject Foo foo;

  // Remove the code below later when all the children have been migrated
  @Inject DispatchAndroidInjector<Object> androidInjector;

  @Override
  public AndroidInjector<Object> androidInjector() {
    return androidInjector;
  }
}

// If the list of modules is very short, you don’t need this aggregator
// module, just put the @InstallIn(ActivityComponent.class) annotation on
// all the modules in includes list like FooModule
@Module(includes = { FooModule.class, ...})
@InstallIn(ActivityComponent.class)
interface MyActivityAggregatorModule {}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@AndroidEntryPoint
class MyActivity : AppCompatActivity(), HasAndroidInjector {
  @Inject lateinit var foo: Foo

  // Remove the code below later when all the children have been migrated
  @Inject lateinit var androidInjector: DispatchAndroidInjector<Object>

  override fun androidInjector() = androidInjector
}

// If the list of modules is very short, you don’t need this aggregator
// module, just put the @InstallIn(ActivityComponent.class) annotation on
// all the modules in includes list like FooModule
@Module(includes = [ FooModule::class, ...])
@InstallIn(ActivityComponent::class)
interface MyActivityAggregatorModule
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

Final state:

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@AndroidEntryPoint
public final class MyActivity extends AppCompatActivity {
  @Inject Foo foo;
}

// Each activity module is annotated with @InstallIn(ActivityComponent.class)
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@AndroidEntryPoint
class MyActivity : AppCompatActivity() {
  @Inject lateinit var foo: Foo
}

// Each activity module is annotated with @InstallIn(ActivityComponent::class)
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

### Check your build

You should be able to stop and build/run your app successfully after migrating
an activity or fragment. It is a good idea to check after migrating each class
to make sure you’re on the right track.

## 3. Other Android components

`View`, `Service`, and `BroadcastReceiver` types should follow the same formula
as above and be ready to migrate now. Once you have moved everything, you are
done!

Remember to:

*   Go back and clean up any leftover `HasAndroidInjector` usages.
*   Clean up any leftover aggregator modules or entry point interfaces. In
    general, you shouldn’t need to use `@Module(includes=)` with Hilt, so if you
    see that, you’ll want to remove it and just put an `@InstallIn` annotation
    on the included module.
*   Migrate any old scope annotation and the scope alias if you used that
    feature
*   Migrate any `@Binds` you had to put in place to make component argument
    bindings match

## What to do with ... ?

### Qualifiers

The qualifiers you have in your project are still valid, they'll be used by Hilt
in the same way they were used by Dagger.

If you have your own `@ApplicationContext` and `@ActivityContext` qualifiers to
differentiate between different `Contexts` in your app, you can add an `@Binds`
to map them together and then choose to replace your usage with the Hilt
qualifiers at your leisure.

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@InstallIn(ApplicationComponent.class)
@Module
interface ApplicationContextModule {
  @Binds
  @my.app.ApplicationContext
  Context bindAppContext(
      @dagger.hilt.android.qualifiers.ApplicationContext Context context);
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@InstallIn(ApplicationComponent::class)
@Module
interface ApplicationContextModule {
  @Binds
  @my.app.ApplicationContext
  fun bindAppContext(
      @dagger.hilt.android.qualifiers.ApplicationContext context: Context) :
      Context
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

### Component arguments

Because component instantiation is hidden when using Hilt, it is not possible to
add in your own component arguments with either module instances or
`@BindsInstance` calls. If you have these in your component, you’ll need to
refactor your code away from using these. Hilt comes with a set of default
bindings in each component which can be seen
[here](components.md#component-bindings). Depending on what your component
arguments are, you may want to have some of them depend on those default
bindings. This sometimes requires a slight redesign, but most cases can be
solved this way using the following strategies. If that is not the case though,
you may need to consider using a [custom component](#custom-components).

For example, in the simplest case, sometimes the binding didn’t need to be
passed in at all and it could be just a regular static `@Provides` method. In
another simple case, your argument may just be a variation of the default
binding like a custom BaseFragment type. Hilt can’t know that all Fragments are
going to be an instance of your `BaseFragment`, so if you need the actual type
bound to be your `BaseFragment`, you’ll need to do that with a cast.

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@Component.Builder
interface Builder {
  @BindsInstance
  Builder fragment(BaseFragment fragment);
}

@InstallIn(FragmentComponent.class)
@Module
final class BaseFragmentModule {
  @Provides
  static BaseFragment provideBaseFragment(Fragment fragment) {
    return (BaseFragment) fragment;
  }
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@Component.Builder
interface Builder {
  @BindsInstance
  fun fragment(fragment: BaseFragment): Builder
}

@InstallIn(FragmentComponent::class)
@Module
object BaseFragmentModule {
  @Provides
  fun provideBaseFragment(fragment: Fragment) : BaseFragment {
    return fragment as BaseFragment
  }
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

In other cases, your argument may be something on one of the default bindings,
like the activity `Intent`.

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@Component.Builder
interface Builder {
  @BindsInstance
  Builder intent(Intent intent);
}

@InstallIn(ActivityComponent.class)
@Module
final class IntentModule {
  @Provides
  static Intent provideIntent(Activity activity) {
    return activity.getIntent();
  }
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@Component.Builder
interface Builder {
  @BindsInstance
  fun intent(intent: Intent): Builder
}

@InstallIn(ActivityComponent::class)
@Module
object IntentModule {
  @Provides
  fun provideIntent(activity: Activity) : Intent {
    return activity.getIntent()
  }
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

Finally, you may have to redesign some things if they were configured
differently for different activity or fragment components. For example, you
could use a new interface on the activity to provide the object.

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@Component.Builder
interface Builder {
  @BindsInstance
  Builder foo(Foo foo);  // Foo is different per Activity
}

// Define an interface the activity can implement to provide a custom Foo
interface HasFoo {
  Foo getFoo();
}

@InstallIn(ActivityComponent::class)
@Module
final class FooModule {
  @Provides
  @Nullable
  static Foo provideFoo(Activity activity) {
    if (activity instanceof HasFoo) {
      return ((HasFoo) activity).getFoo();
    }
    return null;
  }
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@Component.Builder
interface Builder {
  @BindsInstance
  fun foo(foo: Foo): Builder  // Foo is different per Activity
}

// Define an interface the activity can implement to provide a custom Foo
interface HasFoo {
  fun getFoo() : Foo
}

@InstallIn(ActivityComponent::class)
@Module
object FooModule {
  @Provides
  fun provideFoo(activity: Activity) : Foo? {
    if (activity is HasFoo) {
      return activity.getFoo()
    }
    return null
  }
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

### Custom components

If you have other components that do not map to the Hilt components, you should
first consider if they can be simplified into the Hilt components. If not
though, you can keep your components as manual Dagger components. Choose the
section below based on if you want to use component dependencies or
subcomponents.

#### Component dependencies

Component dependencies can be hooked up with an [`@EntryPoint`].

For example, if you had a component dependency off of the
`ApplicationComponent`, you can keep it working by factoring out the needed
methods into an interface that is annotated with [`@EntryPoint`].

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
// Starting with this component dependency
@Component
interface MyApplicationComponent {
  // These bindings are exposed for MyCustomComponent
  Foo getFoo();
  Bar getBar();
  Baz getBaz();
  ...
}

@Component(dependencies = {MyApplicationComponent.class})
interface MyCustomComponent {
  @Component.Builder
  interface Builder {
    Builder appComponent(MyApplicationComponent appComponent);
    MyCustomComponent build();
  }
}

// It can be migrated to Hilt with the following classes

@InstallIn(ApplicationComponent.class)
@EntryPoint
interface CustomComponentDependencies {
  Foo getFoo();
  Bar getBar();
  Baz getBaz();
  ...
}

@Component(dependencies = {CustomComponentDependencies.class})
interface MyCustomComponent {
  @Component.Builder
  interface Builder {
    Builder appComponentDeps(CustomComponentDependencies deps);
    MyCustomComponent build();
  }
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
// Starting with this component dependency
@Component
interface MyApplicationComponent {
  // These bindings are exposed for MyCustomComponent
  fun getFoo(): Foo
  fun getBar(): Bar
  fun getBaz(): Baz
  ...
}

@Component(dependencies = [MyApplicationComponent::class])
interface MyCustomComponent {
  @Component.Builder
  interface Builder {
    fun appComponent(appComponent: MyApplicationComponent): Builder
    fun build(): MyCustomComponent
  }
}

// It can be migrated to Hilt with the following classes

@InstallIn(ApplicationComponent::class)
@EntryPoint
interface CustomComponentDependencies {
  fun getFoo(): Foo
  fun getBar(): Bar
  fun getBaz(): Baz
  ...
}

@Component(dependencies = [CustomComponentDependencies::class])
interface MyCustomComponent {
  @Component.Builder
  interface Builder {
    fun appComponentDeps(deps: CustomComponentDependencies): Builder
    fun build(): MyCustomComponent
  }
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

When building the custom component, you can get an instance of the
`CustomComponentDependencies` by using `EntryPoints`.

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
DaggerMyCustomComponent.builder()
    .appComponentDeps(
        EntryPoints.get(
            applicationContext,
            CustomComponentDependencies.class))
    .build();
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
DaggerMyCustomComponent.builder()
    .appComponentDeps(
        EntryPoints.get(
            applicationContext,
            CustomComponentDependencies::class.java))
    .build()
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

#### Subcomponents

Subcomponents can be added as a child of any Hilt component in the same way you
would install a normal subcomponent with an injectable subcomponent builder in
Dagger. Just install the subcomponent in a module with the appropriate
`@InstallIn` of the parent.

For example, if you have a `FooSubcomponent` that is a child of the
`ApplicationComponent`, you can install it like the following example:

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@InstallIn(ApplicationComponent.class)
@Module(subcomponents = FooSubcomponent.class)
interface FooModule {}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@InstallIn(ApplicationComponent::class)
@Module(subcomponents = FooSubcomponent::class)
interface FooModule {}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

### Component dependencies for components that map to Hilt components

If you currently use component dependencies and your components map relatively
well to the Hilt components, then as you migrate you’ll also need to keep in the
mind the differences between component dependencies and subcomponents. You may
also want to check out this page which describes some of the reasons Hilt chose
to use [subcomponents](subcomponents-vs-deps.md).

The main differences to be aware of will be that bindings are automatically
inherited from the parent. This means likely getting rid of extra methods for
exposing bindings as well as dealing with any duplicate bindings that may arise
if a binding is defined in both the parent and child components. Getting rid of
those extra methods for exposing bindings is optional as they will not
technically break your build, but it is recommended as they can prevent some
dead code pruning. They can be safely migrated though as described in this
[section](#b-handle-any-extended-interfaces-or-methods).

Here is an example of the exposed bindings:

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@Component
interface MySingletonComponent {
  // These bindings were likely exposed for component dependencies.
  // Consider getting rid of them.
  Foo getFoo();
  Bar getBar();
  Baz getBaz();
  ...
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@Component
interface MySingletonComponent {
  // These bindings were likely exposed for component dependencies.
  // Consider getting rid of them.
  fun getFoo(): Foo
  fun getBar(): Bar
  fun getBaz(): Baz
  ...
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

Then when you follow steps above to migrate components, if your component has a
dep on a component that is equivalent to the Hilt parent, just remove the dep as
you remove the rest of the component.

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
// Just delete these deps as you follow the migration guide for migrating
// the rest of the component
@Component(deps = {MySingletonComponent.class})
interface MyActivityComponent {
  ...
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
// Just delete these deps as you follow the migration guide for migrating
// the rest of the component
@Component(deps = [MySingletonComponent::class])
interface MyActivityComponent {
  ...
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

[`@EntryPoint`]: entry-points.md

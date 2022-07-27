---
layout: default
title: Testing
---

## Introduction

**Note:** Currently, Hilt only supports Android instrumentation and Robolectric
tests (although, see [here](gradle-setup.md#running-with-android-studio) for
limitations when running Robolectric tests via Android Studio). In addition,
Hilt cannot be used in vanilla JVM tests, but it does not prevent you from
writing these tests as you would normally.
{: .c-callouts__note }

Hilt makes testing easier by bringing the power of dependency injection to your
Android tests. Hilt allows your tests to easily access Dagger bindings, provide
new bindings, or even replace bindings. Each test gets its own set of Hilt
components so that you can easily customize bindings at a per-test level.

Many of the testing APIs and functionality described in this documentation are
based upon an unstated philosophy of what makes a good test. For more
details on Hilt's testing philosophy see [here](testing-philosophy.md).

## Test Setup

**Note:** For Gradle users, make sure to first add the Hilt test build dependencies
as described in the
[Gradle setup guide](gradle-setup.md#hilt-test-dependencies).
{: .c-callouts__note }

To use Hilt in a test:

1.  Annotate the test with [`@HiltAndroidTest`],
2.  Add the [`HiltAndroidRule`] test rule,
3.  Use [`HiltTestApplication`] for your Android Application class.

For example:

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@HiltAndroidTest
public class FooTest {
  @Rule public HiltAndroidRule hiltRule = new HiltAndroidRule(this);
  ...
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@HiltAndroidTest
class FooTest {
  @get:Rule val hiltRule = HiltAndroidRule(this)
  ...
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

Note that setting the application class for a test (step 3 above) is dependent
on whether the test is a Robolectric or instrumentation test. For a more
detailed guide on how to set the test application for a particular test
environment, see [Robolectric testing](robolectric-testing.md) or
[Instrumentation testing](instrumentation-testing.md). The remainder of this doc
applies to both Robolectric and instrumentation tests.

If your test requires a custom application class, see the section on
[custom test application](#custom-test-application).

If your test requires multiple test rules, see the section on
[Hilt rule order](#hilt-rule-order) to determine the proper placement of the
Hilt rule.

## Accessing bindings

A test often needs to request bindings from its Hilt components. This section
describes how to request bindings from each of the different components.

### Accessing SingletonComponent bindings

An `SingletonComponent` binding can be injected directly into a test using an
`@Inject` annotated field. Injection doesn't occur until calling
`HiltAndroidRule#inject()`.

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@HiltAndroidTest
class FooTest {
  @Rule public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

  @Inject Foo foo;

  @Test
  public void testFoo() {
    assertThat(foo).isNull();
    hiltRule.inject();
    assertThat(foo).isNotNull();
  }
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@HiltAndroidTest
class FooTest {
  @get:Rule val hiltRule = HiltAndroidRule(this)

  @Inject lateinit var foo: Foo

  @Test
  fun testFoo() {
    assertThat(foo).isNull()
    hiltRule.inject()
    assertThat(foo).isNotNull()
  }
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

### Accessing ActivityComponent bindings

Requesting an `ActivityComponent` binding requires an instance of a Hilt
`Activity`. One way to do this is to define a nested activity within your test
that contains an `@Inject` field for the binding you need. Then create an
instance of your test activity to get the binding.

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@HiltAndroidTest
class FooTest {
  @AndroidEntryPoint
  public static final class TestActivity extends AppCompatActivity {
    @Inject Foo foo;
  }

  // Create the activity through standard testing APIs and get an
  // instance as testActivity. Make sure the activity has gone through
  // onCreate()
  ...

  // Now just access the foo which has been injected on the activity directly
  Foo foo = testActivity.foo;
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@HiltAndroidTest
class FooTest {
  @AndroidEntryPoint
  class TestActivity : AppCompatActivity() {
    @Inject lateinit var foo: Foo
  }

  // Create the activity through standard testing APIs and get an
  // instance as testActivity. Make sure the activity has gone through
  // onCreate()
  ...

  // Now just access the foo which has been injected on the activity directly
  val foo = testActivity.foo
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

Alternatively, if you already have a Hilt activity instance available in your
test, you can get any `ActivityComponent` binding using an
[`EntryPoint`](entry-points.md).

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@HiltAndroidTest
class FooTest {
  @EntryPoint
  @InstallIn(ActivityComponent.class)
  interface FooEntryPoint {
    Foo getFoo();
  }

  ...
  Foo foo = EntryPoints.get(activity, FooEntryPoint.class).getFoo();
}
```
{: .c-codeselector__code .c-codeselector__code_java }

```kotlin
@HiltAndroidTest
class FooTest {
  @EntryPoint
  @InstallIn(ActivityComponent::class)
  interface FooEntryPoint {
    fun getFoo() : Foo
  }

  ...
  val foo = EntryPoints.get(activity, FooEntryPoint::class.java).getFoo()
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

### Accessing FragmentComponent bindings

A `FragmentComponent` binding can be accessed in a similar way to an
[`ActivityComponent` binding](#accessing-activitycomponent-bindings). The main
difference is that accessing a `FragmentComponent` binding requires both an
instance of a Hilt `Activity` and a Hilt `Fragment`.

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@HiltAndroidTest
class FooTest {
  @AndroidEntryPoint
  public static final class TestFragment extends Fragment {
    @Inject Foo foo;
  }

  ...
  Foo foo = testFragment.foo;
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@HiltAndroidTest
class FooTest {
  @AndroidEntryPoint
  class TestFragment : Fragment() {
    @Inject lateinit var foo: Foo
  }

  ...
  val foo = testFragment.foo
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

Alternatively, if you already have a Hilt fragment instance available in your
test, you can get any `FragmentComponent` binding using an
[`EntryPoint`](entry-points.md).

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>

```java
@HiltAndroidTest
class FooTest {
  @EntryPoint
  @InstallIn(FragmentComponent.class)
  interface FooEntryPoint {
    Foo getFoo();
  }

  ...
  Foo foo = EntryPoints.get(fragment, FooEntryPoint.class).getFoo();
}
```
{: .c-codeselector__code .c-codeselector__code_java }

```kotlin
@HiltAndroidTest
class FooTest {
  @EntryPoint
  @InstallIn(FragmentComponent::class)
  interface FooEntryPoint {
    fun getFoo() : Foo
  }

  ...
  val foo = EntryPoints.get(fragment, FooEntryPoint::class.java).getFoo()
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

**Warning**:Hilt does not currently support [`FragmentScenario`](https://developer.android.com/reference/androidx/fragment/app/testing/FragmentScenario)
because there is no way to specify an activity class, and Hilt requires a Hilt
fragment to be contained in a Hilt activity. One workaround for this is to
launch a Hilt activity and then attach your fragment.
{: .c-callouts__warning }

## Replacing bindings {#replacing-bindings}

It's often useful for tests to be able to replace a production binding with a
fake or mock binding to make tests more hermetic or easier to control in test.
The next sections describe some ways to accomplish this in Hilt.

### @TestInstallIn {#testinstallin}

A Dagger module annotated with [`@TestInstallIn`] allows users to replace an
existing [`@InstallIn`] module for all tests in a given source set. For example,
suppose we want to replace `ProdDataServiceModule` with `FakeDataServiceModule`.
We can accomplish this by annotating `FakeDataServiceModule` with
`@TestInstallIn`, as shown below:

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@Module
@TestInstallIn(
    components = SingletonComponent.class,
    replaces = ProdDataServiceModule.class)
interface FakeDataServiceModule {
  @Binds DataService bind(FakeDataService impl);
}
```
{: .c-codeselector__code .c-codeselector__code_java }

```kotlin
@Module
@TestInstallIn(
    components = SingletonComponent::class,
    replaces = ProdDataServiceModule::class)
interface FakeDataServiceModule {
  @Binds fun bind(impl: FakeDataService): DataService
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

A `@TestInstallIn` module can be included in the same source set as your test
sources, as shown below:

```
:foo
  |_ srcs/test/java/my/project/foo
      |_ FooTest.java
      |_ BarTest.java
      |_ FakeDataServiceModule.java
```

However, if a particular `@TestInstallIn` module is needed in multiple Gradle
modules, we recommend putting it in its own Gradle module (usually the same
one as the fake), as shown below:

```
:dataservice-testing
  |_ srcs/main/java/my/project/dataservice/testing
      |_ FakeDataService.java
      |_ FakeDataServiceModule.java

// This depends on `testImplementation project(":dataservice-testing")`
:foo/build.gradle

// This depends on `testImplementation project(":dataservice-testing")`
:bar/build.gradle
```

Putting the `@TestInstallIn` in the same Gradle module as the fake has a number
of benefits. First, it ensures that all clients that depend on the fake properly
replace the production module with the test module. It also avoids duplicating
`FakeDataServiceModule` for every Gradle module that needs it.

Note that `@TestInstallIn` applies to all tests in a given source set. For cases
where an individual test needs to replace a binding that is specific to the
given test, the test can either be moved into its own source set, or it can use
Hilt testing features such as [`@UninstallModules`](#uninstall-modules),
[`@BindValue`](#bind-value), and [nested `@InstallIn` modules](#nested-modules)
to replace bindings specific to that test. These features will be described in
more detail in the following sections.

### @UninstallModules {#uninstall-modules}

**Warning**:Test classes that use `@UninstallModules`, `@BindValue`, or nested
`@InstallIn` modules result in a custom component being generated for that test.
While this may be fine in most cases, it does have an impact on build speed. The
recommended approach is to use [`@TestInstallIn`
modules](#testinstallin) instead.
{: .c-callouts__warning }

A test annotated with [`@UninstallModules`] can uninstall production
`@InstallIn` modules for that particular test (unlike `@TestInstallIn`, it has
no effect on other tests). Once a module is uninstalled, the test can install
new, test-specific bindings for that particular test.

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@UninstallModules(ProdFooModule.class)
@HiltAndroidTest
public class FooTest {
  // ... Install a new binding for Foo
}
```
{: .c-codeselector__code .c-codeselector__code_java }

```kotlin
@UninstallModules(ProdFooModule::class)
@HiltAndroidTest
class FooTest {
  // ... Install a new binding for Foo
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

There are two ways to install a new binding for a particular test:

  * Add an `@InstallIn` module nested within the test that provides the binding.
  * Add an `@BindValue` field within the test that provides the binding.

These two approaches are described in more detail in the next sections.

**Note:** `@UninstallModules` can only uninstall `@InstallIn` modules, not
`@TestInstallIn` modules. If a `@TestInstallIn` module needs to be uninstalled
the module must be split into two separate modules: a `@TestInstallIn` module
that replaces the production module with no bindings (i.e. only removes the
production module), and a `@InstallIn` module that provides the standard fake
so that `@UninstallModules` can uninstall the provided fake.
{: .c-callouts__note }

### Nested @InstallIn modules {#nested-modules}

**Warning**:Test classes that use `@UninstallModules`, `@BindValue`, or nested
`@InstallIn` modules result in a custom component being generated for that test.
While this may be fine in most cases, it does have an impact on build speed. The
recommended approach is to use [`@TestInstallIn`
modules](#testinstallin) instead.
{: .c-callouts__warning }

Normally, `@InstallIn` modules are installed in the Hilt components of every
test. However, if a binding needs to be installed only in a particular test,
that can be accomplished by nesting the `@InstallIn` module within the test
class.

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@HiltAndroidTest
public class FooTest {
  // Nested modules are only installed in the Hilt components of the outer test.
  @Module
  @InstallIn(SingletonComponent.class)
  static class FakeBarModule {
    @Provides
    static Bar provideBar(...) {
      return new FakeBar(...);
    }
  }
  ...
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@HiltAndroidTest
class FooTest {
  // Nested modules are only installed in the Hilt components of the outer test.
  @Module
  @InstallIn(SingletonComponent::class)
  object FakeBarModule {
    @Provides fun provideBar() = Bar()
  }
  ...
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

Thus, if there is another test that needs to provision the same binding with a
different implementation, it can do that without a duplicate binding conflict.

In addition to static nested `@InstallIn` modules, Hilt also supports inner
(non-static) `@InstallIn` modules within tests. Using an inner module allows the
`@Provides` methods to reference members of the test instance.

**Note:** Hilt does not support `@InstallIn` modules with constructor parameters.
{: .c-callouts__note }

### @BindValue {#bind-value}

**Warning**:Test classes that use `@UninstallModules`, `@BindValue`, or nested
`@InstallIn` modules result in a custom component being generated for that test.
While this may be fine in most cases, it does have an impact on build speed. The
recommended approach is to use [`@TestInstallIn`
modules](#testinstallin) instead.
{: .c-callouts__warning }

For simple bindings, especially those that need to also be accessed in the test
methods, Hilt provides a convenience annotation to avoid the boilerplate of
creating a module and method normally required to provision a binding.

[`@BindValue`] is an annotation that allows you to easily bind fields in your
test into the Dagger graph. To use it, just annotate a field with `@BindValue`
and it will be bound to the declared field type with any qualifiers that are
present on the field.

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@HiltAndroidTest
public class FooTest {
  ...
  @BindValue Bar fakeBar = new FakeBar();
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@HiltAndroidTest
class FooTest {
  ...
  @BindValue
  @JvmField
  val fakeBar: Bar = FakeBar()
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

Note that `@BindValue` does not support the use of scope annotations since the
binding's scope is tied to the field and controlled by the test. The field's
value is queried whenever it is requested, so it can be mutated as necessary for
your test. If you want the binding to be effectively singleton, just ensure that
the field is only set once per test case, e.g. by setting the field's value from
either the field's initializer or from within an `@Before` method of the test.

Similarly, Hilt also has a convenience annotation for multibindings with
[`@BindValueIntoSet`], [`@BindElementsIntoSet`], and [`@BindValueIntoMap`] to
support [`@IntoSet`], [`@ElementsIntoSet`], and [`@IntoMap`] respectively. (Note
that `@BindValueIntoMap` requires the field to also be annotated with a map key
annotation.)

**Warning**:Be careful when using [`@BindValue`](#bind-value) or
[non-static inner modules](#nested-modules) with `ActivityScenarioRule`.
`ActivityScenarioRule` creates the activity before calling the `@Before` method,
so if an `@BindValue` field is initialized in `@Before` (or later), then it's
possible for the Activity to inject the binding in its unitialized state. To
avoid this, try initializing the `@BindValue` field in the field's initializer.
{: .c-callouts__warning }

## Custom test application {#custom-test-application}

Every Hilt test must use a Hilt test application as the Android application
class. Hilt comes with a default test application, [`HiltTestApplication`],
which extends [`MultiDexApplication`](https://developer.android.com/studio/build/multidex);
however, there are cases where a test may need to use a different base class.

### @CustomTestApplication

If your test requires a custom base class, [`@CustomTestApplication`] can
be used to generate a Hilt test application that extends the given base class.

To use `@CustomTestApplication`, just annotate a class or interface with
`@CustomTestApplication` and specify the base class in the annotation value:

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
// Generates MyCustom_Application.class
@CustomTestApplication(MyBaseApplication.class)
interface MyCustom {}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
// Generates MyCustom_Application.class
@CustomTestApplication(MyBaseApplication::class)
interface MyCustom
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

In the above example, Hilt will generate an application named
`MyCustom_Application` that extends `MyBaseApplication`. In general, the name of
the generated application will be the name of the annotated class appended with
`_Application`. If the annotated class is a nested class, the name will also
include the name of the outer class separated by an underscore. Note that the
class that is annotated is irrelevant, other than for the name of the generated
application.

### Best practices

As a best practice, avoid using `@CustomTestApplication` and instead use
`HiltTestApplication` in your tests. In general, having your Activity, Fragment,
etc. be independent of the parent they are contained in makes it easier to
compose and reuse it in the future.

However, if you must use a custom base application, there are some subtle
differences with the production lifecycle to be aware of.

One difference is that instrumentation tests use the same application instance
for every test and test case. Thus, it's easy to accidentally leak state
across test cases when using a custom test application. Instead, it's better to
avoid storing any test or test case dependendent state in your application.

Another difference is that the Hilt component in a test application is not
created in `super#onCreate`. This restriction is mainly due to fact that some of
Hilt's features (e.g. [`@BindValue`](#bind-value)) rely on the test instance,
which is not available in tests until after `Application#onCreate` is called.
Thus, unlike production applications, custom base applications must avoid
calling into the component during `Application#onCreate`. This includes
injecting memebers into the application. To prevent this issue, Hilt doesn't
allow injection in the base application.

## Hilt rule order

If your test uses multiple test rules, make sure that the `HiltAndroidRule` runs
before any other test rules that require access to the Hilt component. For
example [`ActivityScenarioRule`](https://developer.android.com/reference/androidx/test/ext/junit/rules/ActivityScenarioRule)
calls `Activity#onCreate`, which (for Hilt activities) requires the Hilt
component to perform injection. Thus, the `ActivityScenarioRule` should run
after the `HiltAndroidRule` to ensure that the component has been properly
initialized.

**Note:** If you're using JUnit < 4.13 use [`RuleChain`](https://junit.org/junit4/javadoc/4.12/org/junit/rules/RuleChain.html)
to specify the order instead.
{: .c-callouts__note }

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@HiltAndroidTest
public class FooTest {
  // Ensures that the Hilt component is initialized before running the ActivityScenarioRule
  @Rule(order = 0) public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

  @Rule(order = 1)
  public ActivityScenarioRule scenarioRule =
      new ActivityScenarioRule(MyActivity.class);
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@HiltAndroidTest
class FooTest {
  // Ensures that the Hilt component is initialized before running the ActivityScenarioRule
  @get:Rule(order = 0)
  val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1)
  val scenarioRule = ActivityScenarioRule(MyActivity::class.java)
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

[`@HiltAndroidApp`]: https://dagger.dev/api/latest/dagger/hilt/android/HiltAndroidApp.html
[`@HiltAndroidTest`]: https://dagger.dev/api/latest/dagger/hilt/android/testing/HiltAndroidTest.html
[`HiltAndroidRule`]: https://dagger.dev/api/latest/dagger/hilt/android/testing/HiltAndroidRule.html
[`HiltTestApplication`]: https://dagger.dev/api/latest/dagger/hilt/android/testing/HiltTestApplication.html
[`@BindValue`]: https://dagger.dev/api/latest/dagger/hilt/android/testing/BindValue.html
[`@BindValueIntoSet`]: https://dagger.dev/api/latest/dagger/hilt/android/testing/BindValueIntoSet.html
[`@BindValueIntoMap`]: https://dagger.dev/api/latest/dagger/hilt/android/testing/BindValueIntoMap.html
[`@BindElementsIntoSet`]: https://dagger.dev/api/latest/dagger/hilt/android/testing/BindElementsIntoSet.html
[`@UninstallModules`]: https://dagger.dev/api/latest/dagger/hilt/android/testing/UninstallModules.html
[`@CustomTestApplication`]: https://dagger.dev/api/latest/dagger/hilt/android/testing/CustomTestApplication.html
[`@IntoSet`]: https://dagger.dev/api/latest/dagger/multibindings/IntoSet.html
[`@IntoMap`]: https://dagger.dev/api/latest/dagger/multibindings/IntoMap.html
[`@ElementsIntoSet`]: https://dagger.dev/api/latest/dagger/multibindings/ElementsIntoSet.html
[`@InstallIn`]: https://dagger.dev/api/latest/dagger/hilt/InstallIn.html
[`@TestInstallIn`]: https://dagger.dev/api/latest/dagger/hilt/testing/TestInstallIn.html

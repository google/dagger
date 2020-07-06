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
  @Rule HiltAndroidRule rule = new HiltAndroidRule(this);
  ...
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@HiltAndroidTest
class FooTest {
  @get:Rule val rule = HiltAndroidRule(this)
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

### Accessing ApplicationComponent bindings

An `ApplicationComponent` binding can be injected directly into a test using an
`@Inject` annotated field. Injection doesn't occur until calling
`HiltAndroidRule#inject()`.

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@HiltAndroidTest
class FooTest {
  @Rule HiltAndroidRule hiltRule = new HiltAndroidRule(this);

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
  @get:Rule HiltAndroidRule hiltRule = HiltAndroidRule(this)

  @Inject foo: Foo

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

  ...
  Foo foo = testActivity.foo;
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@HiltAndroidTest
class FooTest {
  @AndroidEntryPoint
  class TestActivity : AppCompatActivity() {
    @Inject foo: Foo
  }

  ...
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
    @Inject foo: Foo
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

## Adding bindings

A test may need to provision additional Dagger bindings that are not included in
the production build of the application. In addition, tests may need to
provision the same binding with different values per test. This section
describes how to provision bindings for a test using Hilt.

### Nested modules {#nested-modules}

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
  @InstallIn(ApplicationComponent.class)
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
  @InstallIn(ApplicationComponent::class)
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
  @BindValue fakeBar: Bar = new FakeBar()
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

### Caveats

Be careful when using [`@BindValue`](#bind-value) or
[non-static inner modules](#nested-modules) with `ActivityScenarioRule`.
`ActivityScenarioRule` creates the activity before calling the `@Before` method,
so if an `@BindValue` field is initialized in `@Before` (or later), then it's
possible for the Activity to inject the binding in its unitialized state. To
avoid this, try initializing the `@BindValue` field in the field's initializer.

## Replacing bindings

### @UninstallModules {#uninstall-modules}

A common use case within tests is the need to replace a production binding with
test binding, e.g. a fake or mock. Within a Hilt test, a production binding can
be replaced by first uninstalling the production module it's contained in using
[`@UninstallModules`], then provisioning a new binding from within the test.

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@UninstallModules(ProdFooModule.class)
@HiltAndroidTest
public class FooTest {

  ...
  @BindValue Foo fakeFoo = new FakeFoo();
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@UninstallModules(ProdFooModule::class)
@HiltAndroidTest
class FooTest {

  ...
  @BindValue fakeFoo: Foo = new FakeFoo()
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

Note that `@UninstallModules` is equivalent to removing the `@InstallIn`
annotation from that module with respect to the given test. Hilt does not
directly support uninstalling individual bindings, but it's effectively possible
by only including a single binding in a given module.

## Custom test application

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
  @Rule(order = 0) HiltAndroidRule hiltRule = new HiltAndroidRule(this);

  @Rule(order = 1)
  ActivityScenarioRule scenarioRule =
      new ActivityScenarioRule(MyActivity.class);
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@HiltAndroidTest
class FooTest {
  // Ensures that the Hilt component is initialized before running the ActivityScenarioRule
  @get:Rule(order = 0)
  val hiltRule = HiltAndroidRule(this);

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


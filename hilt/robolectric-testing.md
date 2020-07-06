---
layout: default
title: Robolectric testing
---

**Warning:** See [here](gradle-setup.md#running-with-android-studio) for
limitations when running Robolectric tests via Android Studio when using the
[Hilt Gradle plugin](gradle-setup.md#hilt-gradle-plugin).
{: .c-callouts__warning }

## Setting the test application

Hilt's [testing APIs](testing.md) are built to be agnostic of the particular
testing environment; however, the instructions for setting up the application
class in your test will depend on whether you are using Robolectric or Android
instrumentation tests.

For Robolectric tests, the application can be set either locally using
[`@Config`] or globally using `robolectric.properties`. For Hilt tests, the
application must either be [`HiltTestApplication`] or one of Hilt's
[custom test applications](testing.md#custom-test-application).

**Note:** This setup is not particular to Hilt. See the
[offical Robolectric documentation] for more details.
{: .c-callouts__note }

### Using @Config

The Hilt application class can be set locally using
`@Config`. To set the application, just annotate the test (or test method) with
`@Config` and set the value of the annotation to the desired application class.

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@HiltAndroidTest
@Config(application = HiltTestApplication.class)
public class FooTest {...}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
class FooTest {...}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

### Using robolectric.properties


The Hilt application class can be set globally using the
`robolectric.properties` file. To set the application, just create the
`robolectric.properties` file in the appropriate `resources` package, and set
the Hilt test application class.

```groovy
application=dagger.hilt.android.testing.HiltTestApplication
```

This approach can be useful when a test needs to run in both Robolectric and
Android instrumentation environments, since the `@Config` annotation cannot be
used with Android instrumentation tests.

[`@Config`]: http://robolectric.org/javadoc/latest/org/robolectric/annotation/Config.html
[offical Robolectric documentation]: http://robolectric.org/configuring/
[`HiltTestApplication`]: https://dagger.dev/api/latest/dagger/hilt/android/testing/HiltTestApplication.html

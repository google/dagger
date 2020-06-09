---
layout: default
title: Instrumentation testing
---

## Setting the test application

Hilt's [testing APIs](testing.md) are built to be agnostic of the particular
testing environment; however, the instructions for setting up the application
class in your test will depend on whether you are using Robolectric or Android
instrumentation tests.

For Android instrumentation tests, the application can be set using a custom
test runner that extends [`AndroidJUnitRunner`]. To set the application using
the runner, just override the `newApplication` method and pass in the
application class name. For Hilt tests, the application must either be
[`HiltTestApplication`] or one of Hilt's
[custom test applications](testing.md#custom-test-application).

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
package my.pkg;

public final class MyTestRunner extends AndroidJUnitRunner {
  @Override
  public Application newApplication(
      ClassLoader cl, String appName, Context context) {
    return super.newApplication(
        cl, HiltTestApplication.class.getName(), context);
  }
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
package my.pkg

class MyTestRunner extends AndroidJUnitRunner {
  override fun newApplication(
      cl: ClassLoader,
      appName: String,
      context: Context) : Application {
    return super.newApplication(
        cl, HiltTestApplication::class.java.getName(), context)
  }
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

In addition, the `testInstrumentationRunner` must be configured in the
`build.gradle` file for the given Gradle module:

```groovy
android {
  defaultConfig {
      testInstrumentationRunner "my.pkg.MyTestRunner"
  }
}
```

[`AndroidJUnitRunner`]: https://developer.android.com/reference/androidx/test/runner/AndroidJUnitRunner
[`HiltTestApplication`]: https://dagger.dev/api/latest/dagger/hilt/android/testing/HiltTestApplication.html

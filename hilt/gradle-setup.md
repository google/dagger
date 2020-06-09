---
layout: default
title: Gradle Build Setup
---

## Hilt dependencies

To use Hilt, add the following build dependencies to the Android Gradle module's
`build.gradle` file:

```groovy
dependencies {
  implementation 'com.google.dagger:hilt-android:<VERSION>'
  annotationProcessor 'com.google.dagger:hilt-android-compiler:<VERSION>'

  // For instrumentation tests
  androidTestImplementation  'com.google.dagger:hilt-android-testing:<VERSION>'
  androidTestAnnotationProcessor 'com.google.dagger:hilt-android-compiler:<VERSION>'

  // For local unit tests
  testImplementation 'com.google.dagger:hilt-android-testing:<VERSION>'
  testAnnotationProcessor 'com.google.dagger:hilt-android-compiler:<VERSION>'
}
```

## Using Hilt with Kotlin

If using Kotlin, then apply the
[kapt plugin](https://kotlinlang.org/docs/reference/kapt.html) and declare the
compiler dependency using `kapt` instead of `annotationProcessor`.

Additionally configure kapt to correct error types by setting
[`correctErrorTypes`](https://kotlinlang.org/docs/reference/kapt.html#non-existent-type-correction)
to true.

```groovy
dependencies {
  implementation 'com.google.dagger:hilt-android:<VERSION>'
  kapt 'com.google.dagger:hilt-android-compiler:<VERSION>'

  // For instrumentation tests
  androidTestImplementation  'com.google.dagger:hilt-android-testing:<VERSION>'
  kaptAndroidTest 'com.google.dagger:hilt-android-compiler:<VERSION>'

  // For local unit tests
  testImplementation 'com.google.dagger:hilt-android-testing:<VERSION>'
  kaptTest 'com.google.dagger:hilt-android-compiler:<VERSION>'
}

kapt {
 correctErrorTypes true
}
```

## Hilt Gradle plugin {#hilt-gradle-plugin}

The Hilt Gradle plugin runs a bytecode transformation to make the APIs easier to
use. The plugin was created for a better developer experience in the IDE since
the generated class can disrupt code completion for methods on the base class.
The examples throughout the docs will assume usage of the plugin. To configure
the Hilt Gradle plugin first declare the dependency in your project's root
`build.gradle` file:

<!-- TODO(user): Add .kts (kotlin scripting) code blocks. -->

```groovy
buildscript {
  repositories {
    // other repositories...
    mavenCentral()
  }
  dependencies {
    // other plugins...
    classpath 'com.google.dagger:hilt-android-gradle-plugin:<version>'
  }
}
```

then in the `build.gradle` of your Android Gradle modules apply the plugin:

```groovy
apply plugin: 'com.android.application'
apply plugin: 'dagger.hilt.android.plugin'

android {
  // ...
}
```

### Using the plugin with others

The Hilt Dagger plugin sets some data inside `annotationProcessorOptions.arguments`. 
If you use other libraries that do the same, like the Room library, you need to add their arguments instead of setting them:

```groovy
javaCompileOptions {
  annotationProcessorOptions {
    arguments += ["room.schemaLocation" : "$projectDir/schemas".toString()]
  }
}
```

If the `+` is missing, `arguments` will be replaced, and the build with fail with an error similar to this one:
```
Expected @HiltAndroidApp to have a value. Did you forget to apply the Gradle Plugin?
```

### Why use the plugin?

One of the main benefits of the Gradle plugin is that it makes using
`@AndroidEntryPoint` and `@HiltAndroidApp` easier. Without the Gradle plugin,
the base class must be specified in the annotation and the annotated class must
extend the generated class:

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

### Local test configuration {#gradle-plugin-local-tests}

The plugin by default also transforms instrumented tests classes (usually
located in the `androidTest` source folder). But an additional configuration is
required for local unit tests (usually located in the `test` source folder). To
enable transforming `@AndroidEntryPoint` classes in local unit tests then apply
the following configuration in your module's `build.gradle`:

```
hilt {
    enableTransformForLocalTests = true
}
```

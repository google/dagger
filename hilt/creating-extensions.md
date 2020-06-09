---
layout: default
title: Creating extensions
---

## Generating modules and entry points

Hilt is particularly well-suited for extensions or libraries that want to
integrate with Hilt due to the standard components and the way modules and entry
points are picked up from the classpath.

However, extensions that generate an `@InstallIn` module or entry point will
need to add some extra information to the generated classes in order for them to
be picked up by Hilt correctly.

### @GeneratesRootInput

Because Hilt picks up modules and entry points from the classpath implicitly,
Hilt needs extra information to know if it needs to wait for your extension to
generate code before it tries to generate the Dagger components. This is done by
annotating your annotation class that triggers your code generation with
[`@GeneratesRootInput`].

For example, if an extension generated a module every time someone used a
`@GenerateMyModule` annotation, `@GenerateMyModule` would need to be annotated
like so:

<div class="c-codeselector__button c-codeselector__button_java">Java
</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin
</div>
```java
@GeneratesRootInput
public @interface GenerateMyModule {}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@GeneratesRootInput
annotation class GenerateMyModule {}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

Note that if not annotated, Hilt is not necessarily guaranteed to miss your
modules because it may still pick them up if waiting on something else to be
generated. This is of course unreliable.

### @OriginatingElement

As described in the [testing page](testing.md#nested-modules), nested modules in
tests are isolated to the enclosing test. Generated modules for a test, however,
cannot be generated as a nested class. To properly support this, generated code
should be annotated with an [`@OriginatingElement`] annotation with the
top-level class as the value. Note that this is not always the same as the
enclosing class since there may be many layers of nesting.

For example, assume an extension is triggered by the following code and
generates a module called `FooTest_FooModule`.

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@HiltAndroidTest
public class FooTest {
  @GenerateMyModule
  private Foo foo = new Foo();
  ...
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@HiltAndroidTest
class FooTest {
  @GenerateMyModule
  val foo: Foo = Foo()
  ...
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

Then the generated `FooTest_FooModule` would need to be annotated like so:

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@OriginatingElement(FooTest.class)
@Module
@InstallIn(ApplicationComponent.class)
interface FooTest_FooModule {
  ...
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@OriginatingElement(FooTest::class)
@Module
@InstallIn(ApplicationComponent::class)
interface FooTest_FooModule {
  ...
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

[`@GeneratesRootInput`]: https://dagger.dev/api/latest/dagger/hilt/GeneratesRootInput.html
[`@OriginatingElement`]: https://dagger.dev/api/latest/dagger/hilt/codegen/OriginatingElement.html


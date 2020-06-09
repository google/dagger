---
layout: default
title: Optional injection
---

## Why would you need optional injection?

Hilt fragments need to be attached to Hilt activities and Hilt activities need
to be attached to Hilt applications. While this is a natural restriction for
pure Hilt codebases, it may be an issue during a migration to Hilt if you have a
fragment or activity that is used in a non-Hilt context. For example, say you
want to migrate a fragment to Hilt but it is used in too many places to migrate
at once. Without optional injection, you would have to migrate every activity
that uses that fragment to Hilt first otherwise the fragment will crash when
looking for the Hilt components when it is trying to inject itself. Depending on
the size of your codebase, this could be a large undertaking.

## How to use `@OptionalInject`

If you mark an [`@AndroidEntryPoint`](android-entry-point.md) class with
[`@OptionalInject`](https://dagger.dev/api/latest/dagger/hilt/android/migration/OptionalInject.html)
then it will only try to inject if the parent is using Hilt and not require it.
Using this annotation will also cause a `wasInjectedByHilt()` method to be
generated on the generated base class that returns true if it was successful
injecting.

**Note:** Because API generated on the base class is inaccessible to users of
the [gradle plugin](gradle-setup.md#hilt-gradle-plugin), there is an alternative
API to access this functionality using a static helper method in
[`OptionalInjectCheck`](https://dagger.dev/api/latest/dagger/hilt/android/migration/OptionalInjectCheck.html).
{: .c-callouts__note }

This gives you the chance to provide dependencies in a different way (usually
whichever way you were getting dependencies before using Hilt).

For example:

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@OptionalInject
@AndroidEntryPoint
public final class MyFragment extends Fragment {

  @Inject Foo foo;

  @Override public void onAttach(Activity activity) {
    super.onAttach(activity);  // Injection will happen here, but only if the Activity used Hilt
    if (!wasInjectedByHilt()) {
      // Get Dagger components the previous way and inject
    }
  }
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@OptionalInject
@AndroidEntryPoint
class MyFragment : Fragment() {

  @Inject lateinit var foo: Foo

  override fun onAttach(activity: Activity) {
    super.onAttach(activity)  // Injection will happen here, but only if the Activity used Hilt
    if (!wasInjectedByHilt()) {
      // Get Dagger components the previous way and inject
    }
  }
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

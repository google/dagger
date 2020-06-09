---
layout: default
title: Android Entry Points
---

**Note:** Examples on this page assume usage of the Gradle plugin. If you are **not**
using the plugin, please read this [page](gradle-setup.md#hilt-gradle-plugin) for
details.
{: .c-callouts__note }


## Android types

Once you have enabled members injection in your `Application`, you can start
enabling members injection in your other Android classes using the
[`@AndroidEntryPoint`](https://dagger.dev/api/latest/dagger/hilt/android/AndroidEntryPoint.html)
annotation. You can use `@AndroidEntryPoint` on the following types:

1.  Activity
1.  Fragment
1.  View
1.  Service
1.  BroadcastReceiver[^1]

ViewModels are not directly supported, but are instead supported by a
[Jetpack extension](https://developer.android.com/training/dependency-injection/hilt-jetpack).
The following example shows how to add the annotation to an activity, but the
process is the same for other types.

[^1]: Unlike the other supported Android classes, `BroadcastReceivers` do not
    have their own Dagger component and are instead simply injected from the
    `ApplicationComponent`.


To enable members injection in your activity, annotate your class with
[`@AndroidEntryPoint`](https://dagger.dev/api/latest/dagger/hilt/android/AndroidEntryPoint.html).

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@AndroidEntryPoint
public final class MyActivity extends MyBaseActivity {
  // Bindings in ApplicationComponent or ActivityComponent
  @Inject Bar bar;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    // Injection happens in super.onCreate().
    super.onCreate();

    // Do something with bar ...
  }
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@AndroidEntryPoint
class MyActivity : MyBaseActivity() {
  // Bindings in ApplicationComponent or ActivityComponent
  @Inject lateinit var bar: Bar

  override fun onCreate(savedInstanceState: Bundle?) {
    // Injection happens in super.onCreate().
    super.onCreate()

    // Do something with bar ...
  }
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

**Note:** Hilt currently only supports activities that extend [`ComponentActivity`](https://developer.android.com/reference/androidx/activity/ComponentActivity) and
fragments that extend androidx library
[`Fragment`](https://developer.android.com/reference/androidx/fragment/app/Fragment),
not the (now deprecated)
[`Fragment`](https://developer.android.com/reference/android/app/Fragment) in
the Android platform.
{: .c-callouts__note }


### Retained Fragments

Calling `setRetainInstance(true)` in a Fragment's `onCreate` method will keep a
fragment instance across configuration changes (instead of destroying and
recreating it).

A Hilt fragment _should never_ be retained because it holds a reference to the
component (responsible for injection), and that component holds references to
the previous Activity instance. In addition, scoped bindings and providers that
are injected into the fragment can also cause memory leaks if a Hilt fragment is
retained. To prevent Hilt fragments from being retained, a runtime exception
will be thrown on configuration change if a retained Hilt fragment is detected.

A non-Hilt fragment _can_ be retained, even if attached to a Hilt activity.
However, if that fragment contains a Hilt child fragment, a runtime exception
will be thrown when a configuration change occurs.

**Note:** While it's not recommended, Hilt fragments _can_ be detached and
reattached to the _same_ activity instance. In this case, the Hilt fragment will
only be injected on the first call to `onAttach`. Note that this is not the same
as retaining a fragment, because a retained fragment will be reattached to a
different instance of the activity. Again, this is not recommended, and it is
often less confusing to just create a new fragment instance for each usage.
{: .c-callouts__note }

### Views with Fragment bindings

By default, only `ApplicationComponent` and `ActivityComponent` bindings can be
injected into the view. To enable fragment bindings in your view, add the
`@WithFragmentBindings` annotation to your class.

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@WithFragmentBindings
@AndroidEntryPoint
public final class MyView extends MyBaseView {
  // Bindings in ApplicationComponent, ActivityComponent,
  // FragmentComponent, and ViewComponent
  @Inject Bar bar;

  public MyView(Context context, AttributeSet attributeSet) {
    super(context, attributeSet);

    // Do something with bar...
  }

  @Override
  public void onFinishInflate() {
    super.onFinishInflate();

    // Find & assign child views from the inflated hierarchy.
  }
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@AndroidEntryPoint
@WithFragmentBindings
class MyView : MyBaseView {
  // Bindings in ApplicationComponent, ActivityComponent,
  // FragmentComponent, and ViewComponent
  @Inject lateinit var bar: Bar

  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

  init {
    // Do something with bar ...
  }

  override fun onFinishInflate() {
    super.onFinishInflate();

    // Find & assign child views from the inflated hierarchy.
  }
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

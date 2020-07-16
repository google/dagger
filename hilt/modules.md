---
layout: default
title: Modules
---

## Hilt Modules

Hilt modules are standard Dagger modules that have an additional
[`@InstallIn`](https://dagger.dev/api/latest/dagger/hilt/InstallIn.html)
annotation that determines which
[Hilt component(s)](components.md#hilt-components) to install the module into.

When the Hilt components are generated, the modules annotated with `@InstallIn`
will be installed into the corresponding component or subcomponent via
`@Component#modules` or `@Subcomponent#modules` respectively. Just
like in Dagger, installing a module into a component allows that binding to be
accessed as a dependency of other bindings in that component or any child
component(s) below it in the
[component hierarchy](components.md#component-hierarchy). They can also be
accessed from the corresponding `@AndroidEntryPoint` classes. Being installed in
a component also allows that binding to be scoped to that component.

### Using `@InstallIn`

A module is installed in a [Hilt Component](components.md) by annotating the
module with the
[`@InstallIn`](https://dagger.dev/api/latest/dagger/hilt/InstallIn.html)
annotation. These annotations are required on all Dagger modules when using
Hilt, but this check may be optionally
[disabled](compiler-options.md#disable-install-in-check).

**Note:** If a module does not have an `@InstallIn` annotation, the module will
not be part of the component and may result in compilation errors.
{: .c-callouts__note }


Specify which Hilt Component to install the module in by passing in the
appropriate [Component](components.md) type(s) to the `@InstallIn` annotation.
For example, to install a module so that anything in the application can use it,
use `ApplicationComponent`:

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@Module
@InstallIn(ApplicationComponent.class) // Installs FooModule in the generate ApplicationComponent.
public final class FooModule {
  @Provides
  static Bar provideBar() {...}
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@Module
@InstallIn(ApplicationComponent::class) // Installs FooModule in the generate ApplicationComponent.
object FooModule {
  @Provides
  fun provideBar(): Bar {...}
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

Each component comes with a scoping annotation that can be used to memoize a
binding to the lifetime of the component. For example, to scope a binding to the
`ApplicationComponent` component, use the `@Singleton` annotation:

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@Module
@InstallIn(ApplicationComponent.class)
public final class FooModule {
  // @Singleton providers are only called once per ApplicationComponent instance.
  @Provides
  @Singleton
  static Bar provideBar() {...}
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@Module
@InstallIn(ApplicationComponent::class)
object class FooModule {
  // @Singleton providers are only called once per ApplicationComponent instance.
  @Provides
  @Singleton
  fun provideBar(): Bar {...}
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

In addition, each component has bindings that are available to it by default.
(See [Hilt Components](components.md#component-bindings) for a complete list.)
For example, the `ApplicationComponent` component provides the `Application`
binding:

<!-- TODO(user): Change this to @ApplicationContext Application when that
     is added in -->

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@Module
@InstallIn(ApplicationComponent.class)
public final class FooModule {
  // @InstallIn(ApplicationComponent.class) module providers have access to
  // the Application binding.
  @Provides
  static Bar provideBar(Application app) {...}
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@Module
@InstallIn(ApplicationComponent::class)
object class FooModule {
  // @InstallIn(ApplicationComponent.class) module providers have access to
  // the Application binding.
  @Provides
  fun provideBar(app: Application): Bar {...}
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

### Installing a module in multiple components

A module can be installed in multiple components. For example, maybe you have a
binding in `ViewComponent` and `ViewWithFragmentComponent` and do not want to
duplicate modules. `@InstallIn({ViewComponent.class,
ViewWithFragmentComponent.class})` will install a module in both components.

There are three rules to follow when installing a module in multiple components:

*   Providers can only be scoped if _all_ of the components support the _same_
    scope annotation. For example, a binding provided in `ViewComponent` and
    `ViewWithFragmentComponent` can be `@ViewScoped` because they both support
    that scope annotation. A binding provided in `Fragment` and `Service` can
    not be scoped with any of the standard scopes.
*   Providers can only inject bindings if _all_ of the components have access to
    those bindings. For example, a binding in `ViewComponent` and
    `ViewWithFragmentComponent` can inject a `View`, whereas something bound in
    `FragmentComponent` and `ServiceComponent` could not inject either
    `Fragment` or `Service`.
*   A child and ancestor component should not install the same module. (Just
    install the module in the ancestor, and the child will have access to those
    bindings).

## App Build variants

Most Android apps will want to pull in different modules and bindings depending
on the build variant of the app (e.g. production, debug, testing, etc.).

In Hilt, if your binary's build target transitively depends on a module, then
that module will be installed in the appropriate component for your app. This
makes configuration as easy as defining a different build target and pulling
different deps into your binary definition.

<!-- TODO(user): Discuss source sets for Gradle -->

## Bazel: Organizing your BUILD files {#organizing-build-files}

Because Bazel tends to enourage separation into finer-grained build targets, it
is often better for tests to just avoid depending on modules you intend to
replace in tests instead of [uninstalling](testing.md#uninstall-modules) them.
This is because it reduces the build dependencies of your test which can lead to
overall faster build times.


When organizing your BUILD target for a module, you should consider if this
module should be replaceable in tests or other configurations of your app. If it
should never be replaced, then feel free to include the module with your other
code sources.

If it should be replaceable though, you should create a separate target for your
module. This target can then be pulled in at the root of your app so that each
test root (or other configuration root) can decide whether to use your module or
not.


There are two ways to organize your BUILD targets with regards to modules
depending on the situation:

*   Simply include modules with your normal build target. This will mean that
    users depending on your library will always get your definition.
*   For those bindings that you want to be replaceable in tests, split your
    modules out into a target called “module” that is meant to be depended on at
    the `android_binary` level.

It is recommended to choose the first method by default and use the second
method only for bindings that need to be replaceable in tests. It is expected,
though, that many libraries will use both methods.

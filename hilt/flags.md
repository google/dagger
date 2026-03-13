---
layout: default
title: Flags
redirect_from:
  - /hilt/compiler-options
---

## Compiler Options

### Turning off the `@InstallIn` check {#disable-install-in-check}

By default, Hilt checks `@Module` classes for the `@InstallIn` annotation and
raises an error if it is missing. This is because if someone accidentally
forgets to put `@InstallIn` on a module, it could be very hard to debug that
Hilt isn't picking it up.

This check can sometimes be overly broad though, especially if in the middle of
a migration. To turn off this check, this flag can be used:

`-Adagger.hilt.disableModulesHaveInstallInCheck=true`.

Alternatively, the check can be disabled at the individual module level by
annotating the module with
[`@DisableInstallInCheck`](https://dagger.dev/api/latest/dagger/hilt/migration/DisableInstallInCheck.html).

## Sharing test components {#sharing-test-components}

In cases where a test does not define `@BindValue` fields or inner modules, it
can share a generated component with other tests in the same compilation unit.
Sharing components may reduce the amount of generated code that javac needs to
compile, improving build times.

When component sharing is enabled, all test components are generated in a
separate package from your test class. This may cause visibility and name
collision issues. Those issues are described in the sections below.

Sharing components is enabled by default. If your project does not build due to
component sharing, you can disable this behavior and have Hilt generate a Dagger
separate `@Component` for each `@HiltAndroidTest` using this flag:

`-Adagger.hilt.shareTestComponents=false`

However, consider the following fixes in order to avoid disabling this behavior.

### Entry point method return types must be public

Because the shared components must be generated in a common package location
that is outside of the tests' packages, any entry points included by the test
must only provide publicly visible bindings. This is in order to be referenced
by the generated components. You may find that you will have to mark some Java
types as `public` (or remove `internal` in Kotlin).

### Entry point method names must be unique

Because the shared components must include entry points from every test class,
explicit `@EntryPoint` methods must not clash. Test `@EntryPoint` methods must
either be uniquely named across test classes, or must return the same type.

### Modules with non-static/non-abstract methods must be public

The generated Dagger component must be able to instantiate modules that have
methods that are non-static and non-abstract. This requires referencing the
module type explicitly across package boundaries. You may need to mark some
package-private test modules as `public`.

## Turning off the cross compilation root validation {#disable-cross-compilation-root-validation}

By default, Hilt checks that:

  * If there are `@HiltAndroidTest` or `@HiltAndroidApp` usages in the current
    compilation unit, then there cannot be `@HiltAndroidTest` usages in any
    previous compilation units.
  * If there are `@HiltAndroidApp` usages in the current compilation unit, then
    there cannot be `@HiltAndroidApp` usages in any previous compilation units.

This check can sometimes be overly broad though, especially if in the middle of
a migration. To turn off this check, this flag can be used:

`-Adagger.hilt.disableCrossCompilationRootValidation=true`.

## Fast Init {#fast-init}

Hilt ["Fast Init"][fast-init-link] is an optimization that improves
startup performance by avoiding class loading during Hilt initialization. It is
the recommended mode for android applications.

If you are not using the Hilt Gradle plugin, see Dagger's
[compiler options][compiler-options-link] for instructions on how
to enable Fast Init.

If you are using the Hilt Gradle plugin:

  * FastInit is enabled by default.
  * FastInit can only be disabling via the `dagger.hilt.fastInit` Gradle
    property, e.g. via command line: `-Pdagger.hilt.fastInit=false`.
  * Hilt will fail if you try to set the flag via Annotation Processing options.

## Runtime flags

Runtime flags to control Hilt behavior for rollout of changes. These flags are
usually meant to be temporary and so defaults may change with releases and then
these flags may eventually be removed, just like compiler options with similar
purposes.

### Disable Fragment.getContext() fix

See https://github.com/google/dagger/pull/2620 for the change that introduces
the `getContext()` fix. This flag controls if fragment code should use the fixed
`getContext()` behavior where it correctly returns null after a fragment is
removed. This fixed behavior matches the behavior of a regular, non-Hilt
fragment and can help catch issues where a removed or leaked fragment is
incorrectly used. This is a runtime flag though because code previous relying on
the method returning a non-null value after fragment removal could break.

By default, the fix is turned off (e.g. the flag for disabling is true), but the
fixed version may be used by setting the flag at runtime. The default for this
flag may change in a future release.

In order to set the flag, bind a boolean value qualified with
`DisableFragmentGetContextFix` into a set in the `SingletonComponent`. A set is
used instead of an optional binding to avoid a dependency on Guava. Only one
value may be bound into the set within a given app. Example for binding the
value:

```java
@Module
@InstallIn(SingletonComponent.class)
public final class DisableFragmentGetContextFixModule {
@Provides
@IntoSet
@FragmentGetContextFix.DisableFragmentGetContextFix
  static Boolean provideDisableFragmentGetContextFix() {
    // Return true or false depending on some rollout logic for your app
    // True is the default value if unset. Use false to use the fixed behavior.
 }
}
```

This flag used to be paired with a compiler option flag
`dagger.hilt.android.useFragmentGetContextFix`, however, as of Dagger 2.40 this
compiler option has now been removed and this behavior is only controlled via
the runtime flag.

[fast-init-link]: https://dagger.dev/compiler-options.html#fastinit-mode
[compiler-options-link]: https://dagger.dev/compiler-options.html#fastinit-mode

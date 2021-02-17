---
layout: default
title: Compiler Options
---

## Turning off the `@InstallIn` check {#disable-install-in-check}

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

By default, Hilt generates a Dagger `@Component` for each `@HiltAndroidTest`.
However, in cases where a test does not define `@BindValue` fields or inner
modules, it can share a component with other tests in the same compilation unit.
Sharing components may reduce the amount of generated code that javac needs to
compile.

To enable test component sharing, use this flag:

`-Adagger.hilt.shareTestComponents=true`

### Caveats

You may run into the following issues concerning entry point visibility when
first enabling shared test components, which stem from the fact that enabling
this feature causes Hilt to generate the shared component in a separate package
from your test class.

These limitations are similar to those for production entry points, in which the
`@HiltAndroidApp` typically lives in separate package from application entry
points.

#### Entry point type visibility

Because the shared components must be generated in a common package location
that is outside of the tests' packages, any entry points included by the test
must only provide publicly visible bindings. This is in order to be referenced
by the generated components. You may find that you will have to mark some Java
types as `public` (or remove `internal` in Kotlin) when first enabling this
option.

#### Entry point method names

Because the shared components must include entry points from every test class,
explicit `@EntryPoint` methods may not clash. Test `@EntryPoint` methods must
either be uniquely named across test classes, or must return the same type.

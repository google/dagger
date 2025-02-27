---
layout: default
title: Compiler Options
redirect_from:
  - /compiler-options
---

## useBindingGraphFix

In v2.55, Dagger introduced a rewrite of its core binding graph creation logic
that fixes many subtle bugs that have plague the previous version. However,
these fixes also come with a behavior change (see details below), so we've
introduced a flag to ease the migration to the new behavior. To enable the
fixes, pass the following compiler option:

`-Adagger.useBindingGraphFix=ENABLED`.

In a future release, we will enable this flag by default, and remove the flag
entirely once users have had a chance to migrate.

### Background

In the last few years, Dagger has seen a number of subtle bugs which have led to
incorrect binding graphs (e.g. missing multibindings), nonsensical error
messages (e.g. dependency traces that don't match the reported error), and
difficult to reproduce issues (e.g. issues that depend on the order of bindings
in the user's code). The root cause of these bugs stemmed from issues in the
[`LegacyBindingGraphFactory`] which is responsible for iterating through the
bindings of a component and creating the binding graph. To fix these bugs, we've
rewritten the core logic to be more robust (e.g. replacing custom iteration
logic with standard graph data structures and algorithms). However, enabling
these fixes also comes with a behavior change that could affect some users in
rare cases.

[`LegacyBindingGraphFactory`]:(https://github.com/google/dagger/blob/master/dagger-compiler/main/java/dagger/internal/codegen/binding/LegacyBindingGraphFactory.java)

### Behavior changes

With `-Adagger.useBindingGraphFix=ENABLED`, module bindings are no longer
allowed to "float" from their installed component into a subcomponent to satisfy
a missing dependency. For example, consider a module that provides an
`ActivityBinding` into the `SingletonComponent`, but its `Activity` dependency
is not available from the `SingletonComponent`:

```java
@Module
@InstallIn(SingletonComponent.class)
interface ActivityBindingModule {
  @Provides
  static ActivityBinding provide(Activity activity, Set<Foo> multibindings) {
    …
  }
}
```

With the old behavior (`useBindingGraphFix=DISABLED`), Dagger won't report a
missing binding error if there is a multibinding contribution for `Set<Foo>`
installed in the `ActivityComponent` and nothing in the `SingletonComponent`
requests the `ActivityBinding`. With the new behavior
(`useBindingGraphFix=ENABLED`), Dagger will always report a missing binding
error.

In practice, the new behavior is much easier to reason about, and if you are
broken by this change it can usually be fixed by just moving the module to the
correct component. For example, we can fix the case above by installing the
module into the `ActivityComponent` instead of the `SingletonComponent`:

## fastInit mode

You can choose to generate your Dagger [`@Component`]s in a mode that
prioritizes fast initialization. Normally, the number of classes loaded when
initializing a component (i.e., when calling `DaggerFooComponent.create()` or
`DaggerFooComponent.builder()...build()`) scales with the number of bindings in
the component. In fastInit mode, it doesn’t. There are tradeoffs, however.
Normally, each [`Provider`] you inject only holds a reference to providers of
all its transitive dependencies; in fastInit mode, each `Provider` holds a
reference to the entire component, including all scoped instances. This means
that in fastInit mode, holding/leaking a `Provider` is equivalent to
holding/leaking the entire component, which is usually a much larger set of
objects than just the scoped dependencies of the `Provider` in the default mode.

You should evaluate this tradeoff for your application when choosing to build in
fastInit mode, particularly if you are aware of cases where your application
intentially holds a `Provider` beyond the lifetime of the component. Take care
to measure your application’s startup time with and without fastInit mode to see
how much benefit it has for your users. (Please let us know whether it’s working
for you!)

In general, for environments like Android where Dagger initialization is often
on the user’s critical path and where class loading is expensive, fastInit mode
is likely the correct choice. The main drawback of fastInit mode means that it
is easier to leak a `Context` like the activity with a leaked `Provider`, but
for unintentional cases, those are leaks that should be fixed anyway and those
leaks would always have the potential to have grow dependencies on the activity
as the codebase changes (thereby leaking it in the default mode as well).

To enable fastInit mode, pass the following option to javac when building your
Dagger [`@Component`]: `-Adagger.fastInit=enabled`. Note that if you are using
the Hilt Gradle Plugin in your project, fastInit mode will already be enabled by
default.

## Turning on code formatting

Using `-Adagger.formatGeneratedSource=enabled` will cause Dagger's generated
sources to be formatted according to [google-java-format]. However, by default
this option is disabled because it can lead to noticable build performance
issues.

In most cases, the default formatting should be very readable, but you
might prefer to enable this option in production code or continuous integration
tests to make stack traces easier to interpret (since the unformatted source may
squeeze more things on the same line).

**Note:** Formatting of a 143k line file generated by Dagger (one of the largest
at Google!) took between 2.6 and 3 seconds in a benchmarking test. As with all
optimizations, verify the effects on your machine/environment.
{: .c-callouts__note }

[google-java-format]:https://github.com/google/google-java-format

## Full binding graph validation {#full-binding-graph-validation}

By default, problems among the bindings in a module or subcomponent or component
don't get reported unless they are used as part of a whole component tree rooted
at a root `@Component` or `@ProductionComponent`. However, if you pass
`-Adagger.fullBindingGraphValidation=ERROR` or
`-Adagger.fullBindingGraphValidation=WARNING` to javac, then _all_ the bindings
of each module, subcomponent, and component will be checked, including those
that aren't used. Any binding graph errors, such as duplicate bindings, will be
reported at the module, subcomponent, or component. (Note that missing bindings
will not be reported for full binding graphs unless they're also found when
analyzing the binding graph that's actually used to generate the root
component.)

If full binding graph validation is turned on, [SPI](spi.md) implementations
will see a `BindingGraph` representing the bindings for each module, component,
and subcomponent as well.

## Ignore wildcards in keys of provisioned bindings {#ignore-provision-key-wildcards}

The `dagger.ignoreProvisionKeyWildcards` flag is disabled be default, but it can
be enabled by passing the `-Adagger.ignoreProvisionKeyWildcards=ENABLED`
annotation processing option during compilation.

When enabled, Dagger's annotation processor will no longer allow provisioning
two bindings that only differ by the wildcards in their types. For example,
consider the module below:

```
@Module
interface MyModule {
  @Provides Foo<Bar> provideFooBar() { ... }
  @Provides Foo<? extends Bar> provideFooExtendsBar() { ... }
}
```

When `dagger.ignoreProvisionKeyWildcards` is disabled `MyModule` will result in
the creation of two separate bindings: `Foo<Bar>` and `Foo<? extends Bar>`.

However, when `dagger.ignoreProvisionKeyWildcards` is enabled `MyModule` will
result in a `[Dagger/DuplicateBindings]` error because `Foo<Bar>` and
`Foo<? extends Bar>` are considered duplicate bindings since they only differ by
the wildcards in their type.

In general, **we recommend enabling this flag** because it can be error prone to
have multiple bindings that only differ by the wildcards in their type. This is
especially true when dealing with Kotlin sources, where wildcards must be
interpreted indirectly from a number of factors other than the explicit variance
at the use site.

<!-- References -->

[`@Component`]: https://dagger.dev/api/latest/dagger/Component.html
[`Provider`]: http://docs.oracle.com/javaee/7/api/javax/inject/Provider.html

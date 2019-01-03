---
layout: default
title: Conflicting `@Inject`
---

In general, Dagger does not allow a conflicting binding for the same key in a
subcomponent and an ancestor component. There are two exceptions:

*   A subcomponent can replace an absent [`@BindsOptionalOf`] binding with an
    explicit binding.
*   A subcomponent can add entries or elements to a multibinding.

It was never supposed to be possible for a subcomponent to have an explicit
binding for a class if its [`@Inject`] binding was used in an ancestor
component. However, that validation was mistakenly never implemented.

If an explicit binding in a subcomponent conflicts with an [`@Inject`] binding
in an ancestor component, Dagger now reports a warning. To make those into
errors, pass `-Adagger.explicitBindingConflictsWithInject=ERROR` to `javac`.

You have a few options to fix these conflicts:

1.  Decide whether you really need both the [`@Inject`] binding and the explicit
    binding. If you need only the [`@Inject`] binding, you can delete the
    explicit one. If you need only the explicit one, you can move that into a
    module installed in the ancestor component, where it will replace the
    [`@Inject`] binding.

2.  If you need both, then one or the other must be qualified. If one of the
    bindings is used from only a few places, qualify that one. To qualify the
    explicit binding, just add a qualifier. You cannot qualify an [`@Inject`]
    binding, so instead you must write an explicit qualified [`@Provides`]
    method in a module installed in the ancestor component, which calls the
    constructor explicitly. In either case, you have to add the qualifier to the
    dependency requests that you want to use the qualified binding.

[`@BindsOptionalOf`]: https://google.github.io/dagger/api/latest/dagger/BindsOptionalOf.html
[`@Inject`]: https://docs.oracle.com/javaee/6/api/javax/inject/Inject.html
[`@Provides`]: https://google.github.io/dagger/api/latest/dagger/Provides.html

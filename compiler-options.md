---
layout: default
title: Compiler Options
---

## fastInit mode

You can choose to generate your Dagger [`@Component`]s in a mode that
prioritizes fast initialization. Normally, the number of classes loaded when
initializing a component (i.e., when calling `DaggerFooComponent.create()` or
`DaggerFooComponent.builder()...build()`) scales with the number of bindings in
the component. In "fastInit" mode, it doesn’t. There are tradeoffs, however.
Normally, each [`Provider`] you inject holds a reference to providers
of all its transitive dependencies; in fastInit mode, each
[`Provider`] holds a reference to the entire component, including all
scoped instances.

You should evaluate this tradeoff for your application when choosing to build in
fastInit mode. Take care to measure your application’s startup time with and
without fastInit mode to see how much benefit it has for your users. (Please let
us know whether it’s working for you!) In general, for environments like Android
where Dagger initialization is often on the user’s critical path and where class
loading is expensive, consider using fastInit mode.

To enable fastInit mode, pass the following option to javac when building your
[`@Component`]: `-Adagger.fastInit=enabled`

<!-- References -->

[`@Component`]: https://google.github.io/dagger/api/latest/dagger/Component.html
[`Provider`]: http://docs.oracle.com/javaee/7/api/javax/inject/Provider.html

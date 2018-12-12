---
layout: default
title: Kythe integration
---

Dagger integrates with [Kythe] to add edges that represent the binding and
module graphs.

Dagger adds the following edges to the Kythe graph:

-   `/inject/satisfiedby` from a [dependency request] to the [binding][](s) that
    satisfiy the request (in at least one component)
-   `/inject/installsmodule` from each [component] to all of the [modules]
    installed by the component. This includes the transitive set of modules
    included with `@Module(includes = ...)`
-   `/inject/childcomponent` from each [component] to their direct child
    components


Because Kythe interfaces well with the [Language Server Protocol], IDE tools
could be built on top of this (though none have been yet).

<!-- References -->

[binding]: glossary/binding.md
[component]: glossary/component.md
[dependency request]: glossary/dependency_request.md
[Kythe]: https://kythe.io
[Language Server Protocol]:  https://microsoft.github.io/language-server-protocol/
[modules]: glossary/module.md
[`@Inject`]: http://docs.oracle.com/javaee/7/api/javax/inject/Inject.html
[`@Provides`]: https://google.github.io/dagger/api/latest/dagger/Provides.html


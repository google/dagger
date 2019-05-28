---
layout: default
title: Glossary: _Binding Method_
---

<!--*
# Document freshness: For more information, see go/fresh-source.
freshness: { owner: 'dpb' reviewed: '2018-10-26' }
*-->

## Status: DRAFT

## Definition:

A _binding method_ is a method that specifies a [binding]. It must be annotated
with one of the following:

*   [`@Provides`]
*   [`@Produces`]
*   [`@Binds`]
*   [`@BindsOptionalOf`]
*   [`@Multibinds`]
*   [`@BindsInstance`]

All of these methods must be in a [module], except for methods annotated with
[`@BindsInstance`], which appear in a [component builder].

<!-- I don't think we consider @Inject constructors to be binding methods.
     Do we? -->

[binding]: binding.md
[`@Binds`]: https://dagger.dev/api/latest/dagger/Binds.html
[`@BindsInstance`]: https://dagger.dev/api/latest/dagger/BindsInstance.html
[`@BindsOptionalOf`]: https://dagger.dev/api/latest/dagger/BindsOptionalOf.html
[component builder]: component_builder.md
[module]: module.md
[`@Multibinds`]: https://dagger.dev/api/latest/dagger/multibindings/Multibinds.html
[`@Produces`]: https://dagger.dev/api/latest/dagger/producers/Produces.html
[`@Provides`]: https://dagger.dev/api/latest/dagger/Provides.html

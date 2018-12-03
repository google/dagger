---
layout: default
title: Glossary: _Component_
---

<!--*
# Document freshness: For more information, see go/fresh-source.
freshness: { owner: 'dpb' reviewed: '2018-10-26' }
*-->

## Status: DRAFT

## Definition:

A _component_ is an abstract type that specifies the [entry point]s and
[modules] that define the [binding graph].

Components form a tree: There is one _root component_, and any component can
have any number of child _[subcomponent]s_. A root component is annotated with
[`@Component`] or [`@ProductionComponent`]. A subcomponent is annotated with
[`@Subcomponent`] or [`@ProductionSubcomponent`] and can be instantiated only
within a parent component instance.

Dagger generates an implementation of each component in the tree.

A component is the equivalent of the "injector" in Guice and other
[dependency injection] frameworks.

[`@Component`]: https://google.github.io/dagger/api/latest/dagger/Component.html
[`@ProductionComponent`]: https://google.github.io/dagger/api/latest/dagger/producers/ProductionComponent.html
[`@ProductionSubcomponent`]: https://google.github.io/dagger/api/latest/dagger/producers/ProductionSubcomponent.html
[`@Subcomponent`]: https://google.github.io/dagger/api/latest/dagger/Subcomponent.html
[component dependency]: component_dependency.md
[dependency injection]: dependency_injection.md
[entry point]: entry_point.md
[module]: module.md
[subcomponent]: subcomponent.md

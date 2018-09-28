---
layout: default
title: Glossary: _Dependency Request_
---

<!--*
# Document freshness: For more information, see go/fresh-source.
freshness: { owner: 'dpb' reviewed: '2018-09-11' }
*-->

## Status: DRAFT

## Definition:

A _dependency request_ is a declaration that Dagger is required to provide an
object associated with a specific [key]. There are several kinds of dependency
requests. For each, its type (and optional [qualifier] annotation) determine its
key.

*   An [entry point] is an abstract, non-`void` method with no parameters on a
    [component].
*   A parameter of an [`@Inject`]-annotated constructor or method.
*   An [`@Inject`]-annotated field.
*   A parameter of a [binding method], such as a method annotated with
    [`@Provides`] or [`@Binds`].

In each case, Dagger will use the [binding] associated with the requested [key]
to _satisfy_ the request by implementing the entry point, setting the field, or
passing a value to the parameter.

[binding method]: binding_method.md
[binding]: binding.md
[`@Binds`]: https://github.com/google/dagger/blob/master/java/dagger/Binds.java
[component]: component.md
[entry point]: entry_point.md
[`@Inject`]: https://docs.oracle.com/javaee/6/api/javax/inject/Inject.html
[key]: key.md
[`@Provides`]: https://github.com/google/dagger/blob/master/java/dagger/Provides.java

---
layout: default
title: Glossary: _Entry Point_
---

<!--*
# Document freshness: For more information, see go/fresh-source.
freshness: { owner: 'dpb' reviewed: '2018-10-26' }
*-->

## Status: DRAFT

## Definition:

An _entry point_ is an abstract method on a [component] that either returns an
object or injects dependencies into an object.

1.  An entry point method with no parameters that returns something is a
    [request][dependency request] for the [key] specified by its return type and
    optional qualifier annotation.

2.  An entry point method with one parameter is a [members injection] method,
    which sets all [`@Inject`]-annotated fields and calls all
    [`@Inject`]-annotated instance methods of its argument.

Entry points are how a [component] interacts with external code.

[binding]: binding.md
[component]: component.md
[dependency request]: dependency_request.md
[`@Inject`]: https://docs.oracle.com/javaee/6/api/javax/inject/Inject.html
[key]: key.md
[members injection]: members_injection.md

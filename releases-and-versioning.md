---
layout: default
title: Releases and Versioning
---

The following outlines the strategy for managing versioned releases of Dagger 2.


## Version policy

All versions of Dagger 2 are of the form `2.API.PATCH`.

A change to `PATCH` indicates that a release is fully compatible with the
previous release.  Upgrading should be considered a drop-in replacement.

A change to `API` indicates that the release contains a change to the public
API.  (Adding a new compiler error is considered a change to the public API.)
Most `API` releases are expected to be compatible with previous versions, but
users are advised to read the release notes for a description of the changes
and a description of any associated risk.

Dagger APIs are classified as either `@Beta` or non-`@Beta` and have
separate policies for change.

### `@Beta`

Dagger uses the `@Beta` annotation to signal the same intent as that of [Guava]:

"APIs marked with the @Beta annotation at the class or method level are subject
to change. They can be modified in any way, or even removed, at any time. If
your code is a library itself (i.e. it is used on the CLASSPATH of users
outside your own control), you should not use beta APIs, unless you repackage
them (e.g. using ProGuard)."

We will try to minimize churn, but since `@Beta` features are typically newer
features under active development, we cannot guarantee their stability.

### Non-`@Beta`

Non-`@Beta` APIs are much more stable.  API changes are typicially backwards-
compatible and mostly the result of the proven stability of a previously
`@Beta` API (i.e. removing the `@Beta` annotation).

But, a non-`@Beta` API may change in incompatible ways on rare occasions. Any
breaking change will happen *at least* 6 months after warning documentation
(typically via `@Deprecated`) is added, and will always include migration
instructions.

### Version "`NEXT`"

For features that will be introduced in the upcoming release, use NEXT as the
[`API`][#version-policy] version. These will be updated as part of the release
process.

## Release schedule

As of March 2016, the Dagger team is aiming to produce a versioned release every
2 weeks.  This is not a strict schedule, but a goal to ensure that changes made
at `HEAD` are available within approximately 2 weeks.

<!-- References -->

[Guava]: https://github.com/google/guava

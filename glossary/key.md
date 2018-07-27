---
layout: default
title: Glossary: _Key_
---

## Status: DRAFT

## Definition:

A _key_ is a Java type and an optional [qualifier].

Dagger uses keys to associate [dependency requests] and [bindings].

## Examples:

The following are examples of keys:

```java
String
@Blue String

List<String>
@Red List<String>
@Green List<? extends CharSequence>
```

<!-- TODO(ronshapiro): It may be good to have this in a different style to give
a visual aid that it's an "advanced" section. -->

## Advanced

Although keys are built on Java's type system, they cannot be used
interchangeably with keys that have assignable Java types. For example, in Java,
the following code is valid, even though the type on the left-hand side is
different from the type on the right-hand side.

```java
List<String> l = new ArrayList<String>();
```

Within Dagger, the _keys_ `List<String>` and `ArrayList<String>` are completely
different and cannot be used interchangeably. Similarly, `@Red String` and
`@Blue String` are also unrelated, even though they have the same Java type,
because they have different qualifiers.

There is one exception to this rule: keys for primitives (like `int`) are
interchangeable with the keys for their boxed type (i.e. `java.lang.Integer`).

<!-- TODO(ronshapiro): Add a note for Kotlin users about declaration-site
variance and how it comes into play with keys. -->

[bindings]: binding.md
[dependency requests]: dependency_request.md
[qualifier]: qualifier.md

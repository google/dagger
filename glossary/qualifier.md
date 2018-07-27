---
layout: default
title: Glossary: _Qualifier_
---

## Status: DRAFT

## Definition:

A _qualifier_ is a Java annotation that disambiguates [keys] that have the same
Java type.

Dagger allows at most 1 qualifier per key.

## Examples:

The following are examples of keys that have the same type but are not equal
because they have different qualifiers:

```java
int // unqualified
@UserId int
@StreamDirection(INPUT) int
@StreamDirection(OUTPUT) int
@Coordinate(x = 5, y = 23) int
@Directions({@Direction(NORTH), @Direction(WEST)}) int
```

The ordering of any qualifier attributes is not significant. For example, the
following two keys are equivalent:

```java
@Usb(input = TYPE_A, output = TYPE_C) Cord
@Usb(output = TYPE_C, output = TYPE_A) Cord
```

## Custom Qualifiers

All qualifier annotations are themselves annotated with
[`@Qualifier`][qualifier-javadoc]:

```
import javax.inject.Qualifier;

@Qualifier
public @interface Usb {
  UsbType input();
  UsbType output();
}
```

For more, see the [official documentation][qualifier-javadoc].

<!-- TODO(ronshapiro): link to the tutorial -->

[keys]: key.md
[qualifier-javadoc]: https://docs.oracle.com/javaee/7/api/javax/inject/Qualifier.html

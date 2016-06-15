---
layout: default
title: Design Philosophy
---


# Design Philosophy

1.  The implementation is purely generated code. The *structure* of the binding
graph — how your provisions and dependency requests relate to each other within
a component — should be fully defined and inspectable at compile time. All
dynamism lives outside the structure of the graph.

<!-- TODO(gak): elaborate on why this is a virtue. -->

1.  The generated code should be simple and intuitive — this includes the
_implementation_.  Wherever practical the generated code should look and feel
like user-written, idiomatic Java.

1.  Users should interact with generated API as little as possible.  (See
[the research][auto-value-generate-api] done by `@AutoValue`, which heavily
influences this goal.)

1. The generated code should be portable. As simple, idomatic Java code, it
should strive to be applicable in any environment in which source is compiled.

  * The following environments are explictly supported:
      * `javac` and the JVM
      * Android
  * The following platforms have been shown to work, but not currently supported
  explicitly:
      * GWT
      * j2objc

<!-- References -->

[auto-value-generate-api]: https://docs.google.com/document/d/18lJIcrcyv6WFPo-yt6PmlaHbcEswOEEiHmJ4UarYoCE/edit

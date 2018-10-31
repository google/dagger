---
layout: default
title: Dagger Roadmap
---

Updated: 2016-08

This document outlines our planned features and team efforts.

## Adding subcomponents via modules

While the logical structure of the component/subcomponent relationship is
effective, the mechanism by which it is declared has resulted in
a lot of boilerplate and inflexibility.  Especially in applications (like an
Android app) that is likely to have _many_ subcomponents, the tedium associated
with managing these interfaces can become overwhelming if done without
additional tooling.

We plan to eliminate a lot of that pain by ensuring that users _only_ have to
manage their modules rather than modules _and_ a parallel set of subcomponent
relationships.

## Optimize the implementation

Dagger applications that take advantage of new, more efficient APIs like
`@Binds` and `@Multibinds` can be smaller and more
efficient than ever before.  But, there are still many opportunites to improve
our generated code.  We are targeting a 25% (on average) reduction in code size
for the Dagger generated code.

## Android-specific how-to guide

A long-standing feature request is a clear guide for how to apply Dagger 2 to an
Android application.  While the community has done a great job developing
patterns and providing support, we understand the need for a single,
authoritative guide and plan to (finally) add one.

## Improved validation

While Dagger's compile-time validation provides one of the best experiences for
ensuring that object graphs are correct and well-formed, there are still
opportunities to greatly improve the developer experience.

*   Validate binding subgraphs at the module level in order to report certain
    types of errors earlier that at the full component level (e.g. binding
    collisions within a module).
*   Validate the _unused_ bindings within a component so that "dead code" cannot
    bitrot and cause surprising failures later if they suddenly become used.
*   Provide many more suggestions for how to resolve issues (e.g. "did you mean
    to add this qualifier?")


[`onTrimMemory(int)`] : https://developer.android.com/reference/android/content/ComponentCallbacks2.html#onTrimMemory(int)

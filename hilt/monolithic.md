---
layout: default
title: Monolithic components
---

## Overview

Hilt uses a monolithic component system. This means that a single activity
component definition is used to inject all activity classes. Same for Fragments
and other Android types. Each activity has a separate instance of the component
though, just the class definition is shared. This is as opposed to a polylithic
component system where each activity has a separate component definition. A
polylithic system is the default mode when using dagger.android’s
`@ContributesAndroidInjector`. This page goes through some of the reasons Hilt
was designed using monolithic components along with tradeoffs between the two
models.

## Single binding key space

One of the main benefits of using a monolithic system like in Hilt is that the
binding key space is merged. If you are in a fragment injecting a `Foo` class,
it is much easier to find where that `Foo` binding came from because it cannot
differ based on the activity the fragment is attached to. Polylithic components
give you more flexibility to define different bindings per activity, but this
usually ends up making things more confusing as code bases become larger and
harder to trace.

For keeping bindings private to only code that should use them, we recommend
using qualifier annotations that are protected through restricted visibility or
using an SPI plugin to enforce separation of code.

## Simplicity for configuration

The single binding key space also makes configuration a lot easier. It reduces
the number of places that a module might be installed which makes swapping out
bindings for testing easier. It also means you don’t have to worry about
propagating modules for features to all the places that use that feature. This
can be really useful for features that make use of different scopes. In a
polylithic world, a feature using a fragment scoped object and activity scoped
object might have to have the user include modules into the fragment and then
into all the activities that use that fragment. Oftentimes, this configuration
code just adds to boilerplate and breaks encapsulation.

## Less generated code

Using a monolithic system also means less generated code. When a common module
is used across many subcomponents (as may be the case with a common activity
helper class), this means the generated Dagger code has to be repeated for every
subcomponent. While it may not initially seem like a lot, it can quickly add up
across many activities and be multiplied further by many fragments or views.

## fastInit and start up latency

Some users may be worried about how this will affect startup latency. If you are
using the [fastInit](https://dagger.dev/compiler-options#fastinit-mode) compile
option, monolithic components should not have a noticeable effect on startup
latency. This is the default setting for Hilt gradle users using the
[plugin](gradle-setup.md#hilt-gradle-plugin) and should generally be the Dagger
compilation mode used on Android.

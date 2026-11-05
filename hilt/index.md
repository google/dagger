---
layout: default
title: Hilt
---

Hilt provides a standard way to incorporate Dagger
dependency injection into an Android application.

*The goals of Hilt are:*

*   To simplify Dagger-related infrastructure for Android apps.
*   To create a standard set of components and scopes to ease setup,
    readability/understanding, and code sharing between apps.
*   To provide an easy way to provision different bindings to various build
    types (e.g. testing, debug, or release).

## Hilt Design Overview

# Testing a change

Hilt works by code generating your Dagger setup code for you. This takes away
most of the boilerplate of using Dagger and really just leaves the aspects of
defining how to create objects and where to inject them. Hilt will generate the
Dagger components and the code to automatically inject your Android classes
(like activities and fragments) for you.

Hilt generates a set of standard Android Dagger components based off of your
transitive classpath. This requires marking your Dagger modules with Hilt
annotations to tell Hilt which component they should go into. Getting objects in
your Android framework classes is done by using another Hilt annotation which
will generate the Dagger injection code into a base class that you will extend.
For Gradle users, extending this class is done with a
[bytecode transformation](gradle-setup.md#hilt-gradle-plugin) under the hood.

In your tests, Hilt generates Dagger components for you as well just like in
production. Tests have other special utilities to help with adding or replacing
test bindings.

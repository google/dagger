---
layout: default
title: Dagger & Android
---

One of the primary advantages of Dagger 2 over most other dependency injection
frameworks is that its strictly generated implementation (no reflection) means
that it can be used in Android applications. However, there _are_ still some
considerations to be made when using Dagger within Android applications.

## Philosophy

While code written for Android is Java source, it is often quite different in
terms of style.  Typically, such differences exist to accomodate the unique
[performance][android-performance] considerations of a mobile platform.

But many of the patterns commonly applied to code intended for Android are
contrary to those applied to other Java code.  Even much of the advice in
[Effective Java][effective-java] is considered inappropriate for Android.

In order to achieve the goals of both idiomatic and portable code, Dagger
relies on [ProGuard] to post-process the compiled bytecode.  This allows Dagger
to emit source that looks and feels natural on both the server and Android,
while using the different toolchains to produce bytecode that executes
efficiently in both environements.  Moreover, Dagger has an explicit goal to
ensure that the Java source that it generates is consistently compatible with
ProGuard optimizations.

Of course, not all issues can be addressed in that manner, but it is the primary
mechanism by which Android-specific compatbility will be provided.

### tl;dr

Dagger assumes that users on Android will use ProGuard.

## Recommended ProGuard Settings

Watch this space for ProGuard settings that are relevant to applications using
Dagger.


<!-- References -->

[android-performance]: http://developer.android.com/training/best-performance.html

[effective-java]: https://books.google.com/books?id=ka2VUBqHiWkC
[ProGuard]: http://proguard.sourceforge.net/


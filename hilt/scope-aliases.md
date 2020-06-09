---
layout: default
title: Scope aliases
---

## Why would you need a scope alias?

Scope aliases are useful during a migration to Hilt if you have a lot of code
using a previous scope annotation that you now want to switch to one of the Hilt
provided scope annotations. Depending on the size of your codebase, this could
mean changing the scope annotation in a lot of places. By adding a scope alias,
you can make the changes incrementally.

Using a scope alias just tells Dagger and Hilt that these scope annotations
should be treated the same.

## How to use `@AliasOf`

If you mark a scope annotation with
[`@AliasOf`](https://dagger.dev/api/latest/dagger/hilt/migration/AliasOf.html),
it will tell Hilt that the annotated scope annotation should be treated the same
as the one in the value of the `@AliasOf` annotation. The annotation value must
be a scope annotation used in a `@DefineComponent` type so that Hilt knows what
to do with it.

For example, the following takes a previous `@MyActivityScoped` annotation and
makes it equivalent to the Hilt `@ActivityScoped`. Now it should be easy to
incrementally replace `@MyActivityScoped` with the Hilt version.

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>

```java
@Scope
@AliasOf(dagger.hilt.android.scopes.ActivityScoped.class)
public @interface MyActivityScoped {}
```

{: .c-codeselector__code .c-codeselector__code_java }

```kotlin
@Scope
@AliasOf(dagger.hilt.android.scopes.ActivityScoped::class)
annotation class MyActivityScoped {}
```

{: .c-codeselector__code .c-codeselector__code_kotlin }

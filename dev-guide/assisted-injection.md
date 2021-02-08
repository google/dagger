---
layout: default
title: Assisted Injection
redirect_from:
  - /assisted-injection
---

Assisted injection is a dependency injection (DI) pattern that is used to
construct an object where some parameters may be provided by the DI framework
and others must be passed in at creation time (a.k.a "assisted") by the user.

A factory is typically responsible for combining all of the parameters and
creating the object.

(Related:
[guice/AssistedInject](https://github.com/google/guice/wiki/AssistedInject)).

## Dagger assisted injection

To use Dagger's assisted injection, annotate the constructor of an object with
[`@AssistedInject`](https://github.com/google/dagger/blob/master/java/dagger/assisted/AssistedInject.java)
and annotate any assisted parameters with
[`@Assisted`](https://github.com/google/dagger/blob/master/java/dagger/assisted/Assisted.java),
as shown below:

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
class MyDataService {
  @AssistedInject
  MyDataService(DataFetcher dataFetcher, @Assisted Config config) {}
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
class MyDataService @AssistedInject constructor(
    dataFetcher: DataFetcher,
    @Assisted config: Config
) {}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

Next, define a factory that can be used to create an instance of the object.
The factory must be annotated with
[`@AssistedFactory`](https://github.com/google/dagger/blob/master/java/dagger/assisted/AssistedFactoryjava)
and must contain an abstract method that returns the `@AssistedInject` type and
takes in all `@Assisted` parameters defined in its constructor (in the same
order). This is shown below:

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
@AssistedFactory
public interface MyDataServiceFactory {
  MyDataService create(Config config);
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
@AssistedFactory
interface MyDataServiceFactory {
  fun create(config: Config): MyDataService
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

Finally, Dagger will create the implementation for the assisted factory and
provide a binding for it. The factory can be injected as a dependency as shown
below.

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
class MyApp {
  @Inject MyDataServiceFactory serviceFactory;

  MyDataService setupService(Config config) {
    MyDataService service = serviceFactory.create(config);
    // ...
    return service;
  }
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
class MyApp {
  @Inject lateinit var serviceFactory: MyDataServiceFactory;

  fun setupService(config: Config): MyDataService {
    val service = serviceFactory.create(config)
    ...
    return service
  }
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

## Comparison to @Inject

An `@AssistedInject` constructor looks very similar to an `@Inject` constructor.
However, there are some important differences.

  1. `@AssistedInject` types cannot be injected directly, only the
     `@AssistedFactory` type can be injected. This is true even if the
     constructor does not contain any assisted parameters.
  2. `@AssistedInject` types cannot be scoped.

## Disambiguating @Assisted parameters with the same type

If multiple `@Assisted` parameters have the same type, you must distinguish
them by giving them an identifier. This can be done by adding a name via the
`@Assisted("name")` annotation. These must be put on both the factory method
and the `@AssistedInject` type.

For example:

<div class="c-codeselector__button c-codeselector__button_java">Java</div>
<div class="c-codeselector__button c-codeselector__button_kotlin">Kotlin</div>
```java
class MyDataService {
  @AssistedInject
  MyDataService(
      DataFetcher dataFetcher,
      @Assisted("server") Config serverConfig,
      @Assisted("client") Config clientConfig) {}
}

@AssistedFactory
public interface MyDataServiceFactory {
  MyDataService create(
      @Assisted("server") Config serverConfig,
      @Assisted("client") Config clientConfig);
}
```
{: .c-codeselector__code .c-codeselector__code_java }
```kotlin
class MyDataService @AssistedInject constructor(
    dataFetcher: DataFetcher,
    @Assisted("server") serverConfig: Config,
    @Assisted("client") clientConfig: Config
) {}

@AssistedFactory
interface MyDataServiceFactory {
  fun create(
    @Assisted("server") serverConfig: Config,
    @Assisted("client") clientConfig: Config
  ): MyDataService
}
```
{: .c-codeselector__code .c-codeselector__code_kotlin }

**Note:** Unfortunately, using parameter names to disambiguate parameters
is not possible as there are situations where the parameter names are not kept.
Please see this [issue](https://github.com/google/dagger/issues/2281) for more
information.
{: .c-callouts__note }

## What about AutoFactory and Square's AssistedInject?

For Dagger users, we recommend using Dagger's assisted injection rather than
other assisted injection libraries like
[AutoFactory](https://github.com/google/auto/tree/master/factory) or
[square/AssistedInject](https://github.com/square/AssistedInject). The existence
of these libraries predate Dagger's assisted injection. While they can be used
with Dagger, they require a bit of extra boilerplate to integrate with Dagger.

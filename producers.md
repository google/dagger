---
layout: default
title: Producers
---

Dagger Producers is an extension to Dagger that implements
asynchronous dependency injection in Java.


## Overview

This document assumes familiarity with the [Dagger 2 API][Dagger 2] and with
Guava’s [ListenableFuture][ListenableFuture].

Dagger Producers introduces new annotations [@ProducerModule][ProducerModule],
[@Produces][Produces], and [@ProductionComponent][ProductionComponent] as
analogues of [@Module][Module], [@Provides][Provides], and
[@Component][Component]. We refer to classes annotated `@ProducerModule` as
**producer modules**, methods annotated `@Produces` as **producer methods**, and
interfaces annotated `@ProductionComponent` as **producer graphs** (analogous to
**modules**, **provider methods**, and **object graphs**).

The key difference from ordinary Dagger is that producer methods may return
`ListenableFuture<T>` and subsequent producer methods may depend on the
unwrapped `T`; the framework handles scheduling the subsequent methods when the
future is available.

Here is a simple example that mimics a server’s request-handling flow:

```java
@ProducerModule(includes = UserModule.class)
final class UserResponseModule {
  @Produces
  static ListenableFuture<UserData> lookUpUserData(
      User user, UserDataStub stub) {
    return stub.lookUpData(user);
  }

  @Produces
  static Html renderHtml(UserData data, UserHtmlTemplate template) {
    return template.render(data);
  }
}

@Module
final class ExecutorModule {
  @Provides
  @Production
  static Executor executor() {
    return Executors.newCachedThreadPool();
  }
}
```

In this example, the computation we’re describing here says:

  * call the `lookUpUserData` method
  * when the future is available, call the `renderHtml` method with the result

(Note that we haven’t explicitly indicated where `User`, `UserDataStub`, or
`UserHtmlTemplate` come from; we’re assuming that the Dagger module `UserModule`
provides those types.)

Each of these producer methods will be scheduled on the thread pool that we
specify via the binding to `@Production Executor`. Note that even though the
provides method that specifies the executor is unscoped, the production
component will only ever get one instance from the provider; so in this case,
only one thread pool will be created.

To build this graph, we use a `ProductionComponent`:

```java
@ProductionComponent(modules = {UserResponseModule.class, ExecutorModule.class})
interface UserResponseComponent {
  ListenableFuture<Html> html();
}

// ...

UserResponseComponent component = DaggerUserResponseComponent.create();

ListenableFuture<Html> htmlFuture = component.html();
```

Dagger generates an implementation of the interface `UserResponseComponent`,
whose method `html()` does exactly what we described above: first it calls
`lookUpUserData`, and when that future is available, it calls `renderHtml`.

Both producer methods are scheduled on the provided executor, so the execution
model is entirely user-specified.

Note that, as in the above example, producer modules can be used seamlessly with
ordinary modules, subject to the restriction that provided types cannot depend
on produced types.

## Exception handling

By default, if a producer method throws an exception, or the future that it
returns failed, then any dependent producer methods will be skipped - this
models “propagating” an exception up a call stack.

If a producer method would like to “catch” that exception, the method can
request a [Produced&lt;T>][Produced] instead of a T. For example:

```java
@Produces
static Html renderHtml(
    Produced<UserData> data,
    UserHtmlTemplate template,
    ErrorHtmlTemplate errorTemplate) {
  try {
    return template.render(data.get());
  } catch (ExecutionException e) {
    return errorTemplate.render("user data failed", e.getCause());
  }
}
```

In this example, if the production of `UserData` threw an exception (either by a
producer method throwing an exception, or by a future failing), then the
`renderHtml` method catches it and returns an error template.

If an exception propagates all the way up to the component’s entry point
without any producer method catching it, then the future returned from the
component will fail with an exception.

## Lazy execution

Producer methods can request a [`Producer<T>`][Producer], which is analogous to
a [`Provider<T>`][Provider]: it delays the computation of the associated binding
until a `get()` method is called. `Producer<T>` is non-blocking; its `get()`
method returns a `ListenableFuture`, which can then be fed to the framework. For
example:

```java
@Produces
static ListenableFuture<UserData> lookUpUserData(
    Flags flags,
    @Standard Producer<UserData> standardUserData,
    @Experimental Producer<UserData> experimentalUserData) {
  return flags.useExperimentalUserData()
      ? experimentalUserData.get()
      : standardUserData.get();
}
```

In this example, if the experimental user data is requested, then the standard
user data is never computed. Note that the `Flags` may be a request-time flag,
or even the result of an RPC, which lets users build very flexible conditional
graphs.

## Multibindings

Several bindings of the same type can be collected into a set or map, just like
in [ordinary Dagger](multibindings.md). For example:

```java
@ProducerModule
final class UserDataModule {
  @Produces @IntoSet static ListenableFuture<Data> standardData(...) { ... }
  @Produces @IntoSet static ListenableFuture<Data> extraData(...) { ... }
  @Produces @IntoSet static Data synchronousData(...) { ... }
  @Produces @ElementsIntoSet static Set<ListenableFuture<Data>> rest(...) { ... }

  @Produces static ... collect(Set<Data> data) { ... }
}
```

In this example, when all the producer methods that contribute to this set have
completed futures, the `Set<Data>` is constructed and the collect() method is
called.

### Map multibindings

Map multibindings are similar to set multibindings:

```java
@MapKey @interface DispatchPath {
  String value();
}

@ProducerModule
final class DispatchModule {
  @Produces @IntoMap @DispatchPath("/user")
  static ListenableFuture<Html> dispatchUser(...) { ... }

  @Produces @IntoMap @DispatchPath("/settings")
  static ListenableFuture<Html> dispatchSettings(...) { ... }

  @Produces
  static ListenableFuture<Html> dispatch(
      Map<String, Producer<Html>> dispatchers, Url url) {
    return dispatchers.get(url.path()).get();
  }
}
```

Note that here, `dispatch()` is requesting map values of the type
`Producer<Html>`; this ensures that only the dispatch handler that was
requested will be executed.

Also note that `DispatchPath` is a [simple map key]
(multibindings.md#simple-map-keys), but that [complex map keys]
(multibindings.md#complex-map-keys) are supported as well.

## Scoping

Producer methods and production components are implicitly scoped
`@ProductionScope`. Like ordinary scoped bindings, each method will only be
executed once within the context of a given component, and its result will be
cached. This gives complete control over the lifetime of each binding &mdash; it
is the same as the lifetime of its enclosing component instance.

`@ProductionScope` may also be applied to ordinary provisions; they will then be
scoped to the production component that they're bound in. Production components
may also *additionally* have other scopes, like ordinary components can.

To supply the executor, bind `@Production Executor` in a
`ProductionComponent` or `ProductionSubcomponent`. This binding will be
implicitly scoped `@ProductionScope`. For subcomponents, the executor may be
bound in any parent component, and its binding will be inherited in the
subcomponent (like all bindings are).

The executor binding will only be executed once per component instance, even if
it is not scoped to the component; that is, it will be implicitly scoped
`@ProductionScope`.

## Component dependencies

Like ordinary `Component`s, `ProductionComponent`s may depend on other
interfaces:

```java
interface RequestComponent {
  ListenableFuture<Request> request();
}

@ProducerModule
final class UserDataModule {
  @Produces static ListenableFuture<UserData> userData(
      Request request, ...) { ... }
}

@ProductionComponent(
    modules = UserDataModule.class,
    dependencies = RequestComponent.class)
interface UserDataComponent {
  ListenableFuture<UserData> userData();
}
```

Since the `UserDataComponent` depends on the `RequestComponent`, Dagger will
require that an instance of the `RequestComponent` be provided when building the
`UserDataComponent`; and then that instance will be used to satisfy the bindings
of the getter methods that it offers:

```java
ListenableFuture<UserData> userData = DaggerUserDataComponent.builder()
    .requestComponent(/* a particular RequestComponent */)
    .build()
    .userData();
```

## Subcomponents

Dagger Producers introduces a new annotation
[@ProductionSubcomponent][ProductionSubcomponent] as an analogue to
[@Subcomponent][Subcomponent]. Production subcomponents may be subcomponents of
either components or production components.

A subcomponent inherits all bindings from its parent component, and so it is
often a simpler way of building nested scopes; see
[subcomponents](subcomponents) for more details.

## Monitoring

[ProducerMonitor][ProducerMonitor] can be used to monitor the execution of
producer methods; its methods correspond to various places in a producer's
lifecycle.

To install a `ProducerMonitor`, contribute into a set binding of
[`ProductionComponentMonitor.Factory`][ProductionComponentMonitorFactory]. For
example:

```java
@Module
final class MyMonitorModule {
  @Provides @IntoSet
  static ProductionComponentMonitor.Factory provideMonitorFactory(
      MyProductionComponentMonitor.Factory monitorFactory) {
    return monitorFactory;
  }
}

@ProductionComponent(modules = {MyMonitorModule.class, MyProducerModule.class})
interface MyComponent {
  ListenableFuture<SomeType> someType();
}
```

When the component is created, each monitor factory contributed to the set will
be asked to create a monitor for the component. The resulting (single) instance
will be held for the lifetime of the component, and will be used to create
individual monitors for each producer method.

## Cancellation behavior

For Dagger Producers, cancellation happens for the whole component rather than
for individual tasks in the graph.

When a `ListenableFuture` returned from an entry point method on a production
component is cancelled (either because `cancel()` was called on it directly or
because some future it depends on was cancelled), Dagger will cancel all tasks
defined by that component, _even tasks that entry point doesn't depend on at
all_. When one entry point of a production component is cancelled, all of its
entry points will be cancelled.

Importantly, though, if the component is a production subcomponent, nodes in the
graph that are defined by a parent will _not_ be cancelled unless the parent
component specifically allows that by being annotated with
[`@CancellationPolicy(fromSubcomponents = PROPAGATE)`][CancellationPolicy].

This means two things:

1.  Cancelling an entry point on a subcomponent will not cause its parent
    component to be cancelled, and by extension will not cause any other
    instances of that subcomponent (or other subcomponents of the same parent)
    to be cancelled.
2.  If you really need multiple entry points for a component _and_ need to be
    able to cancel them independently of one another, you can push the entry
    points down into their own subcomponents to achieve that. For example:

```java
@ProductionComponent(modules = {FooModule.class, BarModule.class})
interface MyComponent {
  ListenableFuture<Foo> foo();
  ListenableFuture<Bar> bar();
}

// If we want to be able to cancel foo() without cancelling bar() this might
// become:

@ProductionComponent(modules = BarModule.class)
interface MyComponent {
  FooComponent.Factory fooComponentFactory();
  ListenableFuture<Bar> bar();
}

@ProductionSubcomponent(modules = FooModule.class)
interface FooComponent {
  ListenableFuture<Foo> foo();

  @ProductionSubcomponent.Factory
  interface Factory {
    FooComponent create();
  }
}

// Then instead of myComponent.foo(), write

myComponent.fooComponentFactory().create().foo();
```

You could even leave `FooModule` in `MyComponent`, but putting tasks that only
`Foo` depends on in `FooComponent` will ensure all of those tasks can be
cancelled when the `Foo` task is cancelled.

## Timing, Logging and Debugging

**As of March 2016, not implemented yet**

Since graphs are constructed at compile-time, the graph will be able to be
viewed immediately after compiling, likely via writing its structure to json
or a proto.

Statistics from producer execution (CPU usage of producer method invocations and
latency of futures) will be available to export to many endpoints.

While a graph is running, its current state will be able to be dumped.

## Optional bindings

[`@BindsOptionalOf`] works for producers in much the same way as [for providers]
(users-guide#optional-bindings). If `Foo` is produced, or if there is no binding
for `Foo`, then a `@Produces` method can depend on any of:

*   `Optional<Foo>`
*   `Optional<Producer<Foo>>`
*   `Optional<Produced<Foo>>`

<!-- References -->

[`@BindsOptionalOf`]: https://dagger.dev/api/latest/dagger/BindsOptionalOf.html
[CancellationPolicy]: https://dagger.dev/api/latest/dagger/producers/CancellationPolicy.html
[Component]: https://dagger.dev/api/latest/dagger/Component.html
[Dagger 2]: http://dagger.dev/
[ListenableFuture]: https://github.com/google/guava/wiki/ListenableFutureExplained
[Module]: https://dagger.dev/api/latest/dagger/Module.html
[Produced]: https://dagger.dev/api/latest/dagger/producers/Produced.html
[Producer]: https://dagger.dev/api/latest/dagger/producers/Producer.html
[ProducerModule]: https://dagger.dev/api/latest/dagger/producers/ProducerModule.html
[ProducerMonitor]: https://dagger.dev/api/latest/dagger/producers/monitoring/ProducerMonitor.html
[Produces]: https://dagger.dev/api/latest/dagger/producers/Produces.html
[ProductionComponent]: https://dagger.dev/api/latest/dagger/producers/ProductionComponent.html
[ProductionComponentMonitor]: https://dagger.dev/api/latest/dagger/producers/monitoring/ProductionComponentMonitor.html
[ProductionComponentMonitorFactory]: https://dagger.dev/api/latest/dagger/producers/monitoring/ProductionComponentMonitor.Factory.html
[ProductionSubcomponent]: https://dagger.dev/api/latest/dagger/producers/ProductionSubcomponent.html
[Provider]: http://docs.oracle.com/javaee/6/api/javax/inject/Provider.html
[Provides]: https://dagger.dev/api/latest/dagger/Provides.html
[Subcomponent]: https://dagger.dev/api/latest/dagger/Subcomponent.html

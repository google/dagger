# Dagger Producers Semantics

This series of documents describes the semantics of Dagger. It's not intended
for learning *how* to use Dagger, but rather for *what* Dagger is.


-   [Core Dagger](index.md)
-   [Advanced Dagger](advanced.md)
-   Dagger Producers (this document)

[TOC]

## Overview

Dagger has an extension called Dagger Producers, which allows bindings to
represent asynchronous operations that can be executed concurrently.

Dagger, up until now, doesn't make any mention of threads. It *allows* code to
be safely executed in parallel (e.g., with scoped bindings, Dagger will ensure
the binding is only executed once even if multiple threads request the same
binding), but all Dagger-generated code is synchronous.

Moreover, traditional dependency injection is all about an "object graph", that
is, a graph of object dependencies; `@Inject` constructors are the primary
focus, and Dagger only introduced the other binding kinds (`@Provides` methods,
etc.) as a more flexible way of accommodating related use cases.

Dagger Producers changes both of these assumptions: it lets you (a) run binding
method logic on an [executor][Executor] of your choice, and (b) return a future
from binding methods, so they can represent asynchronous computations.

A **future** is a type representing some work to compute a value. The JDK has a
[Future][Future] type, but Dagger Producers doesn't use that directly; instead,
Dagger supports [ListenableFuture][ListenableFuture] or
[FluentFuture][FluentFuture], both Guava types that offer better APIs than the
JDK's `Future` itself. Throughout this document, whenever we say "future" or
refer to a type `Future<T>`, we mean any of the supported future types.

This enables asynchronous computation (since you can run binding method logic in
parallel, if you supply the right executor), and it focuses on a "computation
graph", that is, a graph of computation dependencies. What the latter means is
that it focuses more on the logic in each binding method, rather than the
internal state of the objects produced. In fact, with producers, it's typically
better to only produce immutable value types.

[Executor]: https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Executor.html
[Future]: https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Future.html
[ListenableFuture]: https://google.github.io/guava/releases/23.6-jre/api/docs/com/google/common/util/concurrent/ListenableFuture.html
[FluentFuture]: https://google.github.io/guava/releases/23.6-jre/api/docs/com/google/common/util/concurrent/FluentFuture.html

Dagger Producers introduces new annotations as follows.

-   A new **module** annotation, `@ProducerModule`; that is, classes annotated
    with this are modules.
-   A new **binding** annotation, `@Produces`; that is, methods annotated with
    this are bindings.
-   Two new **component** annotations, `@ProductionComponent` and
    `@ProductionSubcomponent`; that is, methods annotated with these are
    components and subcomponents, respectively.
-   Two new **Component builder** annotations corresponding to the new
    components, `@ProductionComponent.Builder` and
    `@ProductionSubcomponent.Builder`.
-   Two new types `Producer<T>` and `Produced<T>`

We disambiguate these types as follows:

|            | Producer-related      | Non-producer         |
| ---------- |:---------------------:| --------------------:|
| Bindings   | production bindings   | provision bindings   |
| Modules    | producer modules      | provider modules     |
| Components | production components | provision components |

Note: There's no reason behind the provider/provision or producer/production
nomenclature distinctions.

Provision bindings may not depend on production bindings because that would
violate the semantics of provision bindings: they must be able to be executed
inline, and cannot wait for asynchronous production dependencies to be ready.

In particular, provision component entry points may not depend transitively on
production bindings. This means that provision components cannot ever *use*
production bindings; and so to avoid confusion, we forbid provision components
from even referencing production bindings. We do this by:

-   forbidding provider modules from having production bindings;
-   forbidding provision components from using producer modules (and hence
    containing production bindings);
-   forbidding provision components from being subcomponents of (and hence
    inheriting production bindings from) production components.

## Signatures

The **production executor** of a production component is a Java
[executor][Executor] that is bound to the key `@Production Executor`. A
production component must have a production executor bound via provision binding
(not a production binding); because bindings are inherited in subcomponents,
this binding must reside in the top-level production component, or even a
provision component that is an ancestor of that. See [scope](#scope) for
discussion of this binding's scope.

The **signature** of a `@Produces` method is defined as follows:

Suppose the declared key signature of the method is `(@R1 S1, ..., @Rn Sn) -> @Q
T`.

In addition to the normal unwrapping rules for `Provider` and `Lazy`, if `Si =
Producer<Ti>` or `Si = Produced<Ti>` for some type `Ti`, then the `i`th argument
in the signature is `@Ri Ti`; otherwise it's `@Ri Si`.

Finally, the first argument in the signature is always `@Production Executor`,
followed by the normal (transformed) arguments.

The rules for the binding key are determined by whether the produces method is a
multibinding or not:

-   If it is a unique binding, with declared binding key `T = Future<U>`, for
    some type `U`, then the binding key is `@Q U`; otherwise, it is `@Q T`.
-   If it is a set binding with `@IntoSet`, with declared binding key `T =
    Future<U>`, for some type `U`, then the binding key is `@Q Set<U>`;
    otherwise, it is `@Q Set<T>`.
-   If it is a set binding with `@ElementsIntoSet` with declared binding key
    `T = Future<Set<U>>` or `T = Set<U>`, for some type `U`, then the binding
    key is `@Q Set<U>`; otherwise, it is an error.
-   If it is a set binding with `@IntoMap`, with declared binding key `T =
    Future<U>`, for some type `U`, then the binding key is `@Q Map<K, U>`, where
    `K` is computed as before; otherwise, it is `@Q Map<K, T>`.

Here are some examples of signatures of `@Produces` methods:

```java
// signature = (@Production Executor, @Blue int, double) -> Foo
@Produces Foo foo(@Blue Producer<Integer> i, double d) { ... }

// signature = (@Production Executor, int, String) -> @Green Foo
@Produces @Green Future<Foo> futureFoo(Produced<Integer> i, Lazy<String> s) { ... }

// signature = (@Production Executor, Stub) -> Set<Bar>
@Produces @IntoSet Future<Bar> bar(Stub stub) { ... }
// signature = (@Production Executor) -> Set<Baz>
@Produces @ElementsIntoSet Set<Future<Baz>> setOfFutureBaz() { ... }
```

Component dependencies for `@ProductionComponent` are treated similarly: if a
component dependency `D` has a method that returns `@Q T`, and `T = Future<U>`
for some type `U`, then its signature is `(D) -> @Q U`; otherwise, it is `(D) ->
@Q T`.

## Scope

All production components and producer methods implicitly have
`@ProductionScope` attached; that scope can be applied to any provision binding
in a production component like any normal scope would. This scope is exempt from
the rule that subcomponents may not have the same scope as their ancestor
component.

The `@Production Executor` is always treated like it has `@ProductionScope`
applied, even if it does not.

## Resolution and Runtime

There are no changes to construction of the graph. However, there is one extra
requirement: no provision binding may depend on a production binding; that is,
there may not be a directed path from a production binding to a provision
binding.

At runtime, provision bindings are executed normally (that is, immediately when
the graph traversal requires it). Production bindings, however, are submitted to
the production executor rather than being executed inline.

To do so, all of the production binding's inputs are collected (this process
only happens when the binding's logic is ready to be executed, and hence all of
its inputs have been computed) and partially applied to the binding's method.
The resulting zero-arg function either has the signature `() -> T` or `() ->
Future<T>`, depending on whether the original binding method returned a future
or value. We unify these cases by composing the former signature with
`immediateFuture: T -> Future<T>`.

Now, this function `() -> Future<T>` is submitted to the executor. When the
function is completed *and* the resulting future is done, graph execution
proceeds with subsequent bindings that depend on this one.

### Exceptions and `Produced`

There are two ways a production binding method can throw an exception: either
the method itself can throw, or it can return a future that fails with an
exception. Dagger does not distinguish between these two ways of throwing an
exception.

If a production binding method throws an exception, it propagates to the entry
point just like exceptions in provision binding methods do, subject to
`Produced` injection (see below). Since production entry points are described by
returning futures, the caller won't see an exception when calling the entry
point; instead, the entry point's future will fail with the exception as its
cause.

Unlike with provision bindings, it is common for production binding methods to
throw exceptions, since these often model error-prone tasks like network or disk
reads.

To support this programming model, Dagger provides the type `Produced`. If a
binding method injects `@Q Produced<T>`, then Dagger will catch an exception
thrown during the production of `@Q T` and save it. The method then can call
`Produced#get()`, which either returns a successful `T` or throws an
`ExecutionException` with the original exception as its cause (just like a
failed future would).

Note: Implementations of `Produced#get()` are expected not to block, and it
leads to unspecified behavior if they do. This distinguishes it from
`Future#get()`, which has the same return/throw semantics, but may block. Dagger
ensures that the value or exception is available before constructing the
`Produced` object, and hence `Produced#get()` can return or throw immediately.

### Producer

`Producer<T>` is the producers-analogue of `Provider<T>`; it defers all
computation of the underlying binding until `Producer#get()` is called. There
are three important things to note about this:

1.  Because it defers all computation, the `Producer<T>` object itself is always
    available immediately. Hence, it doesn't block submission of the binding
    method to the executor.
2.  Because `Producer<T>` represents an asynchronous computation, its `#get()`
    method returns `Future<T>`, and hence does not block. However, calling
    `Future#get()` on the resulting future *may* block! It's usually not a good
    idea to ever block inside a producer method, and so typically client code
    would just return this resulting future and let Dagger handle further
    computation.
3.  Unlike with `Provider` (see the [cycles section](#cycles)), Dagger does not
    allow cycles to be broken by `Producer`.

### Monitoring

Recall that producers are intended to represent dependency graphs of
computations; this means that a substantial portion of a program's execution
might be in producer methods. These can be hard to profile using ordinary tools
because of the asynchronous nature of producers: producer methods don't have
their dependencies in the call stack, and time spent waiting for futures is hard
to tie to the producer methods that depend on those futures.

Dagger Producers provides a monitoring API to allow hooks into various aspects
of producer execution.

The core monitor type is `ProducerMonitor`, which monitors individual production
binding methods. This class looks like:

```java
public abstract class ProducerMonitor {
  public void requested() {}
  public void ready() {}
  public void methodStarting() {}
  public void methodFinished() {}
  public void succeeded(Object value) {}
  public void failed(Throwable t) {}
}
```

Clients may extend this class to perform actions at various places relating to
the execution of an individual method. To do so, clients should implement the
following related classes:

```java
public abstract class ProductionComponentMonitor {
  public abstract ProducerMonitor producerMonitorFor(ProducerToken token);

  public abstract static class Factory {
    public abstract ProductionComponentMonitor create(Object component);
  }
}
```

For monitoring, Dagger treats the most ancestral production component as special
(that is, a production component which is either top-level, or whose parent is a
provision component). If, in this component, the multibinding key
`Set<ProductionComponentMonitor.Factory>` is bound by provision bindings, then:

-   these multibindings are treated as scoped to that component;
-   a synthetic binding with signature

    ```java
    component, Set<ProductionComponentMonitor.Factory> -> @Production Set<ProductionComponentMonitor>
    ```

    is generated with the following behavior: each `Factory#create()` method is
    called, passing the component instance; and the resulting
    `ProductionComponentMonitor`s are collected into a set (ignoring null return
    values). This binding is treated as scoped to the component;

-   each production binding's signature is updated to have `@Production
    Set<ProductionComponentMonitor>` as its first argument.

Then, at the first point in the producer method's lifecycle (when the producer
is "requested" - see below), each
`ProductionComponentMonitor#producerMonitorFor()` method is called, and the
resulting `ProducerMonitor`s are collected into a list (ignoring null return
values; and of unspecified order). The `ProducerToken` passed to
`#producerMonitorFor` is an opaque token that will be unique (w.r.t. `#equals()`
and `#hashCode()`) per producer method, and hence could e.g. be used in a hash
map.

Finally, these `ProducerMonitor`s' methods will be called, either in order or in
reverse order, at the various points of the producer method's lifecycle:

-   `#requested`: called in order, the first time Dagger deduces that this
    producer method needs to be run in order to satisfy a dependent binding
    that's already been requested;
-   `#ready`: called in order, when all inputs to the producer method are
    available, right before scheduling the method on the executor;
-   `#methodStarting`: called in order, right before the method is executed, on
    the same thread as the method will be executed;
-   `#methodFinished`: called in reverse order, right after the method is
    executed, on the same thread as the method was executed;
-   `#succeeded`: called in reverse order, when either the method (returning a
    value) has finished executing without exception, or the method's future (if
    it returned one) has finished successfully;
-   `#failed`: called in reverse order, when either the method (returning a
    value) has failed with exception, or the method's future (if it returned
    one) has failed with exception.

See the [javadoc][ProducerMonitor] for details, including more threading
guarantees.

[ProducerMonitor]: https://google.github.io/dagger/api/latest/dagger/producers/monitoring/ProducerMonitor.html

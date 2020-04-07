---
layout: default
title: Frequently Asked Questions
---

* auto-gen TOC:
{:toc}

These are some of the questions most commonly asked of the Dagger team.

In addition to those listed below, be sure to check the highest voted [Dagger 2
questions on Stack Overflow][dagger-2-stack-overflow].


## [`@Binds`]

### Why is `@Binds` different from `@Provides`?

`@Provides`, the most common construct for configuring a binding, serves three
functions:

1.  Declare which type (possibly qualified) is being provided — this is the
    return type
2.  Declare dependencies — these are the method parameters
3.  Provide an implementation for exactly _how_ the instance is provided —
     this is the method body

While the first two functions are unique and critical to _every_ `@Provides`
method, the third can often be tedious and repetitive. So, whenever there is a
`@Provides` whose implementation is simple and common enough to be inferred by
Dagger, it makes sense to just declare that as a method without a body (an
abstract method) and have Dagger apply the behavior.

But, if we were to just say that abstract `@Provides` methods should be treated
as we do for `@Binds` methods, the specification of `@Provides` would basically
be two specifications with a bunch of conditional logic.  For example, a
`@Provides` method can have any number of parameters of any type, but a `@Binds`
method can only have a single parameter whose type is assignable to the return
type.  Separating those specifications makes it easier to reason about
correctness because the annotation determines the constraints.


### Why can't `@Binds` and instance `@Provides` methods go in the same module?

Because `@Binds` methods are _just_ a method _declaration_, they are expressed
as `abstract` methods — no implementation is ever created and nothing is ever
invoked. On the other hand, a `@Provides` method _does_ have an implementation
and _will_ be invoked.

Since `@Binds` methods are never implemented, no concrete class is ever created
that implements those methods.  However, instance `@Provides` methods _require_
a concrete class in order to construct an instance on which the method can be
invoked.

#### What do I do instead?

The easiest change is to make the provides method `static`.  In addition to
being compatible with `@Binds`, they often perform better than instance provides
methods.

If the method _must_ be an instance method (e.g. returns a value from a field),
the easiest fix is to separate your `@Provides` methods and `@Binds` methods
into two separate modules and include one from the other.  A simple example that
provides an `HttpServletRequest` and binds `ServletRequest` might look like:

```java
@Module(includes = Declarations.class)
final class HttpServletRequestModule {
  @Module
  interface Declarations {
    @Binds ServletRequest bindServletRequest(HttpServletRequest httpRequest);
  }

  private final HttpServletRequest httpRequest;

  HttpServletRequestModule(HttpServletRequest httpRequest) {
    this.httpRequest = httpRequest;
  }
}
```

## Performance & Monitoring

### How do I add tracing to my components?

Dagger does not include a tracing mechanism for `@Component` implementations —
the runtime cost of monitoring relative to the runtime cost of simple provisions
would be too great to apply broadly.

If you would still like tracing for debug builds and whatnot, it can be applied
after compilation using [AOP] bytecode transformations.

`@ProductionComponent`, on the other hand, does have a monitoring API in
[`dagger.producers.monitoring`].

## How do I see generated code in my IDE?

### Eclipse with Maven

M2E is the maven-eclipse integration, and is included in the latest
versions of eclipse.  It does not have annotation-processing enabled by
default. To do this, you must install m2e-apt from the eclipse marketplace,
and [this blog post has a great walkthrough of how to set it up][m2e-apt].


### IntelliJ + Android Studio

Whether you use Maven or Gradle with IntelliJ or Android Studio, the generated
code should be available when you sync/build your project using the same tools
as handwritten code. If you experience any weirdness, file a bug!

<!-- References -->

[`@Binds`]: https://dagger.dev/api/latest/dagger/Binds.html
[`@Provides`]: https://dagger.dev/api/latest/dagger/Provides.html
[`dagger.producers.monitoring`]: https://dagger.dev/api/latest/dagger/producers/monitoring/package-summary.html
[AOP]: https://en.wikipedia.org/wiki/Aspect-oriented_programming
[m2e-apt]: https://immutables.github.io/apt.html
[dagger-2-stack-overflow]: https://stackoverflow.com/questions/tagged/dagger-2?sort=votes&pageSize=15


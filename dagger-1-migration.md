---
layout: default
title: Migrating from Dagger 1
---

While Dagger 1 and Dagger 2 are similar in many ways, one is not a drop-in
replacement for the other.  This guide will highlight both the API and
conceptual differences between the two versions and provide recommended patterns
for migrating.


## Injected types

Dagger 2 continues to rely on [JSR 330] for declaring injection sites. All of
the [types of injection][Inject] supported by Dagger 1 (field and constructor)
continue to be supported by Dagger 2, but Dagger 2 supports method injection
as well. Dagger 2 ***does not*** support static injection.

## Framework types

Dagger 2 also continues to support both the JSR 330 and Dagger-specific
framework types. [`Provider`][Provider], [`Lazy`][Lazy] and
[`MembersInjector`][MembersInjector] all maintain the same behavior.

## Modules

Dagger 2 [modules][Module] are very similar to Dagger 1 modules, but reduce much
of the configuration complexity that had been a pain point for some.  For most
users, migration will simply be deleting properties.

### Module Properties

The primary differences between the two stem from the fact that _graph
validation_ is no longer performed on a per-module basis. In Dagger 1 terms,
Dagger 2 modules are all declared as `complete = false` and `library = true`,
but since that is most permissive for validation `complete`, `library` and
`addsTo` can just be removed when migrating.

Modules are no longer the mechanism for declaring which types can be accessed by
calling code.  So, the `injects` and `staticInjections` properties can be
removed as well.

Dagger 2 doesn't support `overrides`.  Modules that override for simple testing
fakes can create a subclass of the module to emulate that behavior.  Modules
that use overrides and rely on dependency injection should be decomposed so that
the overriden modules are instead represented as a choice between two modules.

The remaining property, `includes`, is still the primary tool for composing
modules and continues to behave as it always did.

### `@Provides` methods

Provides methods have the exact same semantics.

## Creating the graph

The mechanism by which the full, dependency-injected graph is built is the
primary difference between Dagger 1 and Dagger 2. In Dagger 1 the graph was
composed via reflection by `ObjectGraph`, but in Dagger 2 it is done by a
[`@Component`][Component]-annotated, user-defined type whose implementation is
generated at compile time. Transitioning from `ObjectGraph` to `@Component` will
likely be the majority of the work in any migration.

### Declaring the component

In order to instantiate an `ObjectGraph`, users were required to supply a list
of module classes or instances.  A typical invocation might look like the
following:

```java
ObjectGraph.create(ModuleOne.class, ModuleTwo.class, new ModuleThree("hello!"));
```

Components separate defining the graph from instantiating it. A component
definition requires the same information, but declared in the `@Component`'s
[`modules`][Component-modules] parameter instead.  An equivalent component named
`MyComponent` would look like the following:

```java
@Component(
  modules = {
    ModuleOne.class,
    ModuleTwo.class,
    ModuleThree.class,
  }
)
interface MyComponent {
  /* … */
}
```

As mentioned previously, `@Module.injects` was the mechanism for declaring which
objects could be retrieved from the graph in Dagger 1.  In Dagger 2, methods on
the component interface fulfill the same role, but in a static, type-safe
way. Any binding in the graph can be accessed via a component method.

If a caller to the `ObjectGraph` above were to ask for a `Foo.class` and a
`Bar.class` and and inject members into an instance of `Baz`, the following
component methods would serve those roles:

```java
@Component(
  modules = {
    ModuleOne.class,
    ModuleTwo.class,
    ModuleThree.class,
  }
)
interface MyComponent {
  /* Functionally equivalent to objectGraph.get(Foo.class). */
  Foo getFoo();
  /* Functionally equivalent to objectGraph.get(Bar.class). */
  Bar getBar();
  /* Functionally equivalent to objectGraph.inject(aBaz). */
  Baz injectBaz(Baz baz);
}
```

More detail about defining components can be found in the
[`Component`][Component] javadocs.

### Instantiating the component

The final task is to instantiate the component implementation. The Dagger 2
annotation processor generates implementations that have the same name as the
component interface prepended with `Dagger`. For the `MyComponent` interface,
the generated component is named `DaggerMyComponent`.

For component implementations that don't have module _instances_ to pass,
instantiating the component is as simple as invoking the `create()` method. In
this example, `ModuleThree` is passed as an instance, so the component's builder
is used instead.

```java
MyComponent myComponent = DaggerMyComponent.builder()
    // ModuleOne and ModuleTwo don't need to be set explicitly
    .moduleThree(new ModuleThree("hello!"))
    .build();
```

More detail about instantiating components can be found in the
[`Component`][Component] javadocs as well.

## Scope

Dagger 1 only supported a single scope: [`@Singleton`][Singleton].  Dagger 2
allows users to _any_ well-formed [scope annotation][Scope].  The
[`Component`][Component] docs describe the details of how to properly apply
scope to a component.

## Subgraphs

Dagger 1 provided `ObjectGraph.plus` as the mechanism for creating new graphs
from existing ones.  Dagger 2 has [two mechanisms][component-relationships] for
composing graphs - each with its own advantages - but
[subcomponents][Subcomponent] is the most direct analog.  Defining a
subcomponent is very similar to defining a component and the component is
associated with the subcomponent via a factory method that accepts the necessary
modules.  If the Dagger 1 code created a new subgraph by invoking
`objectGraph.plus(new ChildGraphModule("child!"))`, the following definition and
invocation would have the same effect.

```java
@Component(/* … */)
interface MyComponent {
  /* … */

  /* Functionally equivalent to objectGraph.plus(childGraphModule). */
  MySubcomponent plus(ChildGraphModule childGraphModule);
}
```

```java
MySubcomponent mySubcomponent = myComponent.plus(new ChildGraphModule("child!"));
```

Subcomponents are further described in the [`@Subcomponent`][Subcomponent] and
the [`@Component`][component-subcomponents] javadoc.

## Nullability

Unlike Dagger 1, Dagger 2 performs implicit null checking unless a `@Nullable`
annotation (from any package) is present.  During migration, applications may
have to annotate injection sites and provides methods with the `@Nullable` of
their choice. Any mismatch in nullability will be reported as a compile-time
error.

<!-- References -->

[Component]: https://google.github.io/dagger/api/latest/dagger/Component.html
[Component-modules]: https://google.github.io/dagger/api/latest/dagger/Component.html#modules()
[component-relationships]: https://google.github.io/dagger/api/latest/dagger/Component.html#component-relationships
[component-subcomponents]: https://google.github.io/dagger/api/latest/dagger/Component.html#subcomponents
[Inject]: http://docs.oracle.com/javaee/7/api/javax/inject/Inject.html
[JSR 330]: https://jcp.org/en/jsr/detail?id=330
[Lazy]: https://google.github.io/dagger/api/latest/dagger/Lazy.html
[MembersInjector]: https://google.github.io/dagger/api/latest/dagger/MembersInjector.html
[Module]: https://google.github.io/dagger/api/latest/dagger/Module.html
[Provider]: http://docs.oracle.com/javaee/7/api/javax/inject/Provider.html
[Provides]: https://google.github.io/dagger/api/latest/dagger/Provides.html
[Scope]: http://docs.oracle.com/javaee/7/api/javax/inject/Scope.html
[Singleton]: http://docs.oracle.com/javaee/7/api/javax/inject/Singleton.html
[Subcomponent]: https://google.github.io/dagger/api/latest/dagger/Subcomponent.html

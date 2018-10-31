# Advanced Dagger Semantics

This series of documents describes the semantics of Dagger. It's not intended
for learning *how* to use Dagger, but rather for *what* Dagger is.


-   [Core Dagger](index.md)
-   Advanced Dagger (this document)
-   [Dagger Producers](producers.md)


## Members-injection

In some environments, you may not have control over the construction of an
object (for example, Android activities), but you'd like to inject data into the
object nevertheless. Dagger provides a way to directly "inject into" fields and
methods after an object is constructed.

A field annotated with `@Inject` is an **injectible field**; these fields must
be non-final and non-private; a method annotated with `@Inject` is an
**injectible method**; these methods must non-private.

A **members-injectible class** is a class with any injectible fields or methods
in it *or* any classes in its superclass chain. Its injectible fields and
methods are all of the injectible fields and methods in it or any superclass,
with one exception: if a superclass has an injectible method, and it is
overridden in a subclass *without* an `@Inject` annotation, then it is not an
injectible method of the subclass.

For any members-injectible class, Dagger generates a synthetic binding with key
signature as follows:

For each injectible field, the **declared key** of the field is the Dagger key
consisting of an optional qualifier on the field, and the type of the field.

The **declared key signature** of a members-injectible class `T`'s synthetic
binding has *inputs* as the declared keys of its injectible fields, together
with the declared dependency keys of its injectible methods, and *output*
`MembersInjector<T>`. The order of input keys in the signature is unspecified.

The **key signature** is derived from the declared key signature as usual (by
"unwrapping" `Provider` and `Lazy`).

Note the API for `MembersInjector<T>` is:

```java
interface MembersInjector<T> {
  void injectMembers(T instance);
}
```

Therefore, the binding logic of this synthetic binding returns a function; this
function has the following behavior:

-   For each injectible field, assign the field the value of the corresponding
    input key;
-   For each injectible method, call the method with the arguments from the
    corresponding input keys.

Warning: this members-injection binding is not allowed to be injected by user
code; it is only a figment of Dagger's imagination.

### Members-injection from entry points

Members-injection introduces a new type of entry point: If a class `T` is
members-injectible, then a component may have a method with signature `T ->
void`, e.g.

```
void injectT(T t);
```

This is a **members-injection entry point** with key `MembersInjector<T>`.

Note: an entry point may, as per usual rules, just return a
`MembersInjector<T>`, in which case it also is an entry point with key
`MembersInjector<T>`, but just a normal entry point, not a members-injection
entry point.

### Resolution and Runtime

Given a members-injection entry point with key `MembersInjector<T>`, Dagger
generates an implementation that computes the `MembersInjector<T>` by usual
rules, and then calls `MembersInjector#injectMembers()`.

Here is a complete example of a members-injection entry point.

```java
final class Foo {
  @Inject Foo() {}
}

final class Bar {
  @Inject Bar() {}
}

abstract class Base {
  @Inject Foo foo;
}

final class Derived extends Base {
  @Inject void bar(Bar bar);
}

@Component
interface C {
  void injectDerived(Derived d);
}
```

The generated implementation of `C` might look like:

```
final class DaggerC implements C {
  void injectDerived(Derived d) {
    d.foo = new Foo();
    d.bar(new Bar());
  }
}
```

### Members-injection from `@Inject` constructors

If a members-injectible class `T` also has an `@Inject` constructor, then Dagger
*replaces* the binding from that `@Inject` constructor with a synthetic binding
as follows.

Suppose the declared key signature of the `@Inject` binding was

```
@Q1 S1, ..., @Qn Sn -> T.
```

Then the synthetic binding's signature is

```
MembersInjector<T>, @Q1 S1, ..., @Qn Sn -> T
```

Its binding logic is:

-   call the constructor to construct a `T`
-   call `MembersInjector#injectMembers()` on the resulting `T`

Here is a complete example of a members-injection with an`@Inject` constructor.

```java
final class Foo {
  @Inject Foo() {}
}

final class Bar {
  @Inject Bar() {}
}

final class Baz {
  @Inject Baz() {}
}

abstract class Base {
  @Inject Foo foo;
}

final class Derived extends Base {
  @Inject Derived(Baz baz) {}
  @Inject void bar(Bar bar);
}

@Component
interface C {
  Derived derived();
}
```

The generated implementation of `C` might look like:

```
final class DaggerC implements C {
  Derived derived() {
    Derived d = new Derived(new Baz());
    d.foo = new Foo();
    d.bar(new Bar());
    return d;
  }
}
```

Warning: using members-injection with an `@Inject` constructor is discouraged.
In general, members-injection makes it more difficult to test an object, since
just calling the constructor does not "fully" initialize the object. As long as
you're allowed to define the constructor of an object, it's usually better to
add the appropriate arguments to the constructor rather than relying on
members-injection.

## Subcomponents

A **subcomponent** is an interface or abstract class annotated `@Subcomponent`.

We often refer to both components and subcomponents as components, and when we
want to make a distinction, we'll refer to components (as defined previously) as
**top-level components**.

The **modules**, **entry points**, and **bindings** of a subcomponent analogous
to modules, entry points, and bindings of a component.

Note: subcomponents may not have component dependencies, because they do not
have the appropriate field in their annotations. There's no technical reason why
they couldn't; they just don't.

The **builder** of a subcomponent is an interface or abstract class annotated
`@Subcomponent.Builder`, that's nested inside a subcomponent. Subcomponent
builders must follow the same rules as component builders.

The **subcomponents** of a component are:

-   the subcomponents specified by the `Module#subcomponents` field on any
    module in its transitive closure
-   the subcomponents that are the return types of any entry points
-   the subcomponents whose builders are the return types of any entry points

For example:

```java
@Component(modules = M.class)
interface C {
  S1 s1();
  S2.Builder s2Builder();
}

@Module(subcomponents = S3.class)
final class M { ... }

@Subcomponent
interface S1 { ... }

@Subcomponent
interface S2 {
  @Subcomponent.Builder
  interface Builder { ... }
}

@Subcomponent
interface S3 { ... }
```

In the above example, the subcomponents of the top-level component `C` are `S1,
S2`, and `S3`.

Note: Subcomponents do not specify the component they are children of; the
dependency goes in the reverse direction. Because of this, subcomponents can be
children of multiple components.

A top-level component forms a **component tree**, and it's possible for
subcomponents to appear multiple times in this tree; these instances of
subcomponents are treated as distinct subcomponents, even though they're
represented by the same class. For example, top-level component `A` might have
subcomponents `B` and `C`, both of which have subcomponent `D`; we view `A` as
having two copies of `D` in its component tree.

The **component path** of a subcomponent, in the context of a top-level
component, is the path from the root to the subcomponent in that tree.

In addition to the bindings described earlier, components and subcomponents also
have a synthetic binding for each of the component's subcomponent's builders,
whose

-   signature is `component -> subcomponent builder`; and
-   binding logic constructs a new subcomponent builder from the component.

Because subcomponents have access to all bindings in their parent components,
constructing a subcomponent builder consists of creating a new object with a
pointer to the parent component object.

### Resolution and Runtime

When subcomponents are in the mix, Dagger amends the resolved graph as follows.
Dagger constructs a graph for each *top-level component*, not for each
subcomponent:

The *nodes* of the graph are:

-   bindings and entry points of the component and all of its subcomponents,
    *tagged by the component path*;
-   binding keys and dependency keys of those bindings, and all entry point
    keys, *tagged by the component path* of the binding or entry point.

Note: Because the same subcomponent class can appear multiple times in a
component tree, bindings and entry points can as well, but they will be tagged
differently, based on the component path.

The *edges* of the graph are directed edges consisting of:

-   for each tagged binding, an edge from its tagged dependency keys to the
    tagged binding; this models **dependencies of bindings**;
-   for each tagged entry point, an edge from the tagged entry point's key to
    the tagged entry point; this models **dependencies of entry points**;
-   for each tagged key `(K, P)`, where `K` is the key and `P` is the component
    path, an edge from any tagged binding `(B, Q)`, where `K` is the binding key
    for `B` and `Q` is a prefix of `P`; this models **resolution of keys**.

Note: This last condition describes the lexical scoping of Dagger subcomponents.
To resolve a key in a subcomponent, we not only look for bindings in that
subcomponent, but also in any of its ancestor components.

This graph can be thought of as a series of graphs layered on top of each other.
First, the top-level component's graph; then, the graph of its subcomponents;
then, the graph of the subcomponents of *those* subcomponents, and so on. Most
edges are within a single level of this graph, but lexical scoping adds edges
from lower levels to higher levels.

## Multibindings

Multibindings introduce a new kind of collection that allows multiple
user-defined binding methods to contribute to the same collection.

Multibinding methods are either **declarations** or **contributions**.
Multibinding declarations declare that a particular key is a multibinding:

-   An **optional binding declaration** is an abstract method in a module
    annotated `@BindsOptionalOf`. Its binding key is `@Q Optional<T>`, assuming
    its declared binding key is `@Q T`.
-   A **multibinding declaration** is an abstract method in a module annotated
    `@Multibinds`. Its binding key is its declared binding key, which must be
    one of `@Q Set<T>` or `@Q Map<K, V>`.

Contribution methods contribute elements into a multibinding collection:

-   A **set contribution binding** is a `@Provides` or `@Binds` binding method
    that is annotated either `@IntoSet` or `@ElementsIntoSet`. Suppose its
    declared binding key is `@Q T`:
    -   If a method is annotated `@IntoSet`, then its binding key is `@Q
        Set<T>`.
    -   If a method is annotated `@ElementsIntoSet`, then `T` must be `Set<U>`
        for some `U`.
-   A **map contribution binding** is a `@Provides` or `@Binds` binding method
    that is annotated `@IntoMap` and has a **map key annotation**, which is an
    annotation which itself is annotated `@MapKey`. Suppose the contribution
    binding's declared binding key is `@Q T`. Then its binding key is `@Q Map<K,
    T>`, where `K` is computed from the map key contribution as follows:

A **simple map key annotation** is an annotation with just a `value` field, and
specifies a map key of the type of its `value` field. Dagger provides several of
these, for example:

```java
@MapKey
public @interface IntKey {
  int value();
}

@MapKey
public @interface ClassKey {
  Class<?> value();
}
```

A **complex map key annotation** is an annotation with `@MapKey(unwrapValue =
false)`, and specifies a map key of the type of the map key annotation itself.
For example:

```java
@MapKey(unwrapValue = false)
@interface MyKey {
  String name();
  Class<?> someClass();
  int[] indices();
}
```

Here are some examples of multibinding methods and their binding keys:

```java
// binding key = Map<Integer, String>
@Multibinds abstract Map<Integer, String> m();

// binding key = Optional<Foo>
@BindsOptionalOf abstract Foo foo();

// binding key = @Blue Set<Foo>
@Provides @IntoSet @Blue Foo blueFoo() { ... }

// binding key = Map<String, Foo>
@Binds abstract @IntoMap @StringKey("foo") Foo sfoo(FooImpl impl);
```

If a binding does not have a multibinding annotation, then it is called a
**unique binding**.

### Resolution and Runtime

A node in the resolution graph that represents a key is a **multibinding
node** if either

- there is a multibinding declaration with that key;
- there is a multibinding contribution whose edge terminates in that key's node.

Multibindings relax the restriction that every key node has exactly one inbound
edge.

#### Optional bindings

If a node with key `@Q Optional<T>` is a multibinding node, and there is a
binding with binding key `@Q T`, then a synthetic binding is generated from `@Q
T` to `@Q Optional<T>`, with binding logic `Optional.of(t)`.

A node representing an optional multibinding `@Q Optional<T>` may have zero or
one inbound edges.

At runtime, to fulfill the request for `Optional<T>`, if the node has zero
inbound edges, its value is `Optional.empty()`, and otherwise (like normally,
using the synthetic binding above), it is an optional consisting of the single
inbound edge's value.

#### Set or map multibindings

If any of the edges to a key node are set or map multibinding methods, then
*all* inbound edges must be multibindings methods. If a map binding has several
entries with the same map key, the graph is ill-formed.

At runtime, all multibinding methods' logic is executed. For set bindings, the
results of each binding method are collected into a set, that is, throwing away
duplicates (using `#equals()` on the resulting objects). For map bindings, the
results are paired with keys and collected into a map.

Map bindings are also allowed to be requested as `Map<K, Provider<V>>` or
`Map<K, Lazy<V>>`. In this case, the logic in the map binding contribution
methods is not executed when building this object, but rather deferred until
`get()` is called on a value, similar to when `Provider` or `Lazy` are injected
normally.

#### Note about multibindings with subcomponents

Multibindings are *accumulated* across subcomponents. Since subcomponents
describe increasing lexical scopes, both multibinding contributions in a parent
component, as well as with multibinding contributions in a child component, are
available in the child component's multibinding. Therefore, for example, if you
inject a `Set<T>` multibinding in a child component, you might get a larger set
than if you inject the same set in a parent component.

## Scopes

Scopes are Dagger's way of caching the results of particular bindings; they
declare that a binding's logic may not be executed more than once within the
context of a particular component.

A **scope** is a Java annotation that's annotated `@Scope`. For example,

```java
@Scope
@interface RequestScope {}
```

defines a scope.

Bindings and components may be annotated with a scope:

The **scope of a user-defined binding** is an optional scope annotation applied
to the binding method. It is an error to apply more than one scope to a binding.

The **scopes of a component** are zero or more scope annotations applied to the
component.

There are two ways that Dagger validates scopes:

1.  The scope on a binding must also be applied to the component that includes
    that binding.

2.  Subcomponents may not have the same scope as any of their ancestor
    components.

### Resolution and Runtime

Recall that a binding of a component corresponds to a node in the resolution
graph, and it has an outgoing edge to its binding key. When a binding is scoped
in a particular component, then that binding's logic is executed at most once;
the component object will cache the resulting value (that is, the output of the
binding's logic) and use the cached value whenever the binding's key is
subsequently requested.

This cache is thread-safe; that is, Dagger guarantees that the binding logic is
executed at most once, even if two threads simultaneously request the binding's
key.

#### Interaction with `Provider` and `Lazy`

If a binding is scoped, and its key `@Q T` is requested by `@Q Provider<T>`,
then each call to `Provider#get` will return the same, scoped instance; Dagger
will always maintain the guarantee that the binding logic is executed at most
once.

If a binding is scoped, and its key `@Q T` is requested by `@Q Lazy<T>`, then
similarly, each call to `Lazy#get` will return the same, scoped instance. (In
this case, of course, there's no potential conflict in semantics, since both
`Lazy` and scoping enforce a single execution of the binding logic.)

### Reusable scope

Dagger defines a special scope called `@Reusable`. This scope may be applied to
a binding, and it may *not* be applied to any component.

When a binding is `@Reusable` scoped, Dagger will treat it as though it is
scoped in the least common ancestor of all components that use it.

For example, suppose the component hierarchy is (where `->` means "has a
subcomponent", and the following describes a tree):

<!-- TODO(beder): Clarify this example. -->

```
A -> B -> C -> D
            -> E
       -> F -> G
     H -> I
```

and suppose that a `@Reusable` binding is used in `D` and `G`. Then the binding
is treated as being scoped in `B`.

---
layout: default
title: Subcomponents
---

Subcomponents are components that inherit and extend the object graph of a
parent component. You can use them to partition your application's object graph
into subgraphs either to encapsulate different parts of your application from
each other or to use more than one scope within a component.

An object bound in a subcomponent can depend on any object that is bound in its
parent component or any ancestor component, in addition to objects that are
bound in its own modules. On the other hand, objects bound in parent components
can't depend on those bound in subcomponents; nor can objects bound in one
subcomponent depend on objects bound in sibling subcomponents.

In other words, the object graph of a subcomponent's parent component is a
subgraph of the object graph of the subcomponent itself.


## Declaring a subcomponent

Just like for top-level components, you create a subcomponent by writing an
abstract class or interface that declares [abstract methods that return the
types your application cares about][component-methods]. Instead of annotating a
subcomponent with [`@Component`], you annotate it with [`@Subcomponent`] and
install [`@Module`]s. Similar to [component builders], a
[`@Subcomponent.Builder`] specifies an interface to supply necessary modules to
construct the subcomponent.

```java
@Subcomponent(modules = RequestModule.class)
interface RequestComponent {
  RequestHandler requestHandler();

  @Subcomponent.Builder
  interface Builder {
    Builder requestModule(RequestModule module);
    RequestComponent build();
  }
}
```

## Adding a subcomponent to a parent component

To add a subcomponent to a parent component, add the subcomponent class to the
[`subcomponents`][subcomponents-attribute] attribute of a `@Module` that the
parent component installs. Then the subcomponent's builder can be requested from
within the parent.

```java
@Module(subcomponents = RequestComponent.class)
class ServerModule {}

@Singleton
@Component(modules = ServerModule.class)
interface ServerComponent {
  RequestRouter requestRouter();
}

@Singleton
class RequestRouter {
  @Inject RequestRouter(
      Provider<RequestComponent.Builder> requestComponentProvider) {}

  void dataReceived(Data data) {
    RequestComponent requestComponent =
        requestComponentProvider.get()
            .data(data)
            .build();
    requestComponent.requestHandler()
        .writeResponse(200, "hello, world");
  }
}
```

<!-- TODO(dpb):
     Describe the "subcomponent framework" pattern in detail elsewhere.
-->

## Subcomponents and scope

<!-- TODO(dpb,gak): Describe scopes independently from subcomponents. -->

One reason to break your application's component up into subcomponents is to use
[scopes][`@Scope`]. With normal, unscoped bindings, each user of an injected
type may get a new, separate instance. But if the binding is scoped, then all
users of that binding *within the scope's lifetime* get the same instance of the
bound type.

The standard scope is [`@Singleton`]. Users of singleton-scoped bindings all
get the same instance.

In Dagger, a component can be associated with a scope by annotating it with a
[`@Scope`] annotation. In that case, the component implementation holds
references to all scoped objects so they can be reused. Modules with
[`@Provides`] methods annotated with a scope may only be installed into a
component annotated with the same scope.

(Types with [`@Inject`] constructors may also be annotated with scope
annotations. These "implicit bindings" may be used by any component annotated
with that scope *or any of its descendant components.* The scoped instance will
be bound in the correct scope.)

No subcomponent may be associated with the same scope as any ancestor component,
although two subcomponents that are not mutually reachable can be associated
with the same scope because there is no ambiguity about where to store the
scoped objects. (The two subcomponents effectively have different scope
*instances* even if they use the same scope annotation.)

For example, in the component tree below, `BadChildComponent` has the same
`@RootScope` annotation as its parent, `RootComponent`, and that is an error.
But `SiblingComponentOne` and `SiblingComponentTwo` can both use `@ChildScope`
because there is no way to confuse a binding in one with a binding of the same
type in another.

```java
@RootScope @Component
interface RootComponent {
  BadChildComponent.Builder badChildComponent(); // ERROR!
  SiblingComponentOne.Builder siblingComponentOne();
  SiblingComponentTwo.Builder siblingComponentTwo();
}

@RootScope @Subcomponent
interface BadChildComponent {...}

@ChildScope @Subcomponent
interface SiblingComponentOne {...}

@ChildScope @Subcomponent
interface SiblingComponentTwo {...}
```

Because a subcomponent is created from within its parent, its lifetime is
strictly smaller than its parent's. That means that it makes sense to consider
subcomponents' scopes as "smaller" and parent components' scopes as "larger". In
fact, you almost always want the root component to use the [`@Singleton`] scope.

In the example below, `RootComponent` is in [`@Singleton`] scope.
`@SessionScope` is nested within [`@Singleton`] scope, and `@RequestScope` is
nested within `@SessionScope`. Note that `FooRequestComponent` and
`BarRequestComponent` are both associated with `@RequestScope`, which works
because they are siblings; neither is an ancestor of the other.

```java
@Singleton @Component
interface RootComponent {
  SessionComponent.Builder sessionComponent();
}

@SessionScope @Subcomponent
interface SessionComponent {
  FooRequestComponent.Builder fooRequestComponent();
  BarRequestComponent.Builder barRequestComponent();
}

@RequestScope @Subcomponent
interface FooRequestComponent {...}

@RequestScope @Subcomponent
interface BarRequestComponent {...}
```

## Subcomponents for encapsulation

Another reason to use subcomponents is to encapsulate different parts of your
application from each other. For example, if two services in your server (or
two screens in your application) share some bindings, say those used for
authentication and authorization, but each have other bindings that really have
nothing to do with each other, it might make sense to create separate
subcomponents for each service or screen, and to put the shared bindings into
the parent component.

In the following example, the `Database` is provided within the `@Singleton`
component, but all of its implementation details are encapsulated within the
`DatabaseComponent`. Rest assured that no UI will have access to the
`DatabaseConnectionPool` to schedule their own queries without going through the
`Database` since that binding only exists in the subcomponent.

```java
@Singleton
@Component(modules = DatabaseModule.class)
interface ApplicationComponent {
  Database database();
}

@Module(subcomponents = DatabaseComponent.class)
class DatabaseModule {
  @Provides
  @Singleton 
  Database provideDatabase(
      @NumberOfCores int numberOfCores,
      DatabaseComponent.Builder databaseComponentBuilder) {
    return databaseComponentBuilder
        .databaseImplModule(new DatabaseImplModule(numberOfCores / 2))
        .build()
        .database();
  }
}

@Module
class DatabaseImplModule {
  DatabaseImplModule(int concurrencyLevel) {}
  @Provides DatabaseConnectionPool provideDatabaseConnectionPool() {}
  @Provides DatabaseSchema provideDatabaseSchema() {}
}

@Subcomponent(modules = DatabaseImplModule.class)
interface DatabaseComponent {
  @PrivateToDatabase Database database();
}
```

<!-- TODO(dpb): Explore this idea further. -->

<!-- TODO(dpb): ## Subcomponents vs. dependent components -->

## Defining subcomponents with abstract factory methods

In addition to `@Module.subcomponents`, a subcomponent can be installed in a
parent by declaring an `abstract` factory method in the parent component that
returns the subcomponent. If the subcomponent requires a module that does not
have a no-arg public constructor, and that module is not installed into the
parent component, then the factory method must have a parameter of that module's
type.  The factory method may have other parameters for any other modules that
are installed on the subcomponent but not on the parent component. (The
subcomponent will automatically share the instance of any module shared between
it and its parent.) Alternatively, the `abstract` method on the parent component
may return the `@Subcomponent.Builder` and no modules need to be listed as
parameters.

Using `@Module.subcomponents` is better since it allows Dagger to detect if the
subcomponent is ever requested. Installing a subcomponent via a method on the
parent component is an explicit request for that component, even if that method
is never called. Dagger can't detect that, and thus must generate the
subcomponent even if you never use it.

## Details

### Extending multibindings

Like other bindings, [multibindings](multibindings.md) in a parent component are
visible to bindings in subcomponents. But subcomponents can also add
multibindings to maps and sets bound in their parent. Any such additional
contributions are visible only to bindings within the subcomponent or its
subcomponents, and are not visible within the parent.

```java
@Component(modules = ParentModule.class)
interface Parent {
  Map<String, Integer> map();
  Set<String> set();

  Child child();
}

@Module
class ParentModule {
  @Provides @IntoMap
  @StringKey("one") static int one() {
    return 1;
  }

  @Provides @IntoMap
  @StringKey("two") static int two() {
    return 2;
  }

  @Provides @IntoSet
  static String a() {
    return "a"
  }

  @Provides @IntoSet
  static String b() {
    return "b"
  }
}

@Subcomponent(modules = ChildModule.class)
interface Child {
  Map<String, Integer> map();
  Set<String> set();
}

@Module
class ChildModule {
  @Provides @IntoMap
  @StringKey("three") static int three() {
    return 3;
  }

  @Provides @IntoMap
  @StringKey("four") static int four() {
    return 4;
  }

  @Provides @IntoSet
  static String c() {
    return "c"
  }

  @Provides @IntoSet
  static String d() {
    return "d"
  }
}

Parent parent = DaggerParent.create();
Child child = parent.child();
assertThat(parent.map().keySet()).containsExactly("one", "two");
assertThat(child.map().keySet()).containsExactly("one", "two", "three", "four");
assertThat(parent.set()).containsExactly("a", "b");
assertThat(child.set()).containsExactly("a", "b", "c", "d");
```

### Repeated modules

When the same module type is installed in a component and any of its
subcomponents, then each of those components will automatically use the same
instance of the module. This means that it is an error if you call a
subcomponent builder method for a repeated module or if a subcomponent factory
method defines a repeated module as a parameter. (The former cannot be checked
at compile time, and is thus a runtime error.)

```java
@Component(modules = {RepeatedModule.class, ...})
interface ComponentOne {
  ComponentTwo componentTwo(RepeatedModule repeatedModule); // COMPILE ERROR!
  ComponentThree.Builder componentThreeBuilder();
}

@Subcomponent(modules = {RepeatedModule.class, ...})
interface ComponentTwo { ... }

@Subcomponent(modules = {RepeatedModule.class, ...})
interface ComponentThree {
  @Subcomponent.Builder
  interface Builder {
    Builder repeatedModule(RepeatedModule repeatedModule);
    ComponentThree build();
  }
}

DaggerComponentOne.create().componentThreeBuilder()
    .repeatedModule(new RepeatedModule()) // UnsupportedOperationException!
    .build();
```

<!-- References -->

[`@Component`]: https://dagger.dev/api/latest/dagger/Component.html
[component builders]: https://dagger.dev/api/latest/dagger/Component.Builder.html
[component-methods]: https://dagger.dev/api/latest/dagger/Component.html#component-methods
[`@Inject`]: http://docs.oracle.com/javaee/7/api/javax/inject/Inject.html
[`@Module`]: https://dagger.dev/api/latest/dagger/Module.html
[`@Provides`]: https://dagger.dev/api/latest/dagger/Provides.html
[`@Scope`]: http://docs.oracle.com/javaee/7/api/javax/inject/Scope.html
[`@Singleton`]: http://docs.oracle.com/javaee/7/api/javax/inject/Singleton.html
[`@Subcomponent`]: https://dagger.dev/api/latest/dagger/Subcomponent.html
[`@Subcomponent.Builder`]: https://dagger.dev/api/latest/dagger/Subcomponent.Builder.html
[subcomponents-attribute]: https://dagger.dev/api/latest/dagger/Module.html#subcomponents()

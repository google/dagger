---
layout: default
title: User's Guide
---

The best classes in any application are the ones that do stuff: the
`BarcodeDecoder`, the `KoopaPhysicsEngine`, and the `AudioStreamer`. These
classes have dependencies; perhaps a `BarcodeCameraFinder`,
`DefaultPhysicsEngine`, and an `HttpStreamer`.

To contrast, the worst classes in any application are the ones that take up
space without doing much at all: the `BarcodeDecoderFactory`, the
`CameraServiceLoader`, and the `MutableContextWrapper`. These classes are the
clumsy duct tape that wires the interesting stuff together.

Dagger is a replacement for these `FactoryFactory` classes that implements the
[dependency injection][DI] design pattern without the burden of writing the
boilerplate. It allows you to focus on the interesting classes. Declare
dependencies, specify how to satisfy them, and ship your app.

By building on standard [`javax.inject`] annotations ([JSR 330]), each class is
**easy to test**. You don't need a bunch of boilerplate just to swap the
`RpcCreditCardService` out for a `FakeCreditCardService`.

Dependency injection isn't just for testing. It also makes it easy to create
**reusable, interchangeable modules**. You can share the same
`AuthenticationModule` across all of your apps. And you can run
`DevLoggingModule` during development and `ProdLoggingModule` in production to
get the right behavior in each situation.


## Why Dagger 2 is Different

[Dependency injection][DI] frameworks have existed for years with a whole
variety of APIs for configuring and injecting.  So, why reinvent the wheel?
Dagger 2 is the first to **implement the full stack with generated code**. The
guiding principle is to generate code that mimics the code that a user might
have hand-written to ensure that dependency injection is as simple, traceable
and performant as it can be. For more background on the design, watch
[this talk](https://youtu.be/oK_XtfXPkqw) ([slides][Dagger Talk Slides]) by
[+Gregory Kick].

## Using Dagger

We'll demonstrate dependency injection and Dagger by building a coffee maker.
For complete sample code that you can compile and run, see Dagger's
[coffee example][CoffeeMaker example].

### Declaring Dependencies

Dagger constructs instances of your application classes and satisfies their
dependencies. It uses the [`javax.inject.Inject`] annotation to identify
which constructors and fields it is interested in.

Use `@Inject` to annotate the constructor that Dagger should use to create
instances of a class. When a new instance is requested, Dagger will obtain the
required parameters values and invoke this constructor.

```java
class Thermosiphon implements Pump {
  private final Heater heater;

  @Inject
  Thermosiphon(Heater heater) {
    this.heater = heater;
  }

  ...
}
```

Dagger can inject fields directly. In this example it obtains a `Heater`
instance for the `heater` field and a `Pump` instance for the `pump` field.

```java
class CoffeeMaker {
  @Inject Heater heater;
  @Inject Pump pump;

  ...
}
```

If your class has `@Inject`-annotated fields but no `@Inject`-annotated
constructor, Dagger will inject those fields if requested, but will not create
new instances. Add a no-argument constructor with the `@Inject` annotation to
indicate that Dagger may create instances as well.

Dagger also supports method injection, though constructor or field injection are
typically preferred.

Classes that lack `@Inject` annotations cannot be constructed by Dagger.

### Satisfying Dependencies

By default, Dagger satisfies each dependency by constructing an instance of the
requested type as described above. When you request a `CoffeeMaker`, it'll
obtain one by calling `new CoffeeMaker()` and setting its injectable fields.

But `@Inject` doesn't work everywhere:

  * Interfaces can't be constructed.
  * Third-party classes can't be annotated.
  * Configurable objects must be configured!

For these cases where `@Inject` is insufficient or awkward, use an
[`@Provides`][Provides]-annotated method to satisfy a dependency. The method's
return type defines which dependency it satisfies.

For example, `provideHeater()` is invoked whenever a `Heater` is required:

```java
@Provides static Heater provideHeater() {
  return new ElectricHeater();
}
```

It's possible for `@Provides` methods to have dependencies of their own. This
one returns a `Thermosiphon` whenever a `Pump` is required:

```java
@Provides static Pump providePump(Thermosiphon pump) {
  return pump;
}
```

All `@Provides` methods must belong to a module. These are just classes that
have an [`@Module`][Module] annotation.

```java
@Module
class DripCoffeeModule {
  @Provides static Heater provideHeater() {
    return new ElectricHeater();
  }

  @Provides static Pump providePump(Thermosiphon pump) {
    return pump;
  }
}
```

By convention, `@Provides` methods are named with a `provide` prefix and module
classes are named with a `Module` suffix.

### Building the Graph

The `@Inject` and `@Provides`-annotated classes form a graph of objects, linked
by their dependencies. Calling code like an application's `main` method or an
Android [`Application`][Android Application] accesses that graph via a
well-defined set of roots. In Dagger 2, that set is defined by an interface with
methods that have no arguments and return the desired type. By applying the
[`@Component`][Component] annotation to such an interface and passing the
[module][Module] types to the `modules` parameter, Dagger 2 then fully generates
an implementation of that contract.

```java
@Component(modules = DripCoffeeModule.class)
interface CoffeeShop {
  CoffeeMaker maker();
}
```

The implementation has the same name as the interface prefixed with `Dagger`.
Obtain an instance by invoking the `builder()` method on that implementation and
use the returned [builder][Builder Pattern] to set dependencies and `build()` a
new instance.

```java
CoffeeShop coffeeShop = DaggerCoffeeShop.builder()
    .dripCoffeeModule(new DripCoffeeModule())
    .build();
```

_Note_: If your `@Component` is not a top-level type, the generated component's
name will include its enclosing types' names, joined with an underscore. For
example, this code:

```java
class Foo {
  static class Bar {
    @Component
    interface BazComponent {}
  }
}
```

would generate a component named `DaggerFoo_Bar_BazComponent`.

Any module with an accessible default constructor can be elided as the builder
will construct an instance automatically if none is set.  And for any module
whose `@Provides` methods are all static, the implementation doesn't need an
instance at all.  If all dependencies can be constructed without the user
creating a dependency instance, then the generated implementation will also
have a `create()` method that can be used to get a new instance without having
to deal with the builder.

```java
CoffeeShop coffeeShop = DaggerCoffeeShop.create();
```

Now, our `CoffeeApp` can simply use the Dagger-generated implementation of
`CoffeeShop` to get a fully-injected `CoffeeMaker`.

```java
public class CoffeeApp {
  public static void main(String[] args) {
    CoffeeShop coffeeShop = DaggerCoffeeShop.create();
    coffeeShop.maker().brew();
  }
}
```

Now that the graph is constructed and the entry point is injected, we run our
coffee maker app. Fun.

```
$ java -cp ... coffee.CoffeeApp
~ ~ ~ heating ~ ~ ~
=> => pumping => =>
 [_]P coffee! [_]P
```

#### Bindings in the graph

The example above shows how to construct a component with some of the more
typical bindings, but there are a variety of mechanisms for contributing
bindings to the graph.  The following are available as dependencies and may be
used to generate a well-formed component:

*   Those declared by `@Provides` methods within a `@Module` referenced directly
    by `@Component.modules` or transitively via `@Module.includes`
*   Any type with an `@Inject` constructor that is unscoped or has a `@Scope`
    annotation that matches one of the component's scopes
*   The [component provision methods][Component#provision-methods] of the
    [component dependencies][Component#dependencies]
*   The component itself
*   Unqualified [builders][Subcomponent.Builder] for any included
    [subcomponent][Subcomponent]
*   `Provider` or `Lazy` wrappers for any of the above bindings
*   A `Provider` of a `Lazy` of any of the above bindings (e.g.,
    `Provider<Lazy<CoffeeMaker>>`)
*   A `MembersInjector` for any type

### Singletons and Scoped Bindings

Annotate an `@Provides` method or injectable class with
[`@Singleton`][Singleton]. The graph will use a single instance of the value for
all of its clients.

```java
@Provides @Singleton static Heater provideHeater() {
  return new ElectricHeater();
}
```

The `@Singleton` annotation on an injectable class also serves as
[documentation][Documented]. It reminds potential maintainers that this class
may be shared by multiple threads.

```java
@Singleton
class CoffeeMaker {
  ...
}
```

Since Dagger 2 associates scoped instances in the graph with instances of
component implementations, the components themselves need to declare which scope
they intend to represent. For example, it wouldn't make any sense to have a
`@Singleton` binding and a `@RequestScoped` binding in the same component
because those scopes have different lifecycles and thus must live in components
with different lifecycles. To declare that a component is associated with a
given scope, simply apply the scope annotation to the component interface.

```java
@Component(modules = DripCoffeeModule.class)
@Singleton
interface CoffeeShop {
  CoffeeMaker maker();
}
```

Components may have multiple scope annotations applied. This declares that they
are all aliases to the same scope, and so that component may include scoped
bindings with any of the scopes it declares.

### Reusable scope

Sometimes you want to limit the number of times an `@Inject`-constructed class
is instantiated or a `@Provides` method is called, but you don't need to
guarantee that the exact same instance is used during the lifetime of any
particular component or subcomponent. This can be useful in environments such as
Android, where allocations can be expensive.

For these bindings, you can apply [`@Reusable`] scope. `@Reusable`-scoped
bindings, unlike other scopes, are not associated with any single component;
instead, each component that actually uses the binding will cache the returned
or instantiated object.

That means that if you install a module with a `@Reusable` binding in a
component, but only a subcomponent actually uses the binding, then only that
subcomponent will cache the binding's object. If two subcomponents that do not
share an ancestor each use the binding, each of them will cache its own object.
If a component's ancestor has already cached the object, the subcomponent will
reuse it.

*There is no guarantee that the component will call the binding only once*, so
applying `@Reusable` to bindings that return mutable objects, or objects where
it's important to refer to the same instance, is dangerous. It's safe to use
`@Reusable` for immutable objects that you would leave unscoped if you didn't
care how many times they were allocated.

```java
@Reusable // It doesn't matter how many scoopers we use, but don't waste them.
class CoffeeScooper {
  @Inject CoffeeScooper() {}
}

@Module
class CashRegisterModule {
  @Provides
  @Reusable // DON'T DO THIS! You do care which register you put your cash in.
            // Use a specific scope instead.
  static CashRegister badIdeaCashRegister() {
    return new CashRegister();
  }
}

@Reusable // DON'T DO THIS! You really do want a new filter each time, so this
          // should be unscoped.
class CoffeeFilter {
  @Inject CoffeeFilter() {}
}

```

### Releasable references

_**DEPRECATED:** This feature is deprecated and scheduled for removal in July
2018._

When a binding uses a scope annotation, that means that the component object
holds a reference to the bound object until the component object itself is
garbage-collected. In memory-sensitive environments such as Android, you may
want to let scoped objects that are not currently being used be deleted during
garbage collection when the application is under memory pressure.

In that case, you can define a scope and annotate it with
[`@CanReleaseReferences`].

```java
@Documented
@Retention(RUNTIME)
@CanReleaseReferences
@Scope
public @interface MyScope {}
```

When you determine that you want to allow objects held in that scope to be
deleted during garbage collection if they're not currently being used by some
other object, you can inject a [`ReleasableReferenceManager`] object for your
scope and call `releaseStrongReferences()` on it, which will make the component
hold a [`WeakReference`] to the object instead of a strong reference:

```java
@Inject @ForReleasableReferences(MyScope.class)
ReleasableReferenceManager myScopeReferenceManager;

void lowMemory() {
  myScopeReferenceManager.releaseStrongReferences();
}
```

If you determine that the memory pressure has receded, then you can restore the
strong references for any cached objects that have not yet been deleted during
garbage collection by calling `restoreStrongReferences()`:

```java
void highMemory() {
  myScopeReferenceManager.restoreStrongReferences();
}
```

### Lazy injections

Sometimes you need an object to be instantiated lazily.  For any binding `T`,
you can create a [`Lazy<T>`][Lazy] which defers instantiation until the first
call to `Lazy<T>`'s `get()` method. If `T` is a singleton, then `Lazy<T>` will
be the same instance for all injections within the `ObjectGraph`.  Otherwise,
each injection site will get its own `Lazy<T>` instance.  Regardless, subsequent
calls to any given instance of `Lazy<T>` will return the same underlying
instance of `T`.

```java
class GrindingCoffeeMaker {
  @Inject Lazy<Grinder> lazyGrinder;

  public void brew() {
    while (needsGrinding()) {
      // Grinder created once on first call to .get() and cached.
      lazyGrinder.get().grind();
    }
  }
}
```

### Provider injections

Sometimes you need multiple instances to be returned instead of just injecting a
single value.  While you have several options (Factories, Builders, etc.), one
option is to inject a [`Provider<T>`][Provider] instead of just `T`.  A
`Provider<T>` invokes the _binding logic_ for `T` each time `.get()` is called.
If that binding logic is an `@Inject` constructor, a new instance will be
created, but a `@Provides` method has no such guarantee.

```java
class BigCoffeeMaker {
  @Inject Provider<Filter> filterProvider;

  public void brew(int numberOfPots) {
  ...
    for (int p = 0; p < numberOfPots; p++) {
      maker.addFilter(filterProvider.get()); //new filter every time.
      maker.addCoffee(...);
      maker.percolate();
      ...
    }
  }
}
```

***Note:*** Injecting `Provider<T>` has the possibility of creating confusing
   code, and may be a design smell of mis-scoped or mis-structured objects in
   your graph.  Often you will want to use a [factory][Factory Pattern] or a
   `Lazy<T>` or re-organize the lifetimes and structure of your code to be able
   to just inject a `T`.  Injecting `Provider<T>` can, however, be a life saver
   in some cases.  A common use is when you must use a legacy architecture that
   doesn't line up with your object's natural lifetimes (e.g. servlets are
   singletons by design, but only are valid in the context of request-specfic
   data).

### Qualifiers

Sometimes the type alone is insufficient to identify a dependency. For example,
a sophisticated coffee maker app may want separate heaters for the water and the
hot plate.

In this case, we add a **qualifier annotation**. This is any annotation that
itself has a [`@Qualifier`][Qualifier] annotation. Here's the declaration of
[`@Named`][Named], a qualifier annotation included in `javax.inject`:

```java
@Qualifier
@Documented
@Retention(RUNTIME)
public @interface Named {
  String value() default "";
}
```

You can create your own qualifier annotations, or just use `@Named`. Apply
qualifiers by annotating the field or parameter of interest. The type and
qualifier annotation will both be used to identify the dependency.

```java
class ExpensiveCoffeeMaker {
  @Inject @Named("water") Heater waterHeater;
  @Inject @Named("hot plate") Heater hotPlateHeater;
  ...
}
```

Supply qualified values by annotating the corresponding `@Provides` method.

```java
@Provides @Named("hot plate") static Heater provideHotPlateHeater() {
  return new ElectricHeater(70);
}

@Provides @Named("water") static Heater provideWaterHeater() {
  return new ElectricHeater(93);
}
```

Dependencies may not have multiple qualifier annotations.

### Optional bindings

If you want a binding to work even if some dependency is not bound in the
component, you can add a [`@BindsOptionalOf`] method to a module:

```java
@BindsOptionalOf abstract CoffeeCozy optionalCozy();
```

That means that `@Inject` constructors and members and `@Provides` methods can
depend on an `Optional<CoffeeCozy>` object. If there is a binding for
`CoffeeCozy` in the component, the `Optional` will be present; if there is no
binding for `CoffeeCozy`, the `Optional` will be absent.

Specifically, you can inject any of the following:

*   `Optional<CoffeeCozy>` (unless there is a `@Nullable` binding for
    `CoffeeCozy`; see below)
*   `Optional<Provider<CoffeeCozy>>`
*   `Optional<Lazy<CoffeeCozy>>`
*   `Optional<Provider<Lazy<CoffeeCozy>>>`

(You could also inject a `Provider` or `Lazy` or `Provider` of `Lazy` of any of
those, but that isn't very useful.)

If there is a binding for `CoffeeCozy`, and that binding is `@Nullable`, then it
is a compile-time error to inject `Optional<CoffeeCozy>`, because `Optional`
cannot contain `null`. You can always inject the other forms, because `Provider`
and `Lazy` can always return `null` from their `get()` methods.

An optional binding that is absent in one component can be present in a
subcomponent if the subcomponent includes a binding for the underlying type.

You can use either Guava's [`Optional`][guava-optional] or Java 8's
[`Optional`][jdk-optional].

### Binding Instances

Often you have data available at the time you're building the component. For
example, suppose you have an application that uses command-line args; you might
want to bind those args in your component.

Perhaps your app takes a single argument representing the user's name that you'd
like to inject as `@UserName String`. You can add a method annotated
[`@BindsInstance`][BindsInstance] to the component builder to allow that
instance to be injected in the component.

```java
@Component(modules = AppModule.class)
interface AppComponent {
  App app();

  @Component.Builder
  interface Builder {
    @BindsInstance Builder userName(@UserName String userName);
    AppComponent build();
  }
}
```

Your app then might look like

```java
public static void main(String[] args) {
  if (args.length > 1) { exit(1); }
  App app = DaggerAppComponent
      .builder()
      .userName(args[0])
      .build()
      .app();
  app.run();
}
```

In the above example, injecting `@UserName String` in the component will use the
instance provided to the `Builder` when calling this method. Before building the
component, all `@BindsInstance` methods must be called, passing a non-null value
(with the exception of `@Nullable` bindings below).

If the parameter to a `@BindsInstance` method is marked `@Nullable`, then the
binding will be considered "nullable" in the same way as a `@Provides` method is
nullable: injection sites must also mark it `@Nullable`, and `null` is an
acceptable value for the binding. Moreover, users of the `Builder` may omit
calling the method, and the component will treat the instance as `null`.

`@BindsInstance` methods should be preferred to writing a `@Module` with
constructor arguments and immediately providing those values.

### Compile-time Validation

The Dagger [annotation processor][Annotation Processor] is strict and will cause
a compiler error if any bindings are invalid or incomplete. For example, this
module is installed in a component, which is missing a binding for `Executor`:

```java
@Module
class DripCoffeeModule {
  @Provides static Heater provideHeater(Executor executor) {
    return new CpuHeater(executor);
  }
}
```

When compiling it, `javac` rejects the missing binding:

```
[ERROR] COMPILATION ERROR :
[ERROR] error: java.util.concurrent.Executor cannot be provided without an @Provides-annotated method.
```

Fix the problem by adding an `@Provides`-annotated method for `Executor` to
_any_ of the modules in the component.  While `@Inject`, `@Module` and
`@Provides` annotations are validated individually, all validation of the
relationship between bindings happens at the `@Component` level.  Dagger 1
relied strictly on `@Module`-level validation (which may or may not have
reflected runtime behavior), but Dagger 2 elides such validation (and the
accompanying configuration parameters on `@Module`) in favor of full graph
validation.

### Compile-time Code Generation

Dagger's annotation processor may also generate source files with names like
`CoffeeMaker_Factory.java` or `CoffeeMaker_MembersInjector.java`. These files
are Dagger implementation details. You shouldn't need to use them directly,
though they can be handy when step-debugging through an injection. The only
generated types you should refer to in your code are the ones Prefixed with
Dagger for your component.

## Using Dagger In Your Build

You will need to include the `dagger-2.X.jar` in your application's runtime.  In
order to activate code generation you will need to include
`dagger-compiler-2.X.jar` in your build at compile time. See
the [README][installation] for more information.


## License

```
Copyright 2012 The Dagger Authors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

<!-- References -->

[Android Application]: http://developer.android.com/reference/android/app/Application.html
[Annotation Processor]: http://docs.oracle.com/javase/6/docs/api/javax/annotation/processing/package-summary.html
[`@BindsOptionalOf`]: https://google.github.io/dagger/api/latest/dagger/BindsOptionalOf.html
[BindsInstance]: https://google.github.io/dagger/api/latest/dagger/BindsInstance.html
[Builder Pattern]: http://en.wikipedia.org/wiki/Builder_pattern
[`@CanReleaseReferences`]: https://google.github.io/dagger/api/latest/dagger/releasablereferences/CanReleaseReferences.html
[CoffeeMaker Example]: https://github.com/google/dagger/tree/master/examples/simple/src/main/java/coffee
[Component#dependencies]: https://google.github.io/dagger/api/latest/dagger/Component.html#dependencies--
[Component#provision-methods]: https://google.github.io/dagger/api/latest/dagger/Component.html#provision-methods
[Component]: https://google.github.io/dagger/api/latest/dagger/Component.html
[Dagger Talk Slides]: https://docs.google.com/presentation/d/1fby5VeGU9CN8zjw4lAb2QPPsKRxx6mSwCe9q7ECNSJQ/pub?start=false&loop=false&delayms=3000
[DI]: http://en.wikipedia.org/wiki/Dependency_injection
[Documented]: http://docs.oracle.com/javase/7/docs/api/java/lang/annotation/Documented.html
[Factory Pattern]: https://en.wikipedia.org/wiki/Factory_(object-oriented_programming)
[installation]: https://github.com/google/dagger/blob/master/README.md#installation
[+Gregory Kick]: https://google.com/+GregoryKick/
[guava-optional]: https://google.github.io/guava/releases/19.0/api/docs/com/google/common/base/Optional.html
[`javax.inject.Inject`]: http://docs.oracle.com/javaee/7/api/javax/inject/Inject.html
[`javax.inject`]: http://docs.oracle.com/javaee/7/api/javax/inject/package-summary.html
[jdk-optional]: https://docs.oracle.com/javase/8/docs/api/java/util/Optional.html
[JSR 330]: https://jcp.org/en/jsr/detail?id=330
[Lazy]: https://google.github.io/dagger/api/latest/dagger/Lazy.html
[Module]: https://google.github.io/dagger/api/latest/dagger/Module.html
[Named]: http://docs.oracle.com/javaee/7/api/javax/inject/Named.html
[Provider]: http://docs.oracle.com/javaee/7/api/javax/inject/Provider.html
[Provides]: https://google.github.io/dagger/api/latest/dagger/Provides.html
[Qualifier]: http://docs.oracle.com/javaee/7/api/javax/inject/Qualifier.html
[`ReleasableReferenceManager`]: https://google.github.io/dagger/api/latest/dagger/releasablereferences/ReleasableReferenceManager.html
[`@Reusable`]: https://google.github.io/dagger/api/latest/dagger/Reusable.html
[Scope]: http://docs.oracle.com/javaee/7/api/javax/inject/Scope.html
[Singleton]: http://docs.oracle.com/javaee/7/api/javax/inject/Singleton.html
[Subcomponent.Builder]: https://google.github.io/dagger/api/latest/dagger/Subcomponent.Builder.html
[Subcomponent]: https://google.github.io/dagger/api/latest/dagger/Subcomponent.html
[`WeakReference`]: https://docs.oracle.com/javase/7/docs/api/java/lang/ref/WeakReference.html


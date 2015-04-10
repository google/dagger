---
layout: default
title: Dagger 2
---

The best classes in any application are the ones that do stuff: the `BarcodeDecoder`, the `KoopaPhysicsEngine`, and the `AudioStreamer`. These classes have dependencies; perhaps a `BarcodeCameraFinder`, `DefaultPhysicsEngine`, and an `HttpStreamer`.

To contrast, the worst classes in any application are the ones that take up space without doing much at all: the `BarcodeDecoderFactory`, the `CameraServiceLoader`, and the `MutableContextWrapper`. These classes are the clumsy duct tape that wires the interesting stuff together.

Dagger is a replacement for these `FactoryFactory` classes that implements the [dependency injection][DI] design pattern without the burden of writing the boilerplate. It allows you to focus on the interesting classes. Declare dependencies, specify how to satisfy them, and ship your app.

By building on standard [`javax.inject`](http://docs.oracle.com/javaee/7/api/javax/inject/package-summary.html) annotations ([JSR 330](https://jcp.org/en/jsr/detail?id=330)), each class is **easy to test**. You don't need a bunch of boilerplate just to swap the `RpcCreditCardService` out for a `FakeCreditCardService`.

Dependency injection isn't just for testing. It also makes it easy to create **reusable, interchangeable modules**. You can share the same `AuthenticationModule`  across all of your apps. And you can run `DevLoggingModule` during development and `ProdLoggingModule` in production to get the right behavior in each situation.

## Why Dagger 2 is Different

[Dependency injection][DI] frameworks have existed for years with a whole variety of APIs for configuring and injecting.  So, why reinvent the wheel?  Dagger 2 is the first to **implement the full stack with generated code**. The guiding principle is to generate code that mimics the code that a user might have hand-written to ensure that dependency injection is a simple, traceable and performant as it can be. For more background on the design, watch [this talk](https://www.youtube.com/watch?v=oK_XtfXPkqw) ([slides](https://docs.google.com/presentation/d/1fby5VeGU9CN8zjw4lAb2QPPsKRxx6mSwCe9q7ECNSJQ/pub?start=false&loop=false&delayms=3000)) by [+Gregory Kick](https://google.com/+GregoryKick/).

## Using Dagger
We'll demonstrate dependency injection and Dagger by building a coffee maker. For complete sample code that you can compile and run, see Dagger's [coffee example](https://github.com/google/dagger/tree/master/examples/simple/src/main/java/coffee).

### Declaring Dependencies

Dagger constructs instances of your application classes and satisfies their dependencies. It uses the [`javax.inject.Inject`](http://docs.oracle.com/javaee/7/api/javax/inject/Inject.html) annotation to identify which constructors and fields it is interested in.

Use `@Inject` to annotate the constructor that Dagger should use to create instances of a class. When a new instance is requested, Dagger will obtain the required parameters values and invoke this constructor.

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

Dagger can inject fields directly. In this example it obtains a `Heater` instance for the `heater` field and a `Pump` instance for the `pump` field.

```java
class CoffeeMaker {
  @Inject Heater heater;
  @Inject Pump pump;

  ...
}
```

If your class has `@Inject`-annotated fields but no `@Inject`-annotated constructor, Dagger will inject those fields if requested, but will not create new instances. Add a no-argument constructor with the `@Inject` annotation to indicate that Dagger may create instances as well.

Dagger also supports method injection, though constructor or field injection are typically preferred.

Classes that lack `@Inject` annotations cannot be constructed by Dagger.

### Satisfying Dependencies

By default, Dagger satisfies each dependency by constructing an instance of the requested type as described above. When you request a `CoffeeMaker`, it'll obtain one by calling `new CoffeeMaker()` and setting its injectable fields.

But `@Inject` doesn't work everywhere:

  * Interfaces can't be constructed.
  * Third-party classes can't be annotated.
  * Configurable objects must be configured!

For these cases where `@Inject` is insufficient or awkward, use an [`@Provides`][Provides]-annotated method to satisfy a dependency. The method's return type defines which dependency it satisfies.

For example, `provideHeater()` is invoked whenever a `Heater` is required:

```java
@Provides Heater provideHeater() {
  return new ElectricHeater();
}
```

It's possible for `@Provides` methods to have dependencies of their own. This one returns a `Thermosiphon` whenever a `Pump` is required:

```java
@Provides Pump providePump(Thermosiphon pump) {
  return pump;
}
```

All `@Provides` methods must belong to a module. These are just classes that have an [`@Module`][Module] annotation.

```java
@Module
class DripCoffeeModule {
  @Provides Heater provideHeater() {
    return new ElectricHeater();
  }

  @Provides Pump providePump(Thermosiphon pump) {
    return pump;
  }
}
```

By convention, `@Provides` methods are named with a `provide` prefix and module classes are named with a `Module` suffix.

### Building the Graph

The `@Inject` and `@Provides`-annotated classes form a graph of objects, linked by their dependencies. Calling code like an application's `main` method or an Android [`Application`](http://developer.android.com/reference/android/app/Application.html) accesses that graph via a well-defined set of roots. In Dagger 2, that set is defined by an interface with methods that have no arguments and return the desired type. By applying the [`@Component`][Component] annotation to such an interface and passing the [module][Module] types to the `module` parameter, Dagger 2 then fully generates an implementation of that contract.

```java
@Component(modules = DripCoffeeModule.class)
interface CoffeeShop {
  CoffeeMaker maker();
}
```

The implementation has the same name as the interface prefixed with `Dagger`.  Obtain an instance by invoking the `builder()` method on that implementation and use the returned [builder](http://en.wikipedia.org/wiki/Builder_pattern) to set dependencies and `build()` a new instance.

```java
CoffeeShop coffeeShop = DaggerCoffeeShop.builder()
    .dripCoffeeModule(new DripCoffeeModule())
    .build();
```

Any module with an accessible default constructor can be elided as the builder will construct an instance automatically if none is set.  If all dependencies can be constructed in that manner, the generated implementation will also have a `create()` method that can be used to get a new instance without having to deal with the builder.

```java
CoffeeShop coffeeShop = DaggerCoffeeShop.create();
```

Now, our `CoffeeApp` can simply use the Dagger-generated implementation of `CoffeeShop` to get a fully-injected `CoffeeMaker`.

```java
public class CoffeeApp {
  public static void main(String[] args) {
    CoffeeShop coffeeShop = DaggerCoffeeShop.create();
    coffeeShop.maker().brew();
  }
}
```

Now that the graph is constructed and the entry point is injected, we run our coffee maker app. Fun.

```
$ java -cp ... coffee.CoffeeApp
~ ~ ~ heating ~ ~ ~
=> => pumping => =>
 [_]P coffee! [_]P
```

### Singletons and Scoped Bindings

Annotate an `@Provides` method or injectable class with [`@Singleton`][Singleton]. The graph will use a single instance of the value for all of its clients.

```java
@Provides @Singleton Heater provideHeater() {
  return new ElectricHeater();
}
```

The `@Singleton` annotation on an injectable class also serves as [documentation](http://docs.oracle.com/javase/7/docs/api/java/lang/annotation/Documented.html). It reminds potential maintainers that this class may be shared by multiple threads.

```java
@Singleton
class CoffeeMaker {
  ...
}
```

Since Dagger 2 associates scoped instances in the graph with instances of component implementations, the components themselves need to declare which scope they intend to represent. For example, it wouldn't make any sense to have a `@Singleton` binding and a `@RequestScoped` binding in the same component because those scopes have different lifecycles and thus must live in components with different lifecycles. Declaring that a component is associated with a given scope, simply apply the scope annotation to the component interface.

```java
@Component(modules = DripCoffeeModule.class)
@Singleton
interface CoffeeShop {
  CoffeeMaker maker();
}
```

### Lazy injections

Sometimes you need an object to be instantiated lazily.  For any binding `T`, you can create a [`Lazy<T>`][Lazy] which defers instantiation until the first call to `Lazy<T>`'s `get()` method. If `T` is a singleton, then `Lazy<T>` will be the same instance for all injections within the `ObjectGraph`.  Otherwise, each injection site will get its own `Lazy<T>` instance.  Regardless, subsequent calls to any given instance of `Lazy<T>` will return the same underlying instance of `T`.

```java
class GridingCoffeeMaker {
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

Sometimes you need multiple instances to be returned instead of just injecting a single value.  While you have several options (Factories, Builders, etc.), one option is to inject a [`Provider<T>`][Provider] instead of just `T`.  A `Provider<T>` invokes the _binding logic_ for `T` each time `.get()` is called.  If that binding logic is an `@Inject` constructor, a new instance will be created, but a `@Provides` method has no such guarantee.

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

***Note:*** Injecting `Provider<T>` has the possibility of creating confusing code, and may be a design smell of mis-scoped or mis-structured objects in your graph.  Often you will want to use a [factory](http://en.wikipedia.org/wiki/Factory_(object-oriented_programming)) or a `Lazy<T>` or re-organize the lifetimes and structure of your code to be able to just inject a `T`.  Injecting `Provider<T>` can, however, be a life saver in some cases.  A common use is when you must use a legacy architecture that doesn't line up with your object's natural lifetimes (e.g. servlets are singletons by design, but only are valid in the context of request-specfic data).

### Member injections

In the event that you don't have control over the instantiation of an object, you can populate the `@Inject`-annotated fields of an object after it is created.  To do so, you can define a void method on a component interface that takes a single instance as a parameter:

```java
@Component(modules = DripCoffeeModule.class)
interface CoffeeShop {
  void injectCoffeeMaker(CoffeeMaker maker);
}
```

Invoking this method on a component will inject the appropriate values into all of the `@Inject` annotated fields on the `CoffeeMaker` class.  The `CoffeeMaker` instance here would be created by another system, such as a piece of infrastructure, and then the component's `injectCoffeeMaker(this)` would be called from an appropriate life-cycle method.

This approach would often be used for an Android `Activity` or `Fragment` subclass, for example.  The injection method on the component would then be called from a life-cycle method such as `onCreate`.

The component can also define a method that returns a `MemberInjector<T>`, eg. `MemberInjector<CoffeeMaker>`.  This interface will then provide the same capabilities as a void method on the component, via the `MemberInjector<T>.injectMembers(T obj)` method.

### Qualifiers

Sometimes the type alone is insufficient to identify a dependency. For example, a sophisticated coffee maker app may want separate heaters for the water and the hot plate.

In this case, we add a **qualifier annotation**. This is any annotation that itself has a [`@Qualifier`][Qualifier] annotation. Here's the declaration of [`@Named`][Named], a qualifier annotation included in `javax.inject`:

```java
@Qualifier
@Documented
@Retention(RUNTIME)
public @interface Named {
  String value() default "";
}
```

You can create your own qualifier annotations, or just use `@Named`. Apply qualifiers by annotating the field or parameter of interest. The type and qualifier annotation will both be used to identify the dependency.

```java
class ExpensiveCoffeeMaker {
  @Inject @Named("water") Heater waterHeater;
  @Inject @Named("hot plate") Heater hotPlateHeater;
  ...
}
```

Supply qualified values by annotating the corresponding `@Provides` method.

```java
@Provides @Named("hot plate") Heater provideHotPlateHeater() {
  return new ElectricHeater(70);
}

@Provides @Named("water") Heater provideWaterHeater() {
  return new ElectricHeater(93);
}
```

Dependencies may not have multiple qualifier annotations.

### Compile-time Validation

The Dagger [annotation processor](http://docs.oracle.com/javase/6/docs/api/javax/annotation/processing/package-summary.html) is strict and will cause a compiler error if any bindings are invalid or incomplete. For example, this module is installed in a component, which is missing a binding for `Executor`:

```java
@Module
class DripCoffeeModule {
  @Provides Heater provideHeater(Executor executor) {
    return new CpuHeater(executor);
  }
}
```

When compiling it, `javac` rejects the missing binding:

```
[ERROR] COMPILATION ERROR :
[ERROR] error: java.util.concurrent.Executor cannot be provided without an @Provides-annotated method.
```

Fix the problem by adding an `@Provides`-annotated method for `Executor` to _any_ of the modules in the component.  While `@Inject`, `@Module` and `@Provides` annotations are validated individually, all validation of the relationship between bindings happens at the `@Component` level.  Dagger 1 relied strictly on `@Module`-level validation (which may or may not have reflected runtime behavior), but Dagger 2 elides such validation (and the accompanying configuration parameters on `@Module`) in favor of full graph validation.

### Compile-time Code Generation

Dagger's annotation processor may also generate source files with names like `CoffeeMaker$$Factory.java` or `CoffeeMaker$$MembersInjector.java`. These files are Dagger implementation details. You shouldn't need to use them directly, though they can be handy when step-debugging through an injection.

## Using Dagger In Your Build

You will need to include the `dagger-{{site.dagger.version}}.jar` in your application's runtime.  In order to activate code generation you will need to include `dagger-compiler-{{site.dagger.version}}.jar` in your build at compile time.

In a Maven project, one would include the runtime in the dependencies section of your `pom.xml`, and the `dagger-compiler` artifact as a dependency of the compiler plugin:

```xml
<dependencies>
  <dependency>
    <groupId>{{site.dagger.groupId}}</groupId>
    <artifactId>dagger</artifactId>
    <version>{{site.dagger.version}}</version>
  </dependency>
  <dependency>
    <groupId>{{site.dagger.groupId}}</groupId>
    <artifactId>dagger-compiler</artifactId>
    <version>{{site.dagger.version}}</version>
    <optional>true</optional>
  </dependency>
</dependencies>
```

## License

```
Copyright 2014 Google, Inc.
Copyright 2012 Square, Inc.

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

[DI]: <http://en.wikipedia.org/wiki/Dependency_injection>

[Component]: </api/latest/dagger/Component.html>
[Lazy]: </api/latest/dagger/Lazy.html>
[Module]: </api/latest/dagger/Module.html>
[Provides]: </api/latest/dagger/Provides.html>

[Named]: <http://docs.oracle.com/javaee/7/api/javax/inject/Named.html>
[Provider]: <http://docs.oracle.com/javaee/7/api/javax/inject/Provider.html>
[Qualifier]: <http://docs.oracle.com/javaee/7/api/javax/inject/Qualifier.html>
[Scope]: <http://docs.oracle.com/javaee/7/api/javax/inject/Scope.html>
[Singleton]: <http://docs.oracle.com/javaee/7/api/javax/inject/Singleton.html>

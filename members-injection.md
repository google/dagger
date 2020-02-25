# Members injection

The default way for a class to request its dependencies is
via its `@Inject`-annotated cosntructor.

That ensures that the dependencies are present when the object is fully
constructed, as is normal Java best practice. There are, however, a few other
options.

The `@Inject` annotation is also valid on fields and methods. Dagger will set
`@Inject` fields after the constructor is invoked. Similarly, it will call
`@Inject` methods, supplying the parameters as if they were dependencies
specified in the constructor.

Here's a simple example:

```java
class Foo {
  @Inject Bar bar;
  private Baz baz;

  @Inject Foo() {}

  @Inject
  void setBaz(Baz baz) {
    this.baz = baz;
  }
}
```

When Dagger creates a `Foo`, it will do something like this:

```java
Bar bar = …;
Baz baz = …;

Foo foo = new Foo();
foo.bar = bar;
foo.setBaz(baz);
```

> **CONCEPTS**
>
> *   **Field injection** is when Dagger sets an `@Inject`-annotated field on a
>     class.
> *   **Method injection*** is when Dagger calls an `@Inject`-annotated method
>     on a class.
> *   Note that `@Inject`-annotated fields and methods may not be `private` or
>     `static`, and fields may not be `final`.

## Injecting objects Dagger can't create

While there are cases where you might want to use field or method injection in
addition to an `@Inject` constructor, one reason you might _need_ to use field
or method injection is because Dagger can't instantiate the class you want to
inject, for example because the class is being instantiated by another
framework.

In this case, you obviously can't rely on Dagger injecting those fields or
methods after it creates the object, because it isn't creating it.

To solve this problem, Dagger allows you to pass it an instance of a class that
has `@Inject` members (fields and methods), and Dagger will set those fields and
call those methods to inject the dependencies the object needs.

There are two ways of doing this in Dagger.

### With component methods

One way is to create a method on a component that takes the type you want to
inject as a parameter (and returns `void` or its parameter type):

```java
@Component
interface MyComponent {
  void inject(Foo foo);
}

…
Foo foo = …;
MyComponent component = DaggerMyComponent.create();
component.inject(foo);
```

Dagger knows that this isn't a normal component entry point method because it
doesn't return anything and it takes a parameter. When you pass a `Foo` to the
`inject` method in the above example, Dagger will set any `@Inject` fields and
call any `@Inject` methods on it. And because the `inject` method takes a `Foo`,
Dagger can confirm at compile time that it will be able to set all of those
fields and call all of those methods with their dependencies.

> **CONCEPTS**
>
> *   **Members injection methods** are `void` methods on a component that take
>     a parameter of a specific type, allowing Dagger to set its
>     `@Inject`-annotated fields and call its `@Inject`-annotated methods.

### `MembersInjector`

The other way is to use the `MembersInjector<T>` type. `MembersInjector` has a
single method, `void injectMembers(T)`. This method works the same way as a
`void` inject method on a component does (described above).

A `MembersInjector` can be returned from a component entry point or injected
into a class.

Here's an example using `MembersInjector`:

```java
class MyClass {
  private final MembersInjector<Foo> fooInjector;

  @Inject
  MyClass(MembersInjector<Foo> fooInjector) {
    this.fooInjector = fooInjector;
  }

  void something() {
    Foo foo = OtherFramework.getInstance(Foo.class);
    fooInjector.injectMembers(foo);
    // do stuff with foo
  }
}
```

> **CONCEPTS**
>
> *   **`MembersInjector<T>`** is a type that Dagger can provide automatically.
>     It has a single method that injects the fields and methods of an object:
>     `injectMembers(T)`.


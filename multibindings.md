---
layout: default
title: Multibindings
---

Dagger allows you to bind several objects into a collection even when the
objects are bound in different modules using multibindings. Dagger assembles the
collection so that application code can inject it without depending directly on
the individual bindings.

You could use multibindings to implement a plugin architecture, for example,
where several modules can contribute individual plugin interface implementations
so that a central class can use the entire set of plugins. Or you could have
several modules contribute individual service providers to a map, keyed by name.


## Set multibindings

In order to contribute one element to an injectable multibound set, add an
[`@IntoSet`] annotation to your module method:

```java
@Module
class MyModuleA {
  @Provides @IntoSet
  static String provideOneString(DepA depA, DepB depB) {
    return "ABC";
  }
}
```

You can also contribute several elements at one time by adding a module method
that returns a subset and is annotated with [`@ElementsIntoSet`]:

```java
@Module
class MyModuleB {
  @Provides @ElementsIntoSet
  static Set<String> provideSomeStrings(DepA depA, DepB depB) {
    return new HashSet<String>(Arrays.asList("DEF", "GHI"));
  }
}
```

Now a binding in that component can depend on the set:

```java
class Bar {
  @Inject Bar(Set<String> strings) {
    assert strings.contains("ABC");
    assert strings.contains("DEF");
    assert strings.contains("GHI");
  }
}
```

Or the component can provide the set:

```java
@Component(modules = {MyModuleA.class, MyModuleB.class})
interface MyComponent {
  Set<String> strings();
}

@Test void testMyComponent() {
  MyComponent myComponent = DaggerMyComponent.create();
  assertThat(myComponent.strings()).containsExactly("ABC", "DEF", "GHI");
}
```

As with any other binding, in addition to depending on a multibound `Set<Foo>`,
you can also depend on `Provider<Set<Foo>>` or `Lazy<Set<Foo>>`. You cannot,
however, depend on `Set<Provider<Foo>>`.

To contribute to a qualified multibound set, annotate each `@Provides` method
with the qualifier:

```java
@Module
class MyModuleC {
  @Provides @IntoSet
  @MyQualifier
  static Foo provideOneFoo(DepA depA, DepB depB) {
    return new Foo(depA, depB);
  }
}

@Module
class MyModuleD {
  @Provides
  static FooSetUser provideFooSetUser(@MyQualifier Set<Foo> foos) { ... }
}
```

## Map multibindings

Dagger lets you use multibindings to contribute entries to an injectable map as
long as the map keys are known at compile time.

To contribute an entry to a multibound map, add a method to a module that
returns the value and is annotated with [`@IntoMap`] and with
another custom annotation that specifies the map key for that entry. To
contribute an entry to a qualified multibound map, annotate each `@IntoMap`
method with the qualifier.

Then you can inject either the map itself (`Map<K, V>`) or a map containing
value providers (`Map<K, Provider<V>>`). The latter is useful when you don't
want all of the values to be instantiated because you're going to extract one
value at a time, or because you want to get a potentially new instance of each
value each time you query the map.

### Simple map keys

For maps with keys that are strings, `Class<?>`, or boxed primitives, use one of
the standard annotations in [`dagger.multibindings`]:

```java
@Module
class MyModule {
  @Provides @IntoMap
  @StringKey("foo")
  static Long provideFooValue() {
    return 100L;
  }

  @Provides @IntoMap
  @ClassKey(Thing.class)
  static String provideThingValue() {
    return "value for Thing";
  }
}

@Component(modules = MyModule.class)
interface MyComponent {
  Map<String, Long> longsByString();
  Map<Class<?>, String> stringsByClass();
}

@Test void testMyComponent() {
  MyComponent myComponent = DaggerMyComponent.create();
  assertThat(myComponent.longsByString().get("foo")).isEqualTo(100L);
  assertThat(myComponent.stringsByClass().get(Thing.class))
      .isEqualTo("value for Thing");
}
```

For maps with keys that are enums or a more specifically parameterized class,
write an annotation type with one member whose type is the map key type, and
annotate it with [`@MapKey`]:

```java
enum MyEnum {
  ABC, DEF;
}

@MapKey
@interface MyEnumKey {
  MyEnum value();
}

@MapKey
@interface MyNumberClassKey {
  Class<? extends Number> value();
}

@Module
class MyModule {
  @Provides @IntoMap
  @MyEnumKey(MyEnum.ABC)
  static String provideABCValue() {
    return "value for ABC";
  }

  @Provides @IntoMap
  @MyNumberClassKey(BigDecimal.class)
  static String provideBigDecimalValue() {
    return "value for BigDecimal";
  }
}

@Component(modules = MyModule.class)
interface MyComponent {
  Map<MyEnum, String> myEnumStringMap();
  Map<Class<? extends Number>, String> stringsByNumberClass();
}

@Test void testMyComponent() {
  MyComponent myComponent = DaggerMyComponent.create();
  assertThat(myComponent.myEnumStringMap().get(MyEnum.ABC)).isEqualTo("value for ABC");
  assertThat(myComponent.stringsByNumberClass.get(BigDecimal.class))
      .isEqualTo("value for BigDecimal");
}
```

Your annotation's single member can be any valid annotation member type except
for arrays, and can have any name.

### Complex map keys

If your map's key is more than can be expressed by a single annotation member,
you can use the entire annotation as the map key by setting `@MapKey`'s
`unwrapValue` to `false`. In that case, the annotation can have array members as
well.

```java
@MapKey(unwrapValue = false)
@interface MyKey {
  String name();
  Class<?> implementingClass();
  int[] thresholds();
}

@Module
class MyModule {
  @Provides @IntoMap
  @MyKey(name = "abc", implementingClass = Abc.class, thresholds = {1, 5, 10})
  static String provideAbc1510Value() {
    return "foo";
  }
}

@Component(modules = MyModule.class)
interface MyComponent {
  Map<MyKey, String> myKeyStringMap();
}
```

#### Using `@AutoAnnotation` to create annotation instances

If your map uses complex keys, then you may need to create an instance of your
`@MapKey` annotation at run-time to pass to the map's `get(Object)` method. The
easiest way to do that is to use the `@AutoAnnotation` annotation to create a
static method that instantiates your annotation. See
`@AutoAnnotation`'s [documentation][`@AutoAnnotation`] for more details.

```java
class MyComponentTest {
  @Test void testMyComponent() {
    MyComponent myComponent = DaggerMyComponent.create();
    assertThat(myComponent.myKeyStringMap()
        .get(createMyKey("abc", Abc.class, new int[] {1, 5, 10}))
        .isEqualTo("foo");
  }

  @AutoAnnotation
  static MyKey createMyKey(String name, Class<?> implementingClass, int[] thresholds) {
    return new AutoAnnotation_MyComponentTest_createMyKey(name, implementingClass, thresholds);
  }
}
```

### Maps whose keys are not known at compile time

Map multibindings work only if your map's keys are known at compile time and can
be expressed in an annotation. If your map's keys don't fit in those
constraints, then you cannot create a multibound map, but you can work around
that by using set multibindings to bind a set of objects that you can then
transform into a non-multibound map.

```java
@Module
class MyModule {
  @Provides @IntoSet
  static Map.Entry<Foo, Bar> entryOne(...) {
    Foo key = ...;
    Bar value = ...;
    return new SimpleImmutableEntry(key, value);
  }

  @Provides @IntoSet
  static Map.Entry<Foo, Bar> entryTwo(...) {
    Foo key = ...;
    Bar value = ...;
    return new SimpleImmutableEntry(key, value);
  }
}

@Module
class MyMapModule {
  @Provides
  static Map<Foo, Bar> fooBarMap(Set<Map.Entry<Foo, Bar>> entries) {
    Map<Foo, Bar> fooBarMap = new LinkedHashMap<>(entries.size());
    for (Map.Entry<Foo, Bar> entry : entries) {
      fooBarMap.put(entry.getKey(), entry.getValue());
    }
    return fooBarMap;
  }
}
```

Note that this technique does not give you the automatic binding of
`Map<Foo, Provider<Bar>>` as well. If you want a map of providers, the
`Map.Entry` objects in your multibound set should include the providers. Then
your non-multibound map can have `Provider` values.

```java
@Module
class MyModule {
  @Provides @IntoSet
  static Map.Entry<Foo, Provider<Bar>> entry(
      Provider<BarSubclass> barSubclassProvider) {
    Foo key = ...;
    return new SimpleImmutableEntry(key, barSubclassProvider);
  }
}

@Module
class MyProviderMapModule {
  @Provides
  static Map<Foo, Provider<Bar>> fooBarProviderMap(
      Set<Map.Entry<Foo, Provider<Bar>>> entries) {
    return ...;
  }
}
```

## Declaring multibindings

You can declare that a multibound set or map is bound by adding an abstract
[`@Multibinds`]-annotated method to a module that returns the set or map you
want to declare.

You do not have to use [`@Multibinds`] for sets or maps that have at least one
[`@IntoSet`], [`@ElementsIntoSet`], or [`@IntoMap`] binding, but you do have to
declare them if they may be empty.

```java
@Module
abstract class MyModule {
  @Multibinds abstract Set<Foo> aSet();
  @Multibinds @MyQualifier abstract Set<Foo> aQualifiedSet();
  @Multibinds abstract Map<String, Foo> aMap();
  @Multibinds @MyQualifier abstract Map<String, Foo> aQualifiedMap();
}
```

A given set or map multibinding can be declared any number of times without
error. Dagger never implements or calls any [`@Multibinds`] methods.

<!-- TODO(dpb): Render as footnote once Github supports them. -->

### Alternative: `@ElementsIntoSet` returning an empty set

For empty sets only, as an alternative, you can add a [`@ElementsIntoSet`]
method that returns an empty set:

```java
@Module
class MyEmptySetModule {
  @Provides @ElementsIntoSet
  static Set<Foo> primeEmptyFooSet() {
    return Collections.emptySet();
  }
}
```

## Inherited subcomponent multibindings

A binding in a subcomponent can depend on a multibound set or map from its
parent, just as it can depend on any other binding from its parent. But a
subcomponent can *add* elements to multibound sets or maps that are bound in its
parent as well, by simply including the appropriate `@Provides` methods in its
modules.

When that happens, the set or map is different depending on where it is
injected. When it is injected into a binding defined on the subcomponent, then
it has the values or entries defined by the subcomponent's multibindings as well
as those defined by the parent component's multibindings. When it is injected
into a binding defined on the parent component, it has only the values or
entries defined there.

```java
@Component(modules = ParentModule.class)
interface ParentComponent {
  Set<String> strings();
  Map<String, String> stringMap();
  ChildComponent childComponent();
}

@Module
class ParentModule {
  @Provides @IntoSet
  static String string1() {
    "parent string 1";
  }

  @Provides @IntoSet
  static String string2() {
    "parent string 2";
  }

  @Provides @IntoMap
  @StringKey("a")
  static String stringA() {
    "parent string A";
  }

  @Provides @IntoMap
  @StringKey("b")
  static String stringB() {
    "parent string B";
  }
}

@Subcomponent(modules = ChildModule.class)
interface ChildComponent {
  Set<String> strings();
  Map<String, String> stringMap();
}

@Module
class ChildModule {
  @Provides @IntoSet
  static String string3() {
    "child string 3";
  }

  @Provides @IntoSet
  static String string4() {
    "child string 4";
  }

  @Provides @IntoMap
  @StringKey("c")
  static String stringC() {
    "child string C";
  }

  @Provides @IntoMap
  @StringKey("d")
  static String stringD() {
    "child string D";
  }
}

@Test void testMultibindings() {
  ParentComponent parentComponent = DaggerParentComponent.create();
  assertThat(parentComponent.strings()).containsExactly(
      "parent string 1", "parent string 2");
  assertThat(parentComponent.stringMap().keySet()).containsExactly("a", "b");

  ChildComponent childComponent = parentComponent.childComponent();
  assertThat(childComponent.strings()).containsExactly(
      "parent string 1", "parent string 2", "child string 3", "child string 4");
  assertThat(childComponent.stringMap().keySet()).containsExactly(
      "a", "b", "c", "d");
}
```

<!-- References -->

[`@AutoAnnotation`]: https://github.com/google/auto/blob/master/value/src/main/java/com/google/auto/value/AutoAnnotation.java
[`dagger.multibindings`]: https://google.github.io/dagger/api/latest/dagger/multibindings/package-summary.html
[`@ElementsIntoSet`]: https://google.github.io/dagger/api/latest/dagger/multibindings/ElementsIntoSet.html
[`@IntoMap`]: https://google.github.io/dagger/api/latest/dagger/multibindings/IntoMap.html
[`@IntoSet`]: https://google.github.io/dagger/api/latest/dagger/multibindings/IntoSet.html
[`@MapKey`]: https://google.github.io/dagger/api/latest/dagger/MapKey.html
[`@Multibinds`]: https://google.github.io/dagger/api/latest/dagger/multibindings/Multibinds.html

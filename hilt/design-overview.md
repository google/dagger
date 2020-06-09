---
layout: default
title: Design Overview
---

## Component generation and module/entry point installation

Hilt generates components by finding all of the modules and entry points in the
transitive classpath. The `@InstallIn` annotation on every module and entry
point generates a small metadata class in a defined package. The special package
is inspected when processing `@HiltAndroidApp` to find all of the aggregated
items that need to be installed in the components. The same strategy is used for
other helper classes like `@DefineComponent` and `@AliasOf`.

Since the Android `Application` is generated at the same time, the generated
`Application` has a direct reference to the root generated component which is
the `ApplicationComponent`.

Since the `HiltTestApplication` must support multiple tests, unlike in the
production application, reflection is used to find the generated components.
This is helpful because it allows the test application to be decoupled from
building with the tests which allows Hilt to provide a convenient default
instead of requiring each project to code generate a test application.
Reflection is not used in production because it provides less value and
reflection may have more costs.

Aggregating all of the modules in the classpath works well for tests because it
means tests can easily add bindings by just nesting classes in the test class
(or even better using `@BindValue` which generates the module). Similarly, the
module detection also allows classes to embed `@Module` classes as inner
classes. This can be used to ensure the class cannot be used without the
associated Dagger bindings and makes its usage less error prone (e.g. pairing a
class with an `@BindsOptionalOf` it consumes or an `@Binds` to an interface).

## `@AndroidEntryPoint` injection

`@AndroidEntryPoint` works by generating a base class that the user code extends
either directly or indirectly via a transform in the
[Gradle plugin](gradle-setup.md#hilt-gradle-plugin). This base class is
responsible for retrieving the parent component (via Hilt interfaces on the
parent), creating the component, injecting the class, and exposing the component
to children via Hilt interfaces.

For example, to inject the activity the generated code essentially does the
following (simplified for readability):

```java
@Override public void onCreate(Bundled savedInstanceState) {
  // This gets the parent component from the Application (in reality there is
  // actually the activity retained component as the parent).
  Object parentComponent =
      ((GeneratedComponentManager) getApplication()).generatedComponent();
  // This creates the activity component. This involves an unsafe cast
  // to know the parent component has the methods to build the activity component.
  Object activityComponent = ((ActivityComponentBuilderEntryPoint) parentComponent)
      .activityComponentBuilder()
      .activity(this)
      .build();
  // This injects the activity. It also involves an unsafe cast to get access
  // to the activity inject method. Like the other unsafe casts, these casts
  // break build dependencies and are safe because they are code generated and
  // guaranteed via the classpath discovery of modules/interfaces.
  (MyActivity_GeneratedInjector) activityComponent).inject(this).
}
```

The generation of all of this glue code makes breaking dependencies with unsafe
casts safe and easy. Also, the automatic discovery combined with the fact that
the interfaces are generated with the activity that uses them makes it so that
including or removing an `@AndroidEntryPoint` adds/takes all of its dependencies
with it. This allows apps built with Hilt to be modular.

Most of the time the parent component is easy to get, but in the case of views
and fragments it isn't so easy because views get the activity context. To
support views with fragment bindings, the generated base class for fragments
override `getLayoutInflater` to wrap the `Context` in a `ContextWrapper` that
holds the Dagger component for the view to get.

By standardizing all of these design decisions in Hilt, integrating libraries
with activities/fragments/views should be much easier.

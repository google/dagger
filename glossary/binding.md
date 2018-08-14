---
layout: default
title: Glossary: _Binding_
---

## Status: DRAFT

## Definition:

A _binding_ is the specification that Dagger uses to satisfy [dependency
requests] for a particular [key]. It is often the code that Dagger calls in
order to satisfy a dependency.

A binding may itself have its own dependency requests that need to be
satisfied before it can execute. The relationships between bindings and
dependency requests form the [binding graph].

## Examples:

There are many ways to create bindings. Here are a few:

1.  A constructor annotated with [`@Inject`] creates a binding for the
    constructor's type. `Inbox`'s constructor is a binding for `Inbox` in this
    example:

    ```java
    class Inbox {
      @Inject Inbox() { ... }
    }
    ```
2.  A method annotated with [`@Provides`] creates a binding for the [key] of the
    method's return type (and qualifier, if it exists).
    `provideDateTimeFormatter` is a binding for `DateTimeFormatter` in this
    example:

    ```java
    @Module
    class FormattingModule {
      @Provides
      DateTimeFormatter provideDateTimeFormatter() {
        return DateTimeFormatter.ofLocalizedDate(
            FormatStyle.valueOf("YYYY-MM-DD"));
      }
    }
    ```

    Alternatively, the `@Provides` method can declare the [`FormatStyle`] as a
    [dependency]\:

    ```java
    @Module
    class FormattingModule {
      @Provides
      DateTimeFormatter provideDateTimeFormatter(FormatStyle formatStyle) {
        return DateTimeFormatter.ofLocalizedDate(formatStyle);
      }
    }
    ```

Other ways to create bindings include:

  - [`@Binds`]
  - [`@BindsInstance`]
  - [`@BindsOptionalOf`]

<!-- TODO(ronshapiro): create and link to pages on multibindings and optional
bindings. Also add an "Advanced" section on synthetic bindings -->


[`@Binds`]: https://google.github.io/dagger/api/latest/dagger/Binds.html
[binding graph]: binding_graph.md
[`@BindsInstance`]: https://google.github.io/dagger/api/latest/dagger/BindsInstance.html
[`@BindsOptionalOf`]: https://google.github.io/dagger/api/latest/dagger/BindsOptionalOf.html
[dependency]: dependency_request.md
[dependency requests]: dependency_request.md
[`FormatStyle`]: https://docs.oracle.com/javase/8/docs/api/java/time/format/FormatStyle.html
[`@Inject`]: https://docs.oracle.com/javaee/7/api/javax/inject/Inject.html
[key]: key.md
[`@Provides`]: https://google.github.io/dagger/api/latest/dagger/Provides.html

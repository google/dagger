---
layout: default
title: Benefits of using Hilt
---

Why use Hilt?

*   Reduced boilerplate
*   Decoupled build dependencies
*   Simplified configuration
*   Improved testing
*   Standardized components

## Reduced boilerplate

The goal of Hilt is to enable users to focus on the Dagger binding definitions
and usages without needing to worry about the rest of the Dagger setup. This
means hiding things like component definitions with module and interface lists,
code to create and hold on to components at the right points in the lifecycle,
interfaces and casts to get the parent component, etc.

Some of the simplicity also comes from Hilt using monolithic components (i.e.
using a single component for all activities, a single component for all
fragments, etc). Hilt tries to encourage an essentially global binding namespace
so that it is easy to know what binding definition is being used without having
to trace back which activity or fragment you were injected from. For more
information about this design decision, read [here](monolithic.md).

## Decoupled build dependencies

A naive usage of Dagger may introduce build problems if code references the
Dagger component directly. These problems occur because the Dagger component has
references to all of the modules installed. This can lead to bloated
dependencies that slow down builds. The natural way to solve this involves
interfaces and unsafe casts. This is a tradeoff though because these can
introduce runtime errors. For example, introducing a new injector interface
avoids directly depending on the component but then forgetting to make your
component extend the injector interface results in a cast exception.

By code generating the interfaces, unsafe casts, and module/interface lists
under the hood, Hilt makes these runtime unsafe casts safe due to the guarantees
of the code generation and module/entry point discovery.

## Configuration

Apps often have different builds configurations like a production or development
build that has different features. These different sets of features often mean a
different set of Dagger modules. In a normal Dagger build, a different set of
modules requires having a separate component tree (a separate component for
every scope) with usually lots of portions repeated. Because Hilt installs
modules via build dependencies and code generates the components, creating a
different flavor of your build is as simple as compiling with an added or
removed dependency.

## Testing

Testing with Dagger can be hard, due to the configuration issue mentioned above.
Hilt similarly makes changing out test modules and bindings easier due to the
code generation of components. Hilt has specific test utilities built-in to make
managing modules and providing test bindings easier so that tests can use
Dagger. Using Dagger in tests helps reduce boilerplate in tests and makes tests
more robust by instantiating code in the same way it is instantiated in
production.

## Standardization

Hilt standardizes the [component hierarchy](components.md#component-heirarchy).
This means that libraries that integrate with Hilt can easily add or consume
bindings from these known components. This allows for more complex libraries to
be built that can integrate cleanly and more simply into any Hilt app.

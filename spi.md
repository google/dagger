---
layout: default
title: Dagger SPI
---

The Dagger [SPI] is a mechanism to hook into Dagger's annotation processor and
access the same binding graph  model that Dagger uses. With the SPI, you can
write a plugin that

- adds project-specific errors and warnings, e.g. `Bindings for
  android.content.Context must have a @Qualifier` or `Bindings that implement
  DatabaseRelated must be scoped`
- generates extra Java source files
- serializes the Dagger model to a resource file
- builds visualizations of the Dagger model
- and more!

The opportunities are endless, which is why the SPI is open for anyone to use!

> Note: The APIs for implementing an SPI plugin are experimental and are free to
change. All changes will be documented in our [GitHub releases
page](https://github.com/google/dagger/releases).We encourage you to [reach
out] when you find rough edges so we can make the APIs easier to use.

## Declaring your SPI plugin

SPI plugins are classes that implement [`BindingGraphPlugin`] and are loaded
using Java's [`ServiceLoader`] (easily done by using [`@AutoService`]. Your
`BindingGraphPlugin` should be available alongside Dagger on the annotation
processor path during compilation so that it can be loaded. In Bazel that is done
with a [`java_plugin`]; in Gradle declare a dependency with
`annotationProcessor` scope.

When Dagger detects an SPI plugin on the classpath, it will call its
[`visitGraph(BindingGraph, DiagnosticReporter)`][`visitGraph()`] method for each
valid `@Component` that Dagger compiles. The `BindingGraph` is implemented as a
[`Network`] that has nodes for [components][component nodes], [bindings][binding
nodes], and [missing bindings][missing binding nodes]. Edges in the graph
represent [dependencies][dependency edges] as well as parent-child component
relationships. For more, please refer to the [javadoc][BindingGraph javadoc].

## Adding Errors and Warnings

Plugins can use [`DiagnosticReporter`] to report project-specific errors and
warnings. These diagnostics will be passed to the [`Messager`] with additional
information to help users debug the issue. For example, an invalid
`DependencyEdge` will also print a dependency trace from a component to the
dependency, similar to regular Dagger errors. In fact, for those that are
familiar with writing annotation processors, the `DiagnosticReporter` methods
will look familiar to those on `Messager` except that they take `BindingGraph`
nodes and edges instead of `Element`s.

All reported diagnostics will be prefixed with `[<plugin name>]` so that users
can easily identify the source/category of the diagnostic. The plugin name
defaults the fully qualified class name of the `BindingGraphPlugin`, but can be
configured by returning a value for `BindingGraphPlugin.pluginName()`.

## Generating files

If you'd like to generate files based on the binding graph, you can obtain an
instance of a [`Filer`] from the [`BindingGraph.initFiler()`] method. The filer
can be used within `visitGraph()` to generate files just like a normal
annotation processor would.

One common use case for generating files is to serialize the binding graph model
to JSON/proto for a UI to use later to help users visualize the binding
graph. (P.S.: If you write a really awesome visualization tool, let us know so we
can promote it!)

## `Types` and `Elements`

Annotation processors are given an instance of [`Types`] in order to simplify
code that interfaces with Java's type system, as well as an instance of
[`Elements`] for code that interfaces with source code elements. SPI plugins can
obtain references to these types via the [`BindingGraph.initTypes()`] and
[`BindingGraph.initElements()`] methods.

## Command line options

SPI plugins may declare a set of supported command line options with the
`BindingGraph.supportedOptions()` method. If any of these are passed to `javac`,
Dagger will forward the values to the `initOptions()` method.

[`@AutoService`]: https://github.com/google/auto/tree/master/service
[binding nodes]: https://google.github.io/dagger/api/latest/dagger/model/BindingGraph.BindingNode.html
[`BindingGraph.initElements()`]: https://google.github.io/dagger/api/latest/dagger/spi/BindingGraphPlugin.html#initElements-javax.lang.model.util.Elements-
[`BindingGraph.initFiler()`]: https://google.github.io/dagger/api/latest/dagger/spi/BindingGraphPlugin.html#initFiler-javax.annotation.processing.Filer-
[`BindingGraph.initTypes()`]: https://google.github.io/dagger/api/latest/dagger/spi/BindingGraphPlugin.html#initTypes-javax.lang.model.util.Types-
[BindingGraph javadoc]: https://google.github.io/dagger/api/latest/dagger/model/BindingGraph.html
[`BindingGraphPlugin`]: https://google.github.io/dagger/api/latest/dagger/spi/BindingGraphPlugin.html
[component nodes]: https://google.github.io/dagger/api/latest/dagger/model/BindingGraph.ComponentNode.html
[dependency edges]: https://google.github.io/dagger/api/latest/dagger/model/BindingGraph.DependencyEdge.html
[`DiagnosticReporter`]: https://google.github.io/dagger/api/latest/dagger/spi/DiagnosticReporter.html
[`Filer`]: https://docs.oracle.com/javase/9/docs/api/javax/annotation/processing/Filer.html
[`java_plugin`]: https://docs.bazel.build/versions/master/be/java.html#java_plugin
[`Messager`]: https://docs.oracle.com/javase/9/docs/api/javax/annotation/processing/Messager.html
[missing binding nodes]: https://google.github.io/dagger/api/latest/dagger/model/BindingGraph.MissingBindingNode.html
[`Network`]: http://google.github.io/guava/releases/25.0-jre/api/docs/com/google/common/graph/Network.html
[reach out]: https://github.com/google/dagger/issues/new
[`ServiceLoader`]: https://docs.oracle.com/javase/9/docs/api/java/util/ServiceLoader.html
[SPI]: https://en.wikipedia.org/wiki/Service_provider_interface
[`visitGraph()`]: https://google.github.io/dagger/api/latest/dagger/spi/BindingGraphPlugin.html#visitGraph-dagger.model.BindingGraph-dagger.spi.DiagnosticReporter-


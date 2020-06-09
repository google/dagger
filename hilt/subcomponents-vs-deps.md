---
layout: default
title: Subcomponents vs Component dependencies
---

## Overview

Hilt is based around using Dagger subcomponents as opposed to component
dependencies. This page explains some of the reasons why Hilt was designed this
way.

## Single binding key space

Subcomponents propagate all bindings by default. This includes multibindings
which can be difficult to propagate via component dependencies. This creates a
merged binding key space. This generally makes it easier to understand the
Dagger graph because you don't have to worry about considering if a binding is
propagated or not from a parent component to a child component. Also, if
bindings are not propagated with component dependencies, it is possible to use
two different definitions of the same binding key in different components. This
can make it difficult to walk through code when debugging issues as the binding
definition will be based on the context of the usage.

One of the downsides of a single binding key space is that it can be extra work
to place restrictions on code usage (e.g. if one feature shouldn't use bindings
from another feature). For this we generally recommend using qualifier
annotations that are restricted visibility or using an SPI plugin to enforce
separation of code. Using a qualifier or an SPI plugin is better than building
these concerns into the structure of your Dagger component dependencies graph
because often these rules encode policy. Policy decisions like this are often in
flux (or need to have exceptions allowed) and having to restructure a Dagger
component dependencies graph based on those changes can be costly.

## Propagating bindings with component dependencies defeats Dagger pruning

Since Dagger can see the entry points to the graph, it can figure out which
bindings are unused and not generate code for those bindings. This optimization
goes through subcomponents, but it is defeated by component dependencies because
propagating bindings through component dependencies adds entry point methods. So
even if entry point methods are only used by other Dagger components and across
the components the binding is unused, Dagger will be forced to still generate
that dead code to adhere to its contract.

## Configuration at the root and build speed

One of the main advantages of component dependencies is building Dagger code
separately and in parallel. This can be done because of the lack of implicit
sharing that make components black boxes with respect to each other. However,
Hilt is already based on the idea of central configuration based on build
dependencies. Since Hilt has to aggregate modules, all components would be
generated at the same time anyway so we wouldn't be able to take advantage of
building in parallel.

Instead, to address build speed, Hilt recommends making smaller test apps for
individual feature development. Without Hilt, this would have been difficult to
do because of all of the repeated Dagger boilerplate for the small test app.
However, with Hilt generating all of the Dagger portion based on build
dependencies, putting together a small test app should be much easier.

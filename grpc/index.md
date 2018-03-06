---
layout: default
title: gRPC with Dagger
---

[gRPC] is an open-source RPC framework from Google. Its [Java
library][grpc-java] lets you create RPC servers by implementing interfaces that
are generated from [Protocol Buffer] files, and provides client stub
implementations.

Dagger-gRPC is an extension to [Dagger] that lets you use Dagger to create
applications that use gRPC.

You can use Dagger-gRPC to create [servers](servers.md) or clients [note: client
support is coming soon].

## Call scope

**Call scope** is a scope that lasts for one gRPC call. A gRPC call comprises
one or more request messages and one or more response messages. You can use
[`@CallScope`] to bind objects once for the lifetime of one call. [On the
server](servers#call-scope), bindings in call scope can inject the
[headers][`Metadata`] object for the call.

<!-- References -->

[`@CallScope`]: https://google.github.io/dagger/api/latest/dagger/grpc/CallScope.html
[Dagger]: https://github.com/google/dagger
[grpc-java]: https://github.com/grpc/grpc-java
[gRPC]: http://www.grpc.io/
[`Metadata`]: https://github.com/grpc/grpc-java/blob/master/core/src/main/java/io/grpc/Metadata.java
[Protocol Buffer]: https://developers.google.com/protocol-buffers/docs/overview
[scopes]: https://docs.oracle.com/javaee/7/api/javax/inject/Scope.html

---
layout: default
title: gRPC servers with Dagger
---

## gRPC servers without Dagger

To implement a gRPC service, you create a class that extends a base service
implementation generated from the Protocol Buffer definition.

```proto
service HelloService {
  rpc SayHello(HelloRequest) returns (HelloResponse);
}
```

The service definition above generates a class `HelloServiceGrpc` that contains
a base implementation `HelloServiceImplBase`:

```java
public class HelloServiceGrpc {
  public static class HelloServiceImplBase implements BindableService {
    public void sayHello(
        HelloRequest request, StreamObserver<HelloResponse> responseObserver);
  }
}
```

You then extend that class:

```java
class Hello extends HelloServiceImplBase {
  @Override
  public void sayHello(
      HelloRequest request, StreamObserver<HelloResponse> responseObserver) {
    // ...
  }
}
```

Typically, you create the server by adding an instance of `Hello` to a
[`ServerBuilder`]:

```java
Hello helloService = new Hello(...);
serverBuilder.add(helloService);
Server server = serverBuilder.build();
```

But because you have to create your service instance before you create your
server, if you're using Dagger to create the service, `Hello` can't inject
anything that isn't unscoped or at [`@Singleton`] scope.

## Configuring a service using Dagger-gRPC

You can use Dagger-gRPC to instantiate your service implementation once for each
call. To do so, follow these steps:

1.  Annotate your service implementation with [`@GrpcService`], setting the
    `grpcClass` parameter to the top-level class that contains the base
    implementation it extends, and give it an [`@Inject`] constructor.

    ```java
    @GrpcService(grpcClass = HelloServiceGrpc.class)
    class Hello extends HelloServiceImplBase {
      @Inject Hello(...) {...}
      ...
    }
    ```

    Make sure the Dagger-gRPC server annotation processor runs over your source
    code.


    That will generate an interface `HelloServiceDefinition` and a [module]
    `HelloUnscopedGrpcServiceModule`.

2.  Create a [`@Singleton`] component that implements `HelloServiceDefinition`,
    and install `HelloUnscopedGrpcServiceModule` into that component. In another
    module you install on that component, bind `HelloServiceDefinition` to your
    component type.

    Also make sure to bind a list of [`ServerInterceptor`]s for the service,
    annotated with [`@ForGrpcService(serviceClass)`][`@ForGrpcService`]. The
    list can be empty.

    ```java
    @Singleton
    @Component(modules = {HelloUnscopedGrpcServiceModule.class, MyModule.class})
    interface MyComponent extends HelloServiceDefinition {}

    @Module
    abstract class MyModule {
      @Binds
      abstract HelloServiceDefinition helloComponent(MyComponent myComponent);

      @Provides
      @ForGrpcService(HelloServiceGrpc.class)
      static List<? extends ServerInterceptor> helloInterceptors(
          AuthInterceptor authInterceptor,
          LoggingInterceptor loggingInterceptor) {
        return Arrays.asList(authInterceptor, loggingInterceptor);
      }
    }
    ```

3.  Install [`NettyServerModule`](or [`InProcessServerModule`] for testing) into
    your [`@Singleton`] component. That component will provide the gRPC
    [`Server`] object.

    ```java
    @Singleton
    @Component(modules = {
      NettyServerModule.class,
      HelloUnscopedGrpcServiceModule.class,
      MyModule.class,
    })
    interface MyComponent {
      Server server();
    }
    ```

Now you can start your server, and a new instance of `Hello` will be injected to
handle each call.

```java
int port = ...;
MyComponent component =
    DaggerMyComponent.builder()
        .nettyServerModule(NettyServerModule.bindingToPort(port))
        .build();
Server server = component.server();
server.start();
```

## Binding in call scope <a name=call-scope></a>

If you want to bind objects in a [scope] whose lifetime is the same as one gRPC
[`ServerCall`], Dagger-gRPC supports that too.

1.  Instead of making your [`@Singleton`] component implement
    `HelloServiceDefinition`, create a subcomponent type that implements
    `HelloServiceDefinition` and is in [`@CallScope`]. Install
    `HelloGrpcServiceModule` into it. If you want bindings in that component to
    be able to depend on the gRPC [call metadata], then also install
    [`GrpcCallMetadataModule`].

    ```java
    @CallScope
    @Subcomponent(modules = {
     HelloGrpcServiceModule.class,
     GrpcCallMetadataModule.class,
     ...})
    interface ServiceComponent extends HelloServiceDefinition {}
    ```

2.  Instead of `HelloUnscopedGrpcServiceModule`, install `HelloGrpcProxyModule`
    into your [`@Singleton`] component. In your other module, instead of
    providing `HelloServiceDefinition`, provide an instance of
    `HelloServiceDefinition.Factory` that calls the proper subcomponent factory
    and/or builder method.

    ```java
    @Singleton
    @Component(modules = {HelloGrpcProxyModule.class, MyModule.class})
    interface MyComponent {
     ...
     ServiceComponent serviceComponent(GrpcCallMetadataModule metadataModule);
    }

    @Module
    class MyModule {
     @Provide
     static HelloServiceDefinition.Factory helloComponent(
         final MyComponent myComponent) {
       return new HelloServiceDefinition.Factory() {
         @Override
         public HelloServiceDefinition grpcService(
             GrpcCallMetadataModule metadataModule) {
           return myComponent.serviceComponent(metadataModule);
         }
       };
     }
    }
    ```

3.  Make sure that a module installed into your `HelloServiceDefinition`
    subcomponent or any ancestor component binds the list of interceptors for
    that service, just like above:

    ```java
    @Module
    class MyModuleInstalledInSubcomponentOrAncestor {
     @Provides
     @ForGrpcService(HelloServiceGrpc.class)
     static List<? extends ServerInterceptor> helloInterceptors(
         AuthInterceptor authInterceptor,
         LoggingInterceptor loggingInterceptor) {
       return Arrays.asList(authInterceptor, loggingInterceptor);
     }
    }
    ```

Now any binding annotated with [`@CallScope`] will be used only once per call,
with the bound object shared among all its dependent bindings.

If you've installed [`GrpcCallMetadataModule`] onto the subcomponent, then
you'll also be able to inject the [headers][`Metadata`] from the call into any
binding in the subcomponent.

You create and start the server in the same way as above.

<!-- References -->

[`AbstractServerBuilder`]: https://github.com/grpc/grpc-java/blob/master/core/src/main/java/io/grpc/AbstractServerBuilder.java
[call metadata]: https://github.com/grpc/grpc-java/blob/master/core/src/main/java/io/grpc/Metadata.java
[`@CallScope`]: https://google.github.io/dagger/api/latest/dagger/grpc/server/CallScope.html
[`@ForGrpcService`]: https://google.github.io/dagger/api/latest/dagger/grpc/server/ForGrpcService.html
[`GrpcCallMetadataModule`]: https://google.github.io/dagger/api/latest/dagger/grpc/server/GrpcCallMetadataModule.html
[`@GrpcService`]: https://google.github.io/dagger/api/latest/dagger/grpc/server/GrpcServices.html
[`@Inject`]: https://docs.oracle.com/javaee/7/api/javax/inject/Inject.html
[`Metadata.Headers`]: https://github.com/grpc/grpc-java/blob/master/core/src/main/java/io/grpc/Metadata.java
[module]: https://google.github.io/dagger/api/latest/dagger/Module.html
[`NettyServerModule`]: https://google.github.io/dagger/api/latest/dagger/grpc/server/NettyServerModule.html
[scope]: https://docs.oracle.com/javaee/7/api/javax/inject/Scope.html
[`Server`]: https://github.com/grpc/grpc-java/blob/master/core/src/main/java/io/grpc/Server.java
[`ServerCall`]: https://github.com/grpc/grpc-java/blob/master/core/src/main/java/io/grpc/ServerCall.java
[`ServerInterceptor`]: https://github.com/grpc/grpc-java/blob/master/core/src/main/java/io/grpc/ServerInterceptor.java
[`ServerServiceDefinition`]: https://github.com/grpc/grpc-java/blob/master/core/src/main/java/io/grpc/ServerServiceDefinition.java
[`@Singleton`]: https://docs.oracle.com/javaee/7/api/javax/inject/Singleton.html

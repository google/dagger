---
layout: default
title: Dagger
redirect_from:

  - /users-guide
---

The best classes in any application are the ones that do stuff: the
`BarcodeDecoder`, the `KoopaPhysicsEngine`, and the `AudioStreamer`. These
classes have dependencies; perhaps a `BarcodeCameraFinder`,
`DefaultPhysicsEngine`, and an `HttpStreamer`.

To contrast, the worst classes in any application are the ones that take up
space without doing much at all: the `BarcodeDecoderFactory`, the
`CameraServiceLoader`, and the `MutableContextWrapper`. These classes are the
clumsy duct tape that wires the interesting stuff together.

Dagger is a replacement for these `Factory` classes that implements the
[dependency injection][DI] design pattern without the burden of writing the
boilerplate. It allows you to focus on the interesting classes. Declare
dependencies, specify how to satisfy them, and ship your app.

By building on standard [`javax.inject`] annotations ([JSR 330]), each class is
**easy to test**. You don't need a bunch of boilerplate just to swap the
`RpcCreditCardService` out for a `FakeCreditCardService`.

Dependency injection isn't just for testing. It also makes it easy to create
**reusable, interchangeable modules**. You can share the same
`AuthenticationModule` across all of your apps. And you can run
`DevLoggingModule` during development and `ProdLoggingModule` in production to
get the right behavior in each situation.

## Why Dagger 2 is Different

[Dependency injection][DI] frameworks have existed for years with a whole
variety of APIs for configuring and injecting. So, why reinvent the wheel?
Dagger 2 is the first to **implement the full stack with generated code**. The
guiding principle is to generate code that mimics the code that a user might
have hand-written to ensure that dependency injection is as simple, traceable
and performant as it can be. For more background on the design, watch
[this talk](https://youtu.be/oK_XtfXPkqw) ([slides][Dagger Talk Slides]) by
Gregory Kick.

## License

```
Copyright 2012 The Dagger Authors

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

<!-- References -->
[Dagger Talk Slides]: https://docs.google.com/presentation/d/1fby5VeGU9CN8zjw4lAb2QPPsKRxx6mSwCe9q7ECNSJQ/pub?start=false&loop=false&delayms=3000
[DI]: http://en.wikipedia.org/wiki/Dependency_injection
[`javax.inject`]: http://docs.oracle.com/javaee/7/api/javax/inject/package-summary.html
[JSR 330]: https://jcp.org/en/jsr/detail?id=330

---
layout: default
title: Frequently Asked Questions
---

<!-- note that due to b/28762248 this is rendering weirdly small -->
# [`@Binds`]

<!-- This is an h2 tag instead of ## because there is no way to have a header
     that spans multiple lines in markdown -->
 <h2>Why can't I put [`@Binds`] methods and instance [`@Provides`] methods in the
     same module?</h2>

Because `@Binds` methods are _just_ a method _declaration_, they are expressed
as `abstract` methods — no implementation is ever created and nothing is never
invoked. On the other hand, a `@Provides` method _does_ have an implmentation and
_will_ be invoked.

Since `@Binds` methods are never implemented, no concrete class is ever created
that implements those methods.  However, instance `@Provides` methods _require_
a concrete class in order to construct an instance on which the method can be
invoked.

### What do I do instead?

The easiest change is to make the provides method `static`.  In addition to
being compatible with `@Binds`, they often perform better than instance provides
methods.

If the method _must_ be an instance method (e.g. returns a value from a field),
the easiest fix is to separate your `@Provides` methods and `@Binds` methods
into two separate modules and include one from the other.  A simple example that
provides an `HttpServletRequest` and binds `ServletRequest` might look like:

```java
@Module(includes = Declarations.class)
final class HttpServletRequestModule {
  @Module
  interface Declarations {
    @Binds ServletRequest bindServletRequest(HttpServletRequet httpRequest);
  }

  private final HttpServletRequet httpRequest;

  HttpServletRequestModule(HttpServletRequet httpRequest) {
    this.httpRequest = httpRequest;
  }
}
```


[`@Binds`]: http://google.github.io/dagger/api/latest/dagger/Binds.html
[`@Provides`]: http://google.github.io/dagger/api/latest/dagger/Provides.html




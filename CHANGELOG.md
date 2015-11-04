Change Log
==========

Dagger 2 (Components)
---------------------

### Version 2.0.1 *(2015-05-28)*

A maintenance release fixing immediate issues following the Dagger 2.0 release, including:

  * Speed up Graph Validation (reduce build times by 10s of seconds on sampled large projects)
  * Generate correct code for @MapKey annotation types (beta)
  * Fix to properly emit code for class literal values in @MapKey annotations.
  * Fix for injecting component dependencies
  * Fixes to generated code to account for differences in generics handling in ecg vs. javac.
  * Subcomponents can now be abstract classes.
  * Subcomponents now properly build the object graph in some cases involving explicit bindings and (sub)components without scope.
  * Improve runtime performance of SetFactory (set multibindings)
  * Other smaller fixes, refactorings, etc.

### Version 2.0.0 *(2015-04-21)*

The initial release of the 2.0 code-line, supporting:

  * `@Component` interfaces representing a custom API to access a graph of objects
  * JSR-330 injection automation using `@Inject` signals, `@Qualifiers`
  * Simple bindings of implementations to interfaces, custom provision of objects, and set-bindings
  * Compile-time validation of graph structure (cycles, missing bindings, duplicate bindings)
  * Generation of 
    - backing implementations for components
    - factories for `@Inject` constructors and modules
    - members-injectors for `@Inject` methods and fields
  * Beta support for
    - Map bindings
    - [Producers](http://google.github.io/dagger/api/latest/dagger/producers/Producer.html)

==============================================================

Dagger 1 (ObjectGraph)
----------------------

### Version 1.2.0 *(2013-12-13)*

 * Numerous performance improvements in both the compiler and runtime.
   * Use more efficient `String` concatenation.
   * Module adapters are now stateless.
   * Use read/write locks over global locks.
   * Reflective constructor invocation is now cached with `Class.newInstance`.
   * Avoid re-linking all bindings when calling `.plus()`.
 * Set bindings are now unioned when calling `.plus()`.
 * Fix: Tolerate missing type information during compilation by deferring writing
   module adapters.


### Version 1.1.0 *(2013-08-05)*

 * Module loading now requires code generation via the 'dagger-compiler' artifact.
 * Allow multiple contributions to Set binding via `Provides.Type.SET_VALUES`.
 * Request classloading from the classloader of the requesting object, not the current thread's
   context classloader.
 * Cache class loading at the root injector to reduce costs of loading adapters.
 * Fix: Primitive array types are no longer incorrectly changed to their boxed type.
 * Update JavaWriter to 2.1.1.


### Version 1.0.1 *(2013-06-03)*

 * Explicitly forbid declaring `@Inject` on a class type (e.g., `@Inject class Foo {}`).
 * Update JavaWriter to 1.0.5.


### Version 1.0.0 *(2013-05-07)*

Initial release.

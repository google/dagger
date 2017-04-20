/*
 * Copyright (C) 2016 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.functional;

import dagger.Component;
import dagger.Module;
import dagger.Provides;

/**
 * This tests that @Module.includes are traversed for supertypes of a module.
 */
final class ModuleIncludesCollectedFromModuleSuperclasses {
  @Component(modules = TopLevelModule.class)
  interface C {
    Foo<String> foo();
    int includedInTopLevelModule();
    String includedFromModuleInheritance();
  }

  @Module(includes = IncludedTopLevel.class)
  static class TopLevelModule extends FooModule<String> {}

  static class Foo<T> {}

  @Module(includes = IncludedFromModuleInheritance.class)
  abstract static class FooModule<T> extends FooCreator {
    @Provides Foo<T> fooOfT() {
      return createFoo();
    }
  }

  static class FooCreator {
    <T> Foo<T> createFoo() {
      return new Foo<T>();
    }
  }

  @Module
  static class IncludedTopLevel {
    @Provides int i() {
      return 123;
    }
  }

  @Module
  static class IncludedFromModuleInheritance {
    @Provides String inheritedProvision() {
      return "inherited";
    }
  }
}

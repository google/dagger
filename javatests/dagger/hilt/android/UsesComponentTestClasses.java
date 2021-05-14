/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.hilt.android;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dagger.BindsInstance;
import dagger.hilt.DefineComponent;
import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import java.lang.annotation.Retention;
import javax.inject.Inject;
import javax.inject.Qualifier;
import javax.inject.Scope;

/**
 * Subcomponent used to verify that subcomponents are correctly installed in shared test components.
 */
public abstract class UsesComponentTestClasses {
  /** Qualifier for test bindings. */
  @Qualifier
  public @interface UsesComponentQualifier {}

  @UsesComponentTestSubcomponentScoped
  public static class Foo {
    final int id;

    @Inject
    Foo(int id) {
      this.id = id;
    }
  }

  @Scope
  @Retention(RUNTIME)
  public @interface UsesComponentTestSubcomponentScoped {}

  @UsesComponentTestSubcomponentScoped
  @DefineComponent(parent = SingletonComponent.class)
  public interface UsesComponentTestSubcomponent {
    @DefineComponent.Builder
    interface Builder {
      @BindsInstance
      Builder id(int id);

      UsesComponentTestSubcomponent build();
    }
  }

  @EntryPoint
  @InstallIn(SingletonComponent.class)
  public interface UsesComponentTestSubcomponentBuilderEntryPoint {
    UsesComponentTestSubcomponent.Builder mySubcomponentBuilder();
  }

  @EntryPoint
  @InstallIn(UsesComponentTestSubcomponent.class)
  public interface FooEntryPoint {
    Foo foo();
  }

  private UsesComponentTestClasses() {}
}

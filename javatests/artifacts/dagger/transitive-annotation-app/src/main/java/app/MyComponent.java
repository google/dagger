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

package app;

import dagger.Component;
import javax.inject.Singleton;
import library1.AssistedFoo;
import library1.Foo;
import library1.MyBaseComponent;
import library1.MyComponentDependency;
import library1.MyComponentDependencyBinding;
import library1.MyComponentModule;
import library1.MyQualifier;
import library1.MySubcomponentWithBuilder;
import library1.MySubcomponentWithFactory;

@Singleton
@Component(dependencies = MyComponentDependency.class, modules = MyComponentModule.class)
abstract class MyComponent extends MyBaseComponent {
  abstract Foo foo();

  abstract AssistedFoo.Factory assistedFooFactory();

  @MyQualifier
  abstract MyComponentModule.ScopedQualifiedBindsType scopedQualifiedBindsType();

  abstract MyComponentModule.ScopedUnqualifiedBindsType scopedUnqualifiedBindsType();

  @MyQualifier
  abstract MyComponentModule.UnscopedQualifiedBindsType unscopedQualifiedBindsType();

  abstract MyComponentModule.UnscopedUnqualifiedBindsType unscopedUnqualifiedBindsType();

  @MyQualifier
  abstract MyComponentModule.ScopedQualifiedProvidesType scopedQualifiedProvidesType();

  abstract MyComponentModule.ScopedUnqualifiedProvidesType scopedUnqualifiedProvidesType();

  @MyQualifier
  abstract MyComponentModule.UnscopedQualifiedProvidesType unscopedQualifiedProvidesType();

  abstract MyComponentModule.UnscopedUnqualifiedProvidesType unscopedUnqualifiedProvidesType();

  abstract MySubcomponentWithFactory.Factory mySubcomponentWithFactory();

  abstract MySubcomponentWithBuilder.Builder mySubcomponentWithBuilder();

  @MyQualifier
  abstract MyComponentDependencyBinding qualifiedMyComponentDependencyBinding();

  abstract MyComponentDependencyBinding unqualifiedMyComponentDependencyBinding();

  @Component.Factory
  abstract static class Factory extends MyBaseComponent.Factory {
    public abstract MyComponent create(
        MyComponentModule myComponentModule,
        MyComponentDependency myComponentDependency);
  }
}

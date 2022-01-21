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
import library1.MyComponentModule;
import library1.MyQualifier;

@Singleton
@Component(modules = MyComponentModule.class)
interface MyComponent {
  Foo foo();

  AssistedFoo.Factory assistedFooFactory();

  @MyQualifier
  MyComponentModule.ScopedQualifiedBindsType scopedQualifiedBindsType();

  MyComponentModule.ScopedUnqualifiedBindsType scopedUnqualifiedBindsType();

  @MyQualifier
  MyComponentModule.UnscopedQualifiedBindsType unscopedQualifiedBindsType();

  MyComponentModule.UnscopedUnqualifiedBindsType unscopedUnqualifiedBindsType();

  @MyQualifier
  MyComponentModule.ScopedQualifiedProvidesType scopedQualifiedProvidesType();

  MyComponentModule.ScopedUnqualifiedProvidesType scopedUnqualifiedProvidesType();

  @MyQualifier
  MyComponentModule.UnscopedQualifiedProvidesType unscopedQualifiedProvidesType();

  MyComponentModule.UnscopedUnqualifiedProvidesType unscopedUnqualifiedProvidesType();

  @Component.Factory
  interface Factory {
    MyComponent create(MyComponentModule myComponentModule);
  }
}

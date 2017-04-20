/*
 * Copyright (C) 2015 The Dagger Authors.
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

package dagger.functional.builder;

import dagger.Component;

@Component(
    modules = {StringModule.class, IntModuleIncludingDoubleAndFloat.class, LongModule.class},
    dependencies = DepComponent.class)
interface TestComponentWithGenericBuilderAbstractClass {
  String s();
  int i();
  long l();
  float f();
  double d();
  
  static abstract class SharedBuilder<B, C, M1, M2> {
    abstract C build(); // Test resolving return type of build()
    abstract B setM1(M1 m1); // Test resolving return type & param of setter
    abstract SharedBuilder<B, C, M1, M2> setM2(M2 m2); // Test being overridden
    abstract void doubleModule(DoubleModule doubleModule);  // Test being overridden
    abstract SharedBuilder<B, C, M1, M2> depComponent(FloatModule floatModule); // Test return type
  }
  
  @Component.Builder
  static abstract class Builder extends SharedBuilder<Builder,
      TestComponentWithGenericBuilderAbstractClass, StringModule,
      IntModuleIncludingDoubleAndFloat> {
    @Override abstract Builder setM2(IntModuleIncludingDoubleAndFloat m2); // Test covariant overrides
    @Override abstract void doubleModule(DoubleModule module3); // Test simple overrides allowed    
    abstract void depComponent(DepComponent depComponent);
    
    // Note we're missing LongModule & FloatModule -- they're implicit
  }
}

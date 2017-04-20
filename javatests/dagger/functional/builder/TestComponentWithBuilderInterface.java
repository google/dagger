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
interface TestComponentWithBuilderInterface {
  String s();
  int i();
  long l();
  float f();
  double d();
  
  interface SharedBuilder {
    // Make sure we use the overriding signature.
    Object build();
    Object stringModule(StringModule m1); 
  }
  
  @Component.Builder
  interface Builder extends SharedBuilder {
    @Override TestComponentWithBuilderInterface build(); // Narrowing return type
    @Override Builder stringModule(StringModule stringModule); // Narrowing return type
    Builder intModule(IntModuleIncludingDoubleAndFloat intModule);
    void doubleModule(DoubleModule doubleModule); // Module w/o args
    void depComponent(DepComponent depComponent);
    
    // Note we're missing LongModule & FloatModule -- they/re implicit
  }
}

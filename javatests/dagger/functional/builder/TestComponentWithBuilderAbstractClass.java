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
abstract class TestComponentWithBuilderAbstractClass {
  
  static Builder builder() {
    return DaggerTestComponentWithBuilderAbstractClass.builder();
  }
  
  abstract String s();
  abstract int i();
  abstract long l();
  abstract float f();
  abstract double d();
  

  static abstract class SharedBuilder {
    // Make sure we use the overriding signature.
    abstract Object build();
    
    Object stringModule(@SuppressWarnings("unused") StringModule stringModule) {
      return null;
    } 

    SharedBuilder ignoredLongModule(@SuppressWarnings("unused") LongModule longModule) {
      return null;
    }
    
  }
  
  @Component.Builder
  static abstract class Builder extends SharedBuilder {
    @Override abstract TestComponentWithBuilderAbstractClass build(); // Narrowing return type
    @Override abstract Builder stringModule(StringModule stringModule); // Make abstract & narrow
    abstract Builder intModule(IntModuleIncludingDoubleAndFloat intModule);
    abstract void doubleModule(DoubleModule doubleModule); // Module w/o args
    abstract void depComponent(DepComponent depComponent);

    Builder ignoredIntModule(
        @SuppressWarnings("unused") IntModuleIncludingDoubleAndFloat intModule) {
      return null;
    }    
    
    // Note we're missing LongModule & FloatModule -- they/re implicit
  }
}

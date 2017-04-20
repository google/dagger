/*
 * Copyright (C) 2017 The Dagger Authors.
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

interface BuildMethodCovariantReturnInherited {
  @Component
  interface Simple {
    interface BuilderSupertype {
      Object build();
    }

    @Component.Builder
    interface Builder extends BuilderSupertype {}
  }

  interface ComponentSupertype {}

  @Component
  interface GenericBuilderType extends ComponentSupertype {
    interface GenericBuilderSupertype<T> {
      T build();
    }

    @Component.Builder
    interface Builder extends GenericBuilderSupertype<ComponentSupertype> {}
  }

  interface ParameterizedComponentSupertype<T> {}

  @Component
  interface GenericComponentSupertypeAndBuilderSupertype
      extends ParameterizedComponentSupertype<Object> {

    interface GenericBuilderSupertype<T> {
      ParameterizedComponentSupertype<T> build();
    }

    @Component.Builder
    interface Builder extends GenericBuilderSupertype<Object> {}
  }
}

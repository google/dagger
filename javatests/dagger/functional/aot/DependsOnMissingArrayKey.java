/*
 * Copyright (C) 2018 The Dagger Authors.
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

package dagger.functional.aot;

import dagger.Module;
import dagger.Provides;
import dagger.Subcomponent;

/**
 * Regression test for an ahead-of-time subcomponents bug where generating the name for a missing
 * binding method for a key of an array type threw an exception.
 */
final class DependsOnMissingArrayKey {
  @Module
  abstract static class ModuleArrayDependencies {
    @Provides
    static int dependsOnMissingArrayType(int[] primitive, Object[] object, String[][] doubleArray) {
      return 0;
    }
  }

  @Subcomponent(modules = ModuleArrayDependencies.class)
  interface HasMissingArrayBindings {
    int dependsOnMissingArrayType();
  }
}

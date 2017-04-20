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

import dagger.Module;
import dagger.Provides;
import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Module with bindings that might result in generated factories with conflicting field and
 * parameter names.
 */
@Module
final class ModuleWithConflictingNames {
  @Provides
  static Object object(int foo, Provider<String> fooProvider) {
    return foo + fooProvider.get();
  }

  /**
   * A class that might result in a generated factory with conflicting field and parameter names.
   */
  static class InjectedClassWithConflictingNames {
    final int foo;
    final Provider<String> fooProvider;

    @Inject
    InjectedClassWithConflictingNames(int foo, Provider<String> fooProvider) {
      this.foo = foo;
      this.fooProvider = fooProvider;
    }
  }
}

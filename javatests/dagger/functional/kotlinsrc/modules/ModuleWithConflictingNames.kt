/*
 * Copyright (C) 2022 The Dagger Authors.
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

package dagger.functional.kotlinsrc.modules

import dagger.Module
import dagger.Provides
import javax.inject.Inject
import javax.inject.Provider

/**
 * Module with bindings that might result in generated factories with conflicting field and
 * parameter names.
 */
@Module
internal object ModuleWithConflictingNames {
  @Provides fun getObject(foo: Int, fooProvider: Provider<String>): Any = "$foo${fooProvider.get()}"

  /**
   * A class that might result in a generated factory with conflicting field and parameter names.
   */
  class InjectedClassWithConflictingNames
  @Inject
  constructor(val foo: Int, val fooProvider: Provider<String>)
}

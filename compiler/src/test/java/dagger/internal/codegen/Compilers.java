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

package dagger.internal.codegen;

import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compiler;

/** {@link Compiler} instances for testing Dagger. */
final class Compilers {

  /** Returns a compiler that runs the Dagger processor. */
  static Compiler daggerCompiler() {
    return javac().withProcessors(new ComponentProcessor());
  }
}

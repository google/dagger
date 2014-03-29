/*
 * Copyright (C) 2014 Google, Inc.
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

import javax.annotation.processing.Messager;

/**
 * An interface for types that represent a compilation
 * {@linkplain javax.tools.Diagnostic.Kind#ERROR error} (though, not necessarily a
 * {@link Throwable}) that can be printed using a {@link Messager}.
 *
 * @author Gregory Kick
 * @since 2.0
 */
interface PrintableErrorMessage {
  /**
   * Prints the information represented by this object to the given {@link Messager} as an
   * {@link javax.tools.Diagnostic.Kind#ERROR}.
   */
  void printMessageTo(Messager messager);
}

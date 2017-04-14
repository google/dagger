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

import com.google.auto.common.MoreTypes;
import java.util.Optional;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Utilities for working with {@link TypeMirror} objects. Each is a candidate to move to {@link
 * MoreTypes}.
 */
final class DaggerTypes {
  /**
   * Returns the non-{@link Object} superclass of the type with the proper type parameters. An empty
   * {@link Optional} is returned if there is no non-{@link Object} superclass.
   */
  static Optional<DeclaredType> nonObjectSuperclass(
      Types types, Elements elements, DeclaredType type) {
    return Optional.ofNullable(MoreTypes.nonObjectSuperclass(types, elements, type).orNull());
  }
}

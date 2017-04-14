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

package dagger.internal.codegen;

import java.util.Optional;
import javax.tools.Diagnostic;

/**
 * Allows options to control how component process validates things such as scope cycles
 * or nullability.
 */
enum ValidationType {
  ERROR,
  WARNING,
  NONE;

  Optional<Diagnostic.Kind> diagnosticKind() {
    switch (this) {
      case ERROR:
        return Optional.of(Diagnostic.Kind.ERROR);
      case WARNING:
        return Optional.of(Diagnostic.Kind.WARNING);
      default:
        return Optional.empty();
    }
  }
}

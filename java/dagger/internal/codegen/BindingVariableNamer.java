/*
 * Copyright (C) 2014 The Dagger Authors.
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

import dagger.model.DependencyRequest;
import dagger.model.Key;

/**
 * Suggests a variable name for a type based on a {@link Binding}. Prefer
 * {@link DependencyVariableNamer} for cases where a specific {@link DependencyRequest} is present.
 */
final class BindingVariableNamer {
  private BindingVariableNamer() {}

  static String name(Binding binding) {
    Key key = binding.key();
    if (binding instanceof ContributionBinding
        && ((ContributionBinding) binding).contributionType().equals(ContributionType.SET)) {
      key = key.toBuilder().type(SetType.from(key.type()).elementType()).build();
    }
    return KeyVariableNamer.name(key);
  }
}

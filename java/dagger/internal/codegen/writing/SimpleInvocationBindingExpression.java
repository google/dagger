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

package dagger.internal.codegen.writing;

import static com.google.common.base.Preconditions.checkNotNull;

import dagger.internal.codegen.binding.ContributionBinding;

/** A simple binding expression for instance requests. Does not scope. */
abstract class SimpleInvocationBindingExpression extends BindingExpression {
  private final ContributionBinding binding;

  SimpleInvocationBindingExpression(ContributionBinding binding) {
    this.binding = checkNotNull(binding);
  }

  @Override
  boolean requiresMethodEncapsulation() {
    return !binding.dependencies().isEmpty();
  }
}

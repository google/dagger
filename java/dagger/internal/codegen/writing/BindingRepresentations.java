/*
 * Copyright (C) 2021 The Dagger Authors.
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

import static dagger.internal.codegen.javapoet.TypeNames.DOUBLE_CHECK;
import static dagger.internal.codegen.javapoet.TypeNames.SINGLE_CHECK;

import com.squareup.javapoet.CodeBlock;
import dagger.internal.codegen.binding.Binding;
import dagger.internal.codegen.writing.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;
import javax.inject.Inject;

/** Holds common methods for BindingRepresentations. */
final class BindingRepresentations {

  @Inject BindingRepresentations() {}

  FrameworkInstanceCreationExpression scope(
      Binding binding, FrameworkInstanceCreationExpression unscoped) {
    return () ->
        CodeBlock.of(
            "$T.provider($L)",
            binding.scope().get().isReusable()
                ? SINGLE_CHECK
                : DOUBLE_CHECK,
            unscoped.creationExpression());
  }
}

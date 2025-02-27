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

import static androidx.room.compiler.codegen.compat.XConverters.toJavaPoet;

import com.squareup.javapoet.CodeBlock;
import dagger.internal.codegen.binding.Binding;
import dagger.internal.codegen.writing.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;
import dagger.internal.codegen.xprocessing.XTypeNames;
import javax.inject.Inject;

/** Holds common methods for BindingRepresentations. */
final class BindingRepresentations {

  @Inject BindingRepresentations() {}

  FrameworkInstanceCreationExpression scope(
      Binding binding, FrameworkInstanceCreationExpression unscoped) {
    return () ->
        CodeBlock.of(
            "$T.provider($L)",
            toJavaPoet(
                binding.scope().get().isReusable()
                    ? XTypeNames.SINGLE_CHECK
                    : XTypeNames.DOUBLE_CHECK),
            unscoped.creationExpression());
  }
}

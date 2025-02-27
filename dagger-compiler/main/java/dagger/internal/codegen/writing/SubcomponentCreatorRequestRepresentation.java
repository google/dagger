/*
 * Copyright (C) 2017 The Dagger Authors.
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
import static dagger.internal.codegen.javapoet.CodeBlocks.toParametersCodeBlock;

import androidx.room.compiler.codegen.XClassName;
import com.squareup.javapoet.CodeBlock;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.SubcomponentCreatorBinding;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;

/** A binding expression for a subcomponent creator that just invokes the constructor. */
final class SubcomponentCreatorRequestRepresentation extends RequestRepresentation {
  private final ShardImplementation shardImplementation;
  private final SubcomponentCreatorBinding binding;

  @AssistedInject
  SubcomponentCreatorRequestRepresentation(
      @Assisted SubcomponentCreatorBinding binding,
      ComponentImplementation componentImplementation) {
    this.binding = binding;
    this.shardImplementation = componentImplementation.shardImplementation(binding);
  }

  @Override
  Expression getDependencyExpression(XClassName requestingClass) {
    return Expression.create(
        binding.key().type().xprocessing(),
        "new $T($L)",
        toJavaPoet(shardImplementation.getSubcomponentCreatorSimpleName(binding.key())),
        shardImplementation.componentFieldsByImplementation().values().stream()
            .map(field -> CodeBlock.of("$N", field))
            .collect(toParametersCodeBlock()));
  }

  CodeBlock getDependencyExpressionArguments() {
    return shardImplementation.componentFieldsByImplementation().values().stream()
        .map(field -> CodeBlock.of("$N", field))
        .collect(toParametersCodeBlock());
  }

  @AssistedFactory
  static interface Factory {
    SubcomponentCreatorRequestRepresentation create(SubcomponentCreatorBinding binding);
  }
}

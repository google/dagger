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

import static dagger.internal.codegen.javapoet.CodeBlocks.toParametersCodeBlock;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;
import java.util.ArrayList;
import java.util.List;

/** A binding expression for a subcomponent creator that just invokes the constructor. */
final class SubcomponentCreatorRequestRepresentation extends RequestRepresentation {
  private final ShardImplementation shardImplementation;
  private final ContributionBinding binding;
  private final boolean isExperimentalMergedMode;

  @AssistedInject
  SubcomponentCreatorRequestRepresentation(
      @Assisted ContributionBinding binding, ComponentImplementation componentImplementation) {
    this.binding = binding;
    this.shardImplementation = componentImplementation.shardImplementation(binding);
    this.isExperimentalMergedMode =
        componentImplementation.compilerMode().isExperimentalMergedMode();
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    return Expression.create(
        binding.key().type().xprocessing(),
        "new $T($L)",
        shardImplementation.getSubcomponentCreatorSimpleName(binding.key()),
        isExperimentalMergedMode
            ? getDependenciesExperimental()
            : shardImplementation.componentFieldsByImplementation().values().stream()
                .map(field -> CodeBlock.of("$N", field))
                .collect(toParametersCodeBlock()));
  }

  private CodeBlock getDependenciesExperimental() {
    List<CodeBlock> expressions = new ArrayList<>();
    int index = 0;
    for (FieldSpec field : shardImplementation.componentFieldsByImplementation().values()) {
      expressions.add(CodeBlock.of("($T) dependencies[$L]", field.type, index++));
    }
    return expressions.stream().collect(toParametersCodeBlock());
  }

  CodeBlock getDependencyExpressionArguments() {
    return shardImplementation.componentFieldsByImplementation().values().stream()
        .map(field -> CodeBlock.of("$N", field))
        .collect(toParametersCodeBlock());
  }

  @AssistedFactory
  static interface Factory {
    SubcomponentCreatorRequestRepresentation create(ContributionBinding binding);
  }
}

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

import static dagger.internal.codegen.xprocessing.XCodeBlocks.toParametersCodeBlock;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XCodeBlock;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.SubcomponentCreatorBinding;
import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;
import dagger.internal.codegen.xprocessing.XExpression;

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
  XExpression getDependencyExpression(XClassName requestingClass) {
    return XExpression.create(
        binding.key().type().xprocessing(),
        XCodeBlock.ofNewInstance(
            shardImplementation.getSubcomponentCreatorSimpleName(binding.key()),
            "%L",
            shardImplementation.componentFieldsByImplementation().values().stream()
                .map(field -> XCodeBlock.of("%N", field))
                .collect(toParametersCodeBlock())));
  }

  XCodeBlock getDependencyExpressionArguments() {
    return shardImplementation.componentFieldsByImplementation().values().stream()
        .map(field -> XCodeBlock.of("%N", field))
        .collect(toParametersCodeBlock());
  }

  @AssistedFactory
  static interface Factory {
    SubcomponentCreatorRequestRepresentation create(SubcomponentCreatorBinding binding);
  }
}

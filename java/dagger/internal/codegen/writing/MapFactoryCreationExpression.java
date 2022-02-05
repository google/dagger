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

package dagger.internal.codegen.writing;

import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.binding.MapKeys.getMapKeyExpression;
import static dagger.internal.codegen.binding.SourceFiles.mapFactoryClassName;
import static dagger.internal.codegen.extension.DaggerCollectors.toOptional;

import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import com.squareup.javapoet.CodeBlock;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.base.MapType;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.spi.model.DependencyRequest;
import java.util.stream.Stream;

/** A factory creation expression for a multibound map. */
final class MapFactoryCreationExpression extends MultibindingFactoryCreationExpression {

  private final XProcessingEnv processingEnv;
  private final ComponentImplementation componentImplementation;
  private final BindingGraph graph;
  private final ContributionBinding binding;

  @AssistedInject
  MapFactoryCreationExpression(
      @Assisted ContributionBinding binding,
      XProcessingEnv processingEnv,
      ComponentImplementation componentImplementation,
      ComponentRequestRepresentations componentRequestRepresentations,
      BindingGraph graph) {
    super(binding, componentImplementation, componentRequestRepresentations);
    this.processingEnv = processingEnv;
    this.binding = checkNotNull(binding);
    this.componentImplementation = componentImplementation;
    this.graph = graph;
  }

  @Override
  public CodeBlock creationExpression() {
    CodeBlock.Builder builder = CodeBlock.builder().add("$T.", mapFactoryClassName(binding));
    if (!useRawType()) {
      MapType mapType = MapType.from(binding.key());
      // TODO(ronshapiro): either inline this into mapFactoryClassName, or add a
      // mapType.unwrappedValueType() method that doesn't require a framework type
      XType valueType =
          Stream.of(TypeNames.PROVIDER, TypeNames.PRODUCER, TypeNames.PRODUCED)
              .filter(mapType::valuesAreTypeOf)
              .map(mapType::unwrappedValueType)
              .collect(toOptional())
              .orElseGet(mapType::valueType);
      builder.add("<$T, $T>", mapType.keyType().getTypeName(), valueType.getTypeName());
    }

    builder.add("builder($L)", binding.dependencies().size());

    for (DependencyRequest dependency : binding.dependencies()) {
      ContributionBinding contributionBinding = graph.contributionBinding(dependency.key());
      builder.add(
          ".put($L, $L)",
          getMapKeyExpression(contributionBinding, componentImplementation.name(), processingEnv),
          multibindingDependencyExpression(dependency));
    }
    builder.add(".build()");

    return builder.build();
  }

  @AssistedFactory
  static interface Factory {
    MapFactoryCreationExpression create(ContributionBinding binding);
  }
}

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

import static androidx.room.compiler.codegen.XTypeNameKt.toJavaPoet;
import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.binding.SourceFiles.setFactoryClassName;

import com.squareup.javapoet.CodeBlock;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.base.ContributionType;
import dagger.internal.codegen.base.SetType;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.BindingType;
import dagger.internal.codegen.binding.MultiboundSetBinding;
import dagger.internal.codegen.model.DependencyRequest;
import dagger.internal.codegen.xprocessing.XTypeNames;

/** A factory creation expression for a multibound set. */
final class SetFactoryCreationExpression extends MultibindingFactoryCreationExpression {
  private final BindingGraph graph;
  private final MultiboundSetBinding binding;

  @AssistedInject
  SetFactoryCreationExpression(
      @Assisted MultiboundSetBinding binding,
      ComponentImplementation componentImplementation,
      ComponentRequestRepresentations componentRequestRepresentations,
      BindingGraph graph) {
    super(binding, componentImplementation, componentRequestRepresentations);
    this.binding = checkNotNull(binding);
    this.graph = graph;
  }

  @Override
  public CodeBlock creationExpression() {
    CodeBlock.Builder builder =
        CodeBlock.builder().add("$T.", toJavaPoet(setFactoryClassName(binding)));
    if (!useRawType()) {
      SetType setType = SetType.from(binding.key());
      builder.add(
          "<$T>",
          toJavaPoet(
              setType.elementsAreTypeOf(XTypeNames.PRODUCED)
                  ? setType.unwrappedElementType(XTypeNames.PRODUCED).asTypeName()
                  : setType.elementType().asTypeName()));
    }

    int individualProviders = 0;
    int setProviders = 0;
    CodeBlock.Builder builderMethodCalls = CodeBlock.builder();
    String methodNameSuffix =
        binding.bindingType().equals(BindingType.PROVISION) ? "Provider" : "Producer";

    for (DependencyRequest dependency : binding.dependencies()) {
      ContributionType contributionType =
          graph.contributionBinding(dependency.key()).contributionType();
      String methodNamePrefix;
      switch (contributionType) {
        case SET:
          individualProviders++;
          methodNamePrefix = "add";
          break;
        case SET_VALUES:
          setProviders++;
          methodNamePrefix = "addCollection";
          break;
        default:
          throw new AssertionError(dependency + " is not a set multibinding");
      }

      builderMethodCalls.add(
          ".$N$N($L)",
          methodNamePrefix,
          methodNameSuffix,
          multibindingDependencyExpression(dependency));
    }
    builder.add("builder($L, $L)", individualProviders, setProviders);
    builder.add(builderMethodCalls.build());

    return builder.add(".build()").build();
  }

  @AssistedFactory
  static interface Factory {
    SetFactoryCreationExpression create(MultiboundSetBinding binding);
  }
}

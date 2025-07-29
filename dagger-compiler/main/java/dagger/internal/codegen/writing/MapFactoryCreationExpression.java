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
import static dagger.internal.codegen.binding.MapKeys.getLazyClassMapKeyExpression;
import static dagger.internal.codegen.binding.MapKeys.getMapKeyExpression;
import static dagger.internal.codegen.binding.SourceFiles.mapFactoryClassName;
import static dagger.internal.codegen.writing.ComponentImplementation.MethodSpecKind.INITIALIZE_HELPER_METHOD;
import static dagger.internal.codegen.xprocessing.XCodeBlocks.parameterNames;
import static dagger.internal.codegen.xprocessing.XFunSpecs.methodBuilder;
import static javax.lang.model.element.Modifier.PRIVATE;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XCodeBlock;
import androidx.room.compiler.codegen.XFunSpec;
import androidx.room.compiler.codegen.XParameterSpec;
import androidx.room.compiler.codegen.XTypeName;
import androidx.room.compiler.processing.XProcessingEnv;
import com.google.common.collect.ImmutableSet;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.base.MapType;
import dagger.internal.codegen.base.UniqueNameSet;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.binding.KeyVariableNamer;
import dagger.internal.codegen.binding.MapKeys;
import dagger.internal.codegen.binding.MultiboundMapBinding;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.model.DependencyRequest;
import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;
import dagger.internal.codegen.xprocessing.XTypeNames;

/** A factory creation expression for a multibound map. */
final class MapFactoryCreationExpression extends MultibindingFactoryCreationExpression {

  private final XProcessingEnv processingEnv;
  private final ComponentImplementation componentImplementation;
  private final ShardImplementation shardImplementation;
  private final CompilerOptions compilerOptions;
  private final BindingGraph graph;
  private final MultiboundMapBinding binding;
  private final boolean useLazyClassKey;
  private String methodName;

  @AssistedInject
  MapFactoryCreationExpression(
      @Assisted MultiboundMapBinding binding,
      XProcessingEnv processingEnv,
      ComponentImplementation componentImplementation,
      ComponentRequestRepresentations componentRequestRepresentations,
      CompilerOptions compilerOptions,
      BindingGraph graph) {
    super(binding, componentImplementation, componentRequestRepresentations, compilerOptions);
    this.processingEnv = processingEnv;
    this.binding = checkNotNull(binding);
    this.componentImplementation = componentImplementation;
    this.shardImplementation = componentImplementation.shardImplementation(binding);
    this.compilerOptions = compilerOptions;
    this.graph = graph;
    this.useLazyClassKey = MapKeys.useLazyClassKey(binding, graph);
  }

  @Override
  public XCodeBlock creationExpression() {
    return XCodeBlock.of(
        "%N(%L)", methodName(), parameterNames(shardImplementation.constructorParameters()));
  }

  private String methodName() {
    if (methodName == null) {
      // Have to set methodName field before implementing the method in order to handle recursion.
      methodName =
          shardImplementation.getUniqueMethodName(
              KeyVariableNamer.name(binding.key()) + "Builder");

      UniqueNameSet uniqueNameSet = new UniqueNameSet();
      shardImplementation.constructorParameters().stream()
          .map(XParameterSpec::getName) // SUPPRESS_GET_NAME_CHECK
          .forEach(uniqueNameSet::claim);
      String builderName = uniqueNameSet.getUniqueName("builder");

      XCodeBlock.Builder builderMethodCalls = XCodeBlock.builder();
      for (DependencyRequest dependency : binding.dependencies()) {
        builderMethodCalls.addStatement(
            "%N.put(%L, %L)",
            builderName,
            keyExpression(dependency),
            valueExpression(dependency));
      }

      XFunSpec methodSpec =
          methodBuilder(methodName)
              .addParameters(shardImplementation.constructorParameters())
              // TODO(bcorso): remove once dagger.generatedClassExtendsComponent flag is removed.
              .addModifiers(
                  !shardImplementation.isShardClassPrivate()
                      ? ImmutableSet.of(PRIVATE)
                      : ImmutableSet.of())
              .returns(useLazyClassKey ? lazyMapFactoryType() : mapFactoryType())
              .addCode(
                  XCodeBlock.builder()
                      .addStatement(
                          "%T %N = %T.builder(%L)",
                          mapFactoryBuilderType(),
                          builderName,
                          mapFactoryClassName(binding),
                          binding.dependencies().size())
                      .add(builderMethodCalls.build())
                      .addStatement(
                          "%L",
                          useLazyClassKey
                              ? XCodeBlock.of(
                                  "return %T.of(%N.build())",
                                  lazyMapFactoryClassName(binding),
                                  builderName)
                              : XCodeBlock.of("return %N.build()", builderName))
                      .build())
              .build();

      shardImplementation.addMethod(INITIALIZE_HELPER_METHOD, methodSpec);
    }
    return methodName;
  }

  private XTypeName lazyMapFactoryType() {
    return useRawType()
        ? lazyMapFactoryClassName(binding)
        : lazyMapFactoryClassName(binding).parametrizedBy(valueTypeName());
  }

  private XTypeName mapFactoryType() {
    return useRawType()
        ? mapFactoryClassName(binding)
        : mapFactoryClassName(binding).parametrizedBy(keyTypeName(), valueTypeName());
  }

  private XTypeName mapFactoryBuilderType() {
    return useRawType()
        ? mapFactoryClassName(binding).nestedClass("Builder")
        : mapFactoryClassName(binding)
            .nestedClass("Builder")
            .parametrizedBy(keyTypeName(), valueTypeName());
  }

  private XTypeName keyTypeName() {
    if (useLazyClassKey) {
      return XTypeName.STRING;
    }
    return MapType.from(binding.key()).keyType().asTypeName();
  }

  private XTypeName valueTypeName() {
    return MapType.from(binding.key()).unwrappedFrameworkValueType().asTypeName();
  }

  private XCodeBlock keyExpression(DependencyRequest dependency) {
    ContributionBinding contributionBinding = graph.contributionBinding(dependency.key());
    return useLazyClassKey
        ? getLazyClassMapKeyExpression(graph.contributionBinding(dependency.key()))
        : getMapKeyExpression(contributionBinding, componentImplementation.name(), processingEnv);
  }

  private XCodeBlock valueExpression(DependencyRequest dependency) {
    XCodeBlock valueExpression = multibindingDependencyExpression(dependency);
    return valueExpression;
  }

  private static XClassName lazyMapFactoryClassName(MultiboundMapBinding binding) {
    MapType mapType = MapType.from(binding.key());
    switch (binding.bindingType()) {
      case PROVISION:
        return mapType.valuesAreProvider()
            ? XTypeNames.LAZY_CLASS_KEY_MAP_PROVIDER_FACTORY
            : XTypeNames.LAZY_CLASS_KEY_MAP_FACTORY;
      case PRODUCTION:
        return mapType.valuesAreFrameworkType()
            ? mapType.valuesAreTypeOf(XTypeNames.PRODUCER)
                ? XTypeNames.LAZY_MAP_OF_PRODUCER_PRODUCER
                : XTypeNames.LAZY_MAP_OF_PRODUCED_PRODUCER
            : XTypeNames.LAZY_MAP_PRODUCER;
      default:
        throw new IllegalArgumentException(binding.bindingType().toString());
    }
  }

  @AssistedFactory
  static interface Factory {
    MapFactoryCreationExpression create(MultiboundMapBinding binding);
  }
}

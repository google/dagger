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
import static dagger.internal.codegen.binding.SourceFiles.setFactoryClassName;
import static dagger.internal.codegen.writing.ComponentImplementation.MethodSpecKind.INITIALIZE_HELPER_METHOD;
import static dagger.internal.codegen.xprocessing.XCodeBlocks.parameterNames;
import static dagger.internal.codegen.xprocessing.XFunSpecs.methodBuilder;
import static javax.lang.model.element.Modifier.PRIVATE;

import androidx.room.compiler.codegen.XCodeBlock;
import androidx.room.compiler.codegen.XFunSpec;
import androidx.room.compiler.codegen.XParameterSpec;
import androidx.room.compiler.codegen.XTypeName;
import com.google.common.collect.ImmutableSet;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.base.SetType;
import dagger.internal.codegen.base.UniqueNameSet;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.BindingType;
import dagger.internal.codegen.binding.KeyVariableNamer;
import dagger.internal.codegen.binding.MultiboundSetBinding;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.model.DependencyRequest;
import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;
import dagger.internal.codegen.xprocessing.XTypeNames;

/** A factory creation expression for a multibound set. */
final class SetFactoryCreationExpression extends MultibindingFactoryCreationExpression {
  private final CompilerOptions compilerOptions;
  private final BindingGraph graph;
  private final MultiboundSetBinding binding;
  private final ShardImplementation shardImplementation;
  private String methodName;

  @AssistedInject
  SetFactoryCreationExpression(
      @Assisted MultiboundSetBinding binding,
      ComponentImplementation componentImplementation,
      ComponentRequestRepresentations componentRequestRepresentations,
      CompilerOptions compilerOptions,
      BindingGraph graph) {
    super(binding, componentImplementation, componentRequestRepresentations, compilerOptions);
    this.binding = checkNotNull(binding);
    this.shardImplementation = componentImplementation.shardImplementation(binding);
    this.compilerOptions = compilerOptions;
    this.graph = graph;
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

      int individualProviders = 0;
      int setProviders = 0;
      XCodeBlock.Builder builderMethodCalls = XCodeBlock.builder();
      String methodNameSuffix =
          binding.bindingType().equals(BindingType.PROVISION) ? "Provider" : "Producer";

      for (DependencyRequest dependency : binding.dependencies()) {
        String methodNamePrefix;
        XCodeBlock dependencyExpression = multibindingDependencyExpression(dependency);
        switch (graph.contributionBinding(dependency.key()).contributionType()) {
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

        builderMethodCalls.addStatement(
            "%N.%N%N(%L)",
            builderName,
            methodNamePrefix,
            methodNameSuffix,
            dependencyExpression);
      }

      XFunSpec methodSpec =
          methodBuilder(methodName)
              .addParameters(shardImplementation.constructorParameters())
              // TODO(bcorso): remove once dagger.generatedClassExtendsComponent flag is removed.
              .addModifiers(
                  !shardImplementation.isShardClassPrivate()
                      ? ImmutableSet.of(PRIVATE)
                      : ImmutableSet.of())
              .returns(setFactoryType())
              .addCode(
                  XCodeBlock.builder()
                      .addStatement(
                          "%T %N = %T.builder(%L, %L)",
                          setFactoryBuilderType(),
                          builderName,
                          setFactoryClassName(binding),
                          individualProviders,
                          setProviders)
                      .add(builderMethodCalls.build())
                      .addStatement("return %N.build()", builderName)
                      .build())
              .build();

      shardImplementation.addMethod(INITIALIZE_HELPER_METHOD, methodSpec);
    }
    return methodName;
  }

  private XTypeName setFactoryType() {
    return useRawType()
        ? setFactoryClassName(binding)
        : setFactoryClassName(binding).parametrizedBy(valueTypeName());
  }

  private XTypeName setFactoryBuilderType() {
    return useRawType()
        ? setFactoryClassName(binding).nestedClass("Builder")
        : setFactoryClassName(binding)
            .nestedClass("Builder")
            .parametrizedBy(valueTypeName());
  }

  private XTypeName valueTypeName() {
    SetType setType = SetType.from(binding.key());
    return setType.elementsAreTypeOf(XTypeNames.PRODUCED)
        ? setType.unwrappedElementType(XTypeNames.PRODUCED).asTypeName()
        : setType.elementType().asTypeName();
  }

  @AssistedFactory
  static interface Factory {
    SetFactoryCreationExpression create(MultiboundSetBinding binding);
  }
}

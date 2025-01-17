/*
 * Copyright (C) 2018 The Dagger Authors.
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
import static com.google.common.collect.Iterables.getLast;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.javapoet.AnnotationSpecs.Suppression.UNCHECKED;
import static dagger.internal.codegen.javapoet.AnnotationSpecs.suppressWarnings;
import static dagger.internal.codegen.javapoet.TypeNames.daggerProviderOf;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import androidx.room.compiler.processing.XProcessingEnv;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.javapoet.CodeBlocks;
import dagger.internal.codegen.model.BindingKind;
import dagger.internal.codegen.model.Key;
import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;
import dagger.internal.codegen.writing.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;
import dagger.internal.codegen.xprocessing.XProcessingEnvs;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Keeps track of all provider expression requests for a component.
 *
 * <p>The provider expression request will be satisfied by a single generated {@code Provider} class
 * that can provide instances for all types by switching on an id.
 */
final class SwitchingProviders {
  /**
   * Each switch size is fixed at 100 cases each and put in its own method. This is to limit the
   * size of the methods so that we don't reach the "huge" method size limit for Android that will
   * prevent it from being AOT compiled in some versions of Android (b/77652521). This generally
   * starts to happen around 1500 cases, but we are choosing 100 to be safe.
   */
  // TODO(bcorso): Include a proguard_spec in the Dagger library to prevent inlining these methods?
  // TODO(ronshapiro): Consider making this configurable via a flag.
  private static final int MAX_CASES_PER_SWITCH = 100;

  private static final long MAX_CASES_PER_CLASS = MAX_CASES_PER_SWITCH * MAX_CASES_PER_SWITCH;
  private static final TypeVariableName T = TypeVariableName.get("T");

  /**
   * Maps a {@link Key} to an instance of a {@link SwitchingProviderBuilder}. Each group of {@code
   * MAX_CASES_PER_CLASS} keys will share the same instance.
   */
  private final Map<Key, SwitchingProviderBuilder> switchingProviderBuilders =
      new LinkedHashMap<>();

  private final ShardImplementation shardImplementation;
  private final XProcessingEnv processingEnv;

  SwitchingProviders(ShardImplementation shardImplementation, XProcessingEnv processingEnv) {
    this.shardImplementation = checkNotNull(shardImplementation);
    this.processingEnv = checkNotNull(processingEnv);
  }

  /** Returns the framework instance creation expression for an inner switching provider class. */
  FrameworkInstanceCreationExpression newFrameworkInstanceCreationExpression(
      ContributionBinding binding, RequestRepresentation unscopedInstanceRequestRepresentation) {
    return new FrameworkInstanceCreationExpression() {
      @Override
      public CodeBlock creationExpression() {
        return switchingProviderBuilders
            .computeIfAbsent(binding.key(), key -> getSwitchingProviderBuilder())
            .getNewInstanceCodeBlock(binding, unscopedInstanceRequestRepresentation);
      }
    };
  }

  private SwitchingProviderBuilder getSwitchingProviderBuilder() {
    if (switchingProviderBuilders.size() % MAX_CASES_PER_CLASS == 0) {
      String name = shardImplementation.getUniqueClassName("SwitchingProvider");
      SwitchingProviderBuilder switchingProviderBuilder =
          new SwitchingProviderBuilder(shardImplementation.name().nestedClass(name));
      shardImplementation.addTypeSupplier(switchingProviderBuilder::build);
      return switchingProviderBuilder;
    }
    return getLast(switchingProviderBuilders.values());
  }

  // TODO(bcorso): Consider just merging this class with SwitchingProviders.
  private final class SwitchingProviderBuilder {
    // Keep the switch cases ordered by switch id. The switch Ids are assigned in pre-order
    // traversal, but the switch cases are assigned in post-order traversal of the binding graph.
    private final Map<Integer, CodeBlock> switchCases = new TreeMap<>();
    private final Map<Key, Integer> switchIds = new HashMap<>();
    private final ClassName switchingProviderType;

    SwitchingProviderBuilder(ClassName switchingProviderType) {
      this.switchingProviderType = checkNotNull(switchingProviderType);
    }

    private CodeBlock getNewInstanceCodeBlock(
        ContributionBinding binding, RequestRepresentation unscopedInstanceRequestRepresentation) {
      Key key = binding.key();
      if (!switchIds.containsKey(key)) {
        int switchId = switchIds.size();
        switchIds.put(key, switchId);
        switchCases.put(
            switchId, createSwitchCaseCodeBlock(key, unscopedInstanceRequestRepresentation));
      }
      return CodeBlock.of(
          "new $T<$L>($L, $L)",
          switchingProviderType,
          // Add the type parameter explicitly when the binding is scoped because Java can't resolve
          // the type when wrapped. For example, the following will error:
          //   fooProvider = DoubleCheck.provider(new SwitchingProvider<>(1));
          (binding.scope().isPresent()
              || binding.kind().equals(BindingKind.ASSISTED_FACTORY)
              || XProcessingEnvs.isPreJava8SourceVersion(processingEnv))
              ? CodeBlock.of(
                  "$T", shardImplementation.accessibleTypeName(binding.contributedType()))
              : "",
          shardImplementation.componentFieldsByImplementation().values().stream()
              .map(field -> CodeBlock.of("$N", field))
              .collect(CodeBlocks.toParametersCodeBlock()),
          switchIds.get(key));
    }

    private CodeBlock createSwitchCaseCodeBlock(
        Key key, RequestRepresentation unscopedInstanceRequestRepresentation) {
      // TODO(bcorso): Try to delay calling getDependencyExpression() until we are writing out the
      // SwitchingProvider because calling it here makes FrameworkFieldInitializer think there's a
      // cycle when initializing SwitchingProviders which adds an uncessary DelegateFactory.
      CodeBlock instanceCodeBlock =
          unscopedInstanceRequestRepresentation
              .getDependencyExpression(switchingProviderType)
              .box()
              .codeBlock();

      return CodeBlock.builder()
          // TODO(bcorso): Is there something else more useful than the key?
          .add("case $L: // $L\n", switchIds.get(key), key)
          .addStatement("return ($T) $L", T, instanceCodeBlock)
          .build();
    }

    private TypeSpec build() {
      TypeSpec.Builder builder =
          classBuilder(switchingProviderType)
              .addModifiers(PRIVATE, FINAL, STATIC)
              .addTypeVariable(T)
              .addSuperinterface(daggerProviderOf(T))
              .addMethods(getMethods());

      // The SwitchingProvider constructor lists all component parameters first and switch id last.
      MethodSpec.Builder constructor = MethodSpec.constructorBuilder();
      shardImplementation
          .componentFieldsByImplementation()
          .values()
          .forEach(
              field -> {
                builder.addField(field);
                constructor.addParameter(field.type, field.name);
                constructor.addStatement("this.$1N = $1N", field);
              });
      builder.addField(TypeName.INT, "id", PRIVATE, FINAL);
      constructor.addParameter(TypeName.INT, "id").addStatement("this.id = id");

      return builder.addMethod(constructor.build()).build();
    }

    private ImmutableList<MethodSpec> getMethods() {
      ImmutableList<CodeBlock> switchCodeBlockPartitions = switchCodeBlockPartitions();
      if (switchCodeBlockPartitions.size() == 1) {
        // There are less than MAX_CASES_PER_SWITCH cases, so no need for extra get methods.
        return ImmutableList.of(
            methodBuilder("get")
                .addModifiers(PUBLIC)
                .addAnnotation(suppressWarnings(UNCHECKED))
                .addAnnotation(Override.class)
                .returns(T)
                .addCode(getOnlyElement(switchCodeBlockPartitions))
                .build());
      }

      // This is the main public "get" method that will route to private getter methods.
      MethodSpec.Builder routerMethod =
          methodBuilder("get")
              .addModifiers(PUBLIC)
              .addAnnotation(Override.class)
              .returns(T)
              .beginControlFlow("switch (id / $L)", MAX_CASES_PER_SWITCH);

      ImmutableList.Builder<MethodSpec> getMethods = ImmutableList.builder();
      for (int i = 0; i < switchCodeBlockPartitions.size(); i++) {
        MethodSpec method =
            methodBuilder("get" + i)
                .addModifiers(PRIVATE)
                .addAnnotation(suppressWarnings(UNCHECKED))
                .returns(T)
                .addCode(switchCodeBlockPartitions.get(i))
                .build();
        getMethods.add(method);
        routerMethod.addStatement("case $L: return $N()", i, method);
      }

      routerMethod.addStatement("default: throw new $T(id)", AssertionError.class).endControlFlow();

      return getMethods.add(routerMethod.build()).build();
    }

    private ImmutableList<CodeBlock> switchCodeBlockPartitions() {
      return Lists.partition(ImmutableList.copyOf(switchCases.values()), MAX_CASES_PER_SWITCH)
          .stream()
          .map(
              partitionCases ->
                  CodeBlock.builder()
                      .beginControlFlow("switch (id)")
                      .add(CodeBlocks.concat(partitionCases))
                      .addStatement("default: throw new $T(id)", AssertionError.class)
                      .endControlFlow()
                      .build())
          .collect(toImmutableList());
    }
  }
}

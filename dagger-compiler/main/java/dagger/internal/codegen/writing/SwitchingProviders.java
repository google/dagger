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
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.xprocessing.XAnnotationSpecs.Suppression.UNCHECKED;
import static dagger.internal.codegen.xprocessing.XAnnotationSpecs.suppressWarnings;
import static dagger.internal.codegen.xprocessing.XCodeBlocks.concat;
import static dagger.internal.codegen.xprocessing.XCodeBlocks.toParametersCodeBlock;
import static dagger.internal.codegen.xprocessing.XFunSpecs.constructorBuilder;
import static dagger.internal.codegen.xprocessing.XFunSpecs.methodBuilder;
import static dagger.internal.codegen.xprocessing.XTypeNames.daggerProviderOf;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XCodeBlock;
import androidx.room.compiler.codegen.XFunSpec;
import androidx.room.compiler.codegen.XTypeName;
import androidx.room.compiler.codegen.XTypeSpec;
import androidx.room.compiler.processing.XProcessingEnv;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.model.BindingKind;
import dagger.internal.codegen.model.Key;
import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;
import dagger.internal.codegen.writing.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;
import dagger.internal.codegen.xprocessing.XFunSpecs;
import dagger.internal.codegen.xprocessing.XProcessingEnvs;
import dagger.internal.codegen.xprocessing.XPropertySpecs;
import dagger.internal.codegen.xprocessing.XTypeNames;
import dagger.internal.codegen.xprocessing.XTypeSpecs;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

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
  private static final XTypeName typeVariable = XTypeNames.getTypeVariableName("T");

  /**
   * Maps a {@link Key} to an instance of a {@link SwitchingProviderBuilder}. Each group of {@code
   * MAX_CASES_PER_CLASS} keys will share the same instance.
   */
  private final Map<Key, SwitchingProviderBuilder> switchingProviderBuilders =
      new LinkedHashMap<>();

  private final ShardImplementation shardImplementation;
  private final CompilerOptions compilerOptions;
  private final XProcessingEnv processingEnv;

  SwitchingProviders(
      ShardImplementation shardImplementation,
      CompilerOptions compilerOptions,
      XProcessingEnv processingEnv) {
    this.shardImplementation = checkNotNull(shardImplementation);
    this.compilerOptions = checkNotNull(compilerOptions);
    this.processingEnv = checkNotNull(processingEnv);
  }

  /** Returns the framework instance creation expression for an inner switching provider class. */
  FrameworkInstanceCreationExpression newFrameworkInstanceCreationExpression(
      ContributionBinding binding, RequestRepresentation unscopedInstanceRequestRepresentation) {
    return new FrameworkInstanceCreationExpression() {
      @Override
      public XCodeBlock creationExpression() {
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
    private final Map<Integer, XCodeBlock> switchCases = new TreeMap<>();
    private final Map<Key, Integer> switchIds = new HashMap<>();
    private final XClassName switchingProviderType;

    SwitchingProviderBuilder(XClassName switchingProviderType) {
      this.switchingProviderType = checkNotNull(switchingProviderType);
    }

    private XCodeBlock getNewInstanceCodeBlock(
        ContributionBinding binding, RequestRepresentation unscopedInstanceRequestRepresentation) {
      Key key = binding.key();
      if (!switchIds.containsKey(key)) {
        int switchId = switchIds.size();
        switchIds.put(key, switchId);
        switchCases.put(
            switchId, createSwitchCaseCodeBlock(key, unscopedInstanceRequestRepresentation));
      }
      return XCodeBlock.of(
          "new %T<%L>(%L, %L)",
          switchingProviderType,
          maybeTypeParameter(binding),
          shardImplementation.componentFieldsByImplementation().values().stream()
              .map(field -> XCodeBlock.of("%N", field))
              .collect(toParametersCodeBlock()),
          switchIds.get(key));
    }

    private XCodeBlock maybeTypeParameter(ContributionBinding binding) {
      // Add the type parameter explicitly when the binding is scoped because Java can't
      // resolve the type when wrapped. For example, the following will error:
      //   fooProvider = DoubleCheck.provider(new SwitchingProvider<>(1));
      return (binding.scope().isPresent()
              || binding.kind().equals(BindingKind.ASSISTED_FACTORY)
              || XProcessingEnvs.isPreJava8SourceVersion(processingEnv))
          ? XCodeBlock.of("%T", shardImplementation.accessibleTypeName(binding.contributedType()))
          : XCodeBlock.of("");
    }

    private XCodeBlock createSwitchCaseCodeBlock(
        Key key, RequestRepresentation unscopedInstanceRequestRepresentation) {
      // TODO(bcorso): Try to delay calling getDependencyExpression() until we are writing out the
      // SwitchingProvider because calling it here makes FrameworkFieldInitializer think there's a
      // cycle when initializing SwitchingProviders which adds an uncessary DelegateFactory.
      XCodeBlock instanceCodeBlock =
          unscopedInstanceRequestRepresentation
              .getDependencyExpression(switchingProviderType)
              .box()
              .codeBlock();

      return XCodeBlock.builder()
          // TODO(bcorso): Is there something else more useful than the key?
          .add("case %L: // %L\n", switchIds.get(key), key)
          .addStatement("return (%T) %L", typeVariable, instanceCodeBlock)
          .build();
    }

    private XTypeSpec build() {
      XTypeSpecs.Builder builder =
          XTypeSpecs.classBuilder(switchingProviderType)
              .addModifiers(PRIVATE, FINAL, STATIC)
              .addTypeVariable(typeVariable)
              .addSuperinterface(daggerProviderOf(typeVariable))
              .addFunctions(getMethods());

      // The SwitchingProvider constructor lists all component parameters first and switch id last.
      XFunSpecs.Builder constructor = constructorBuilder();
      Stream.concat(
              shardImplementation.componentFieldsByImplementation().values().stream(),
              Stream.of(XPropertySpecs.of("id", XTypeName.PRIMITIVE_INT, PRIVATE, FINAL)))
          .forEach(
              field -> {
                builder.addProperty(field);
                constructor.addParameter(field.getName(), field.getType()); // SUPPRESS_GET_NAME_CHECK
                constructor.addStatement("this.%1N = %1N", field);
              });

      return builder.addFunction(constructor.build()).build();
    }

    private ImmutableList<XFunSpec> getMethods() {
      ImmutableList<XCodeBlock> switchCodeBlockPartitions = switchCodeBlockPartitions();
      if (switchCodeBlockPartitions.size() == 1) {
        // The case amount does not exceed MAX_CASES_PER_SWITCH, so no need for extra get methods.
        return ImmutableList.of(
            methodBuilder("get")
                .isOverride(true)
                .addModifiers(PUBLIC)
                .addAnnotation(suppressWarnings(UNCHECKED))
                .returns(typeVariable)
                .addCode(getOnlyElement(switchCodeBlockPartitions))
                .build());
      }

      // This is the main public "get" method that will route to private getter methods.
      XFunSpecs.Builder routerMethod =
          methodBuilder("get")
              .isOverride(true)
              .addModifiers(PUBLIC)
              .returns(typeVariable)
              .beginControlFlow("switch (id / %L)", MAX_CASES_PER_SWITCH);

      ImmutableList.Builder<XFunSpec> getMethods = ImmutableList.builder();
      for (int i = 0; i < switchCodeBlockPartitions.size(); i++) {
        XFunSpec method =
            methodBuilder("get" + i)
                .addModifiers(PRIVATE)
                .addAnnotation(suppressWarnings(UNCHECKED))
                .returns(typeVariable)
                .addCode(switchCodeBlockPartitions.get(i))
                .build();
        getMethods.add(method);
        routerMethod.addStatement("case %L: return %N()", i, method);
      }

      routerMethod
          .addStatement(
              "default: throw %L", XCodeBlock.ofNewInstance(XTypeNames.ASSERTION_ERROR, "id"))
          .endControlFlow();

      return getMethods.add(routerMethod.build()).build();
    }

    private ImmutableList<XCodeBlock> switchCodeBlockPartitions() {
      return Lists.partition(ImmutableList.copyOf(switchCases.values()), MAX_CASES_PER_SWITCH)
          .stream()
          .map(
              partitionCases ->
                  XCodeBlock.builder()
                      .beginControlFlow("switch (id)")
                      .add(concat(partitionCases))
                      .addStatement(
                          "default: throw %L",
                          XCodeBlock.ofNewInstance(XTypeNames.ASSERTION_ERROR, "id"))
                      .endControlFlow()
                      .build())
          .collect(toImmutableList());
    }
  }
}

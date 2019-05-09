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

package dagger.internal.codegen;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.getLast;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static dagger.internal.codegen.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.javapoet.AnnotationSpecs.Suppression.UNCHECKED;
import static dagger.internal.codegen.javapoet.AnnotationSpecs.suppressWarnings;
import static dagger.internal.codegen.javapoet.TypeNames.providerOf;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import dagger.internal.codegen.javapoet.CodeBlocks;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.Key;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Keeps track of all provider expression requests for a component.
 *
 * <p>The provider expression request will be satisfied by a single generated {@code Provider} inner
 * class that can provide instances for all types by switching on an id.
 */
// TODO(ronshapiro): either merge this with InnerSwitchingProviders, or repurpose this for
// SwitchingProducers
abstract class SwitchingProviders {
  /**
   * Defines the {@linkplain Expression expressions} for a switch case in a {@code SwitchProvider}
   * for a particular binding.
   */
  // TODO(user): Consider handling SwitchingProviders with dependency arguments in this class,
  // then we wouldn't need the getProviderExpression method.
  // TODO(user): Consider making this an abstract class with equals/hashCode defined by the key
  // and then using this class directly in Map types instead of Key.
  interface SwitchCase {
    /** Returns the {@link Key} for this switch case. */
    Key key();

    /** Returns the {@link Expression} that returns the provided instance for this case. */
    Expression getReturnExpression(ClassName switchingProviderClass);

    /**
     * Returns the {@link Expression} that returns the {@code SwitchProvider} instance for this
     * case.
     */
    Expression getProviderExpression(ClassName switchingProviderClass, int switchId);
  }

  /**
   * Each switch size is fixed at 100 cases each and put in its own method. This is to limit the
   * size of the methods so that we don't reach the "huge" method size limit for Android that will
   * prevent it from being AOT compiled in some versions of Android (b/77652521). This generally
   * starts to happen around 1500 cases, but we are choosing 100 to be safe.
   */
  // TODO(user): Include a proguard_spec in the Dagger library to prevent inlining these methods?
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

  private final ComponentImplementation componentImplementation;
  private final ClassName owningComponent;
  private final DaggerTypes types;
  private final UniqueNameSet switchingProviderNames = new UniqueNameSet();

  SwitchingProviders(ComponentImplementation componentImplementation, DaggerTypes types) {
    this.componentImplementation = checkNotNull(componentImplementation);
    this.types = checkNotNull(types);
    this.owningComponent = checkNotNull(componentImplementation).name();
  }

  /** Returns the {@link TypeSpec} for a {@code SwitchingProvider} based on the given builder. */
  protected abstract TypeSpec createSwitchingProviderType(TypeSpec.Builder builder);

  /**
   * Returns the {@link Expression} that returns the {@code SwitchProvider} instance for the case.
   */
  protected final Expression getProviderExpression(SwitchCase switchCase) {
    return switchingProviderBuilders
        .computeIfAbsent(switchCase.key(), key -> getSwitchingProviderBuilder())
        .getProviderExpression(switchCase);
  }

  private SwitchingProviderBuilder getSwitchingProviderBuilder() {
    if (switchingProviderBuilders.size() % MAX_CASES_PER_CLASS == 0) {
      String name = switchingProviderNames.getUniqueName("SwitchingProvider");
      SwitchingProviderBuilder switchingProviderBuilder =
          new SwitchingProviderBuilder(owningComponent.nestedClass(name));
      componentImplementation.addSwitchingProvider(switchingProviderBuilder::build);
      return switchingProviderBuilder;
    }
    return getLast(switchingProviderBuilders.values());
  }

  // TODO(user): Consider just merging this class with SwitchingProviders.
  private final class SwitchingProviderBuilder {
    // Keep the switch cases ordered by switch id. The switch Ids are assigned in pre-order
    // traversal, but the switch cases are assigned in post-order traversal of the binding graph.
    private final Map<Integer, CodeBlock> switchCases = new TreeMap<>();
    private final Map<Key, Integer> switchIds = new HashMap<>();
    private final ClassName switchingProviderType;

    SwitchingProviderBuilder(ClassName switchingProviderType) {
      this.switchingProviderType = checkNotNull(switchingProviderType);
    }

    Expression getProviderExpression(SwitchCase switchCase) {
      Key key = switchCase.key();
      if (!switchIds.containsKey(key)) {
        int switchId = switchIds.size();
        switchIds.put(key, switchId);
        switchCases.put(switchId, createSwitchCaseCodeBlock(switchCase));
      }
      return switchCase.getProviderExpression(switchingProviderType, switchIds.get(key));
    }

    private CodeBlock createSwitchCaseCodeBlock(SwitchCase switchCase) {
      CodeBlock instanceCodeBlock =
          switchCase.getReturnExpression(switchingProviderType).box(types).codeBlock();

      return CodeBlock.builder()
          // TODO(user): Is there something else more useful than the key?
          .add("case $L: // $L \n", switchIds.get(switchCase.key()), switchCase.key())
          .addStatement("return ($T) $L", T, instanceCodeBlock)
          .build();
    }

    private TypeSpec build() {
      return createSwitchingProviderType(
          classBuilder(switchingProviderType)
              .addTypeVariable(T)
              .addSuperinterface(providerOf(T))
              .addMethods(getMethods()));
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

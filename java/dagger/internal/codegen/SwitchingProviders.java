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
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static dagger.internal.codegen.AnnotationSpecs.Suppression.UNCHECKED;
import static dagger.internal.codegen.AnnotationSpecs.suppressWarnings;
import static dagger.internal.codegen.CodeBlocks.toConcatenatedCodeBlock;
import static dagger.internal.codegen.TypeNames.providerOf;
import static dagger.model.RequestKind.INSTANCE;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import dagger.model.Key;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import javax.inject.Provider;

/**
 * Keeps track of all provider expression requests for a component.
 *
 * <p>The provider expression request will be satisfied by a single generated {@code Provider} inner
 * class that can provide instances for all types by switching on an id.
 */
final class SwitchingProviders {
  private static final TypeVariableName T = TypeVariableName.get("T");

  // Keep the switch cases ordered by switch id.
  private final Map<Integer, CodeBlock> switchCases = new TreeMap<>();
  private final Map<Key, Integer> switchIds = new HashMap<>();

  private final ComponentBindingExpressions componentBindingExpressions;
  private final BindingGraph graph;
  private final GeneratedComponentModel generatedComponentModel;
  private final ClassName owningComponent;
  private final ClassName switchingProviderType;
  private final DaggerTypes types;

  SwitchingProviders(
      GeneratedComponentModel generatedComponentModel,
      ComponentBindingExpressions componentBindingExpressions,
      BindingGraph graph,
      DaggerTypes types) {
    this.generatedComponentModel = checkNotNull(generatedComponentModel);
    this.componentBindingExpressions = checkNotNull(componentBindingExpressions);
    this.graph = checkNotNull(graph);
    this.types = checkNotNull(types);
    this.owningComponent = checkNotNull(generatedComponentModel).name();
    this.switchingProviderType = owningComponent.nestedClass("SwitchingProvider");
  }

  /**
   * Returns the binding expression for a binding that satisfies its {link Provider} requests with
   * the generated {@code SwitchingProvider}.
   */
  BindingExpression newBindingExpression(Key key) {
    return new BindingExpression() {
      @Override
      Expression getDependencyExpression(ClassName requestingClass) {
        if (!switchIds.containsKey(key)) {
          // Register the SwitchingProvider creation method the first time it's requested.
          if (switchIds.isEmpty()) {
            generatedComponentModel.addSwitchingProvider(
                SwitchingProviders.this::createSwitchingProviderType);
          }

          int switchId = switchIds.size();
          switchIds.put(key, switchId);
          switchCases.put(switchId, createSwitchCaseCodeBlock(key));
        }

        return Expression.create(
            types.wrapType(key.type(), Provider.class),
            CodeBlock.of("new $T<>($L)", switchingProviderType, switchIds.get(key)));
      }
    };
  }

  private CodeBlock createSwitchCaseCodeBlock(Key key) {
    CodeBlock instanceCodeBlock =
        componentBindingExpressions
            .getDependencyExpression(key, INSTANCE, owningComponent)
            .box(types)
            .codeBlock();

    return CodeBlock.builder()
        // TODO(user): Is there something else more useful than the key?
        .add("case $L: // $L \n", switchIds.get(key), key)
        .addStatement("return ($T) $L", T, instanceCodeBlock)
        .build();
  }

  private TypeSpec createSwitchingProviderType() {
    return classBuilder(switchingProviderType)
        .addModifiers(PRIVATE, FINAL)
        .addTypeVariable(T)
        .addSuperinterface(providerOf(T))
        .addField(TypeName.INT, "id", PRIVATE, FINAL)
        .addMethod(
            constructorBuilder()
                .addParameter(TypeName.INT, "id")
                .addStatement("this.id = id")
                .build())
        .addMethod(
            methodBuilder("get")
                .addModifiers(PUBLIC)
                .addAnnotation(suppressWarnings(UNCHECKED))
                .addAnnotation(Override.class)
                .returns(T)
                .beginControlFlow("switch (id)")
                .addCode(switchCases.values().stream().collect(toConcatenatedCodeBlock()))
                .addStatement("default: throw new $T(id)", AssertionError.class)
                .endControlFlow()
                .build())
        .build();
  }
}

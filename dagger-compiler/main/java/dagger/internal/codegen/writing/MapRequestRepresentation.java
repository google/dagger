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

import static androidx.room.compiler.codegen.compat.XConverters.toJavaPoet;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.binding.BindingRequest.bindingRequest;
import static dagger.internal.codegen.binding.MapKeys.getLazyClassMapKeyExpression;
import static dagger.internal.codegen.binding.MapKeys.getMapKeyExpression;
import static dagger.internal.codegen.javapoet.CodeBlocks.toParametersCodeBlock;
import static dagger.internal.codegen.langmodel.Accessibility.isTypeAccessibleFrom;
import static dagger.internal.codegen.model.BindingKind.MULTIBOUND_MAP;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XTypeName;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.squareup.javapoet.CodeBlock;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.MapBuilder;
import dagger.internal.codegen.base.MapType;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.binding.MapKeys;
import dagger.internal.codegen.binding.MultiboundMapBinding;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.model.BindingKind;
import dagger.internal.codegen.model.DependencyRequest;
import dagger.internal.codegen.xprocessing.XTypeNames;
import java.util.Collections;

/** A {@link RequestRepresentation} for multibound maps. */
final class MapRequestRepresentation extends RequestRepresentation {
  /** Maximum number of key-value pairs that can be passed to ImmutableMap.of(K, V, K, V, ...). */
  private static final int MAX_IMMUTABLE_MAP_OF_KEY_VALUE_PAIRS = 5;

  private final XProcessingEnv processingEnv;
  private final MultiboundMapBinding binding;
  private final ImmutableMap<DependencyRequest, ContributionBinding> dependencies;
  private final ComponentRequestRepresentations componentRequestRepresentations;
  private final boolean useLazyClassKey;

  @AssistedInject
  MapRequestRepresentation(
      @Assisted MultiboundMapBinding binding,
      XProcessingEnv processingEnv,
      BindingGraph graph,
      ComponentImplementation componentImplementation,
      ComponentRequestRepresentations componentRequestRepresentations) {
    this.binding = binding;
    this.processingEnv = processingEnv;
    BindingKind bindingKind = this.binding.kind();
    checkArgument(bindingKind.equals(MULTIBOUND_MAP), bindingKind);
    this.componentRequestRepresentations = componentRequestRepresentations;
    this.dependencies =
        Maps.toMap(binding.dependencies(), dep -> graph.contributionBinding(dep.key()));
    this.useLazyClassKey = MapKeys.useLazyClassKey(binding, graph);
  }

  @Override
  Expression getDependencyExpression(XClassName requestingClass) {
    MapType mapType = MapType.from(binding.key());
    Expression dependencyExpression = getUnderlyingMapExpression(requestingClass);
    // LazyClassKey is backed with a string map, therefore needs to be wrapped.
    if (useLazyClassKey) {
      return Expression.create(
          dependencyExpression.type(),
          CodeBlock.of(
              "$T.<$T>of($L)",
              toJavaPoet(XTypeNames.LAZY_CLASS_KEY_MAP),
              mapType.valueType().getTypeName(),
              dependencyExpression.codeBlock()));
    }
    return dependencyExpression;
  }

  private Expression getUnderlyingMapExpression(XClassName requestingClass) {
    // TODO(ronshapiro): We should also make an ImmutableMap version of MapFactory
    boolean isImmutableMapAvailable = isImmutableMapAvailable();
    // TODO(ronshapiro, gak): Use Maps.immutableEnumMap() if it's available?
    if (isImmutableMapAvailable && dependencies.size() <= MAX_IMMUTABLE_MAP_OF_KEY_VALUE_PAIRS) {
      return Expression.create(
          immutableMapType(),
          CodeBlock.builder()
              .add("$T.", ImmutableMap.class)
              .add(maybeTypeParameters(requestingClass))
              .add(
                  "of($L)",
                  dependencies.keySet().stream()
                      .map(dependency -> keyAndValueExpression(dependency, requestingClass))
                      .collect(toParametersCodeBlock()))
              .build());
    }
    switch (dependencies.size()) {
      case 0:
        return collectionsStaticFactoryInvocation(requestingClass, CodeBlock.of("emptyMap()"));
      case 1:
        return collectionsStaticFactoryInvocation(
            requestingClass,
            CodeBlock.of(
                "singletonMap($L)",
                keyAndValueExpression(getOnlyElement(dependencies.keySet()), requestingClass)));
      default:
        CodeBlock.Builder instantiation =
            CodeBlock.builder()
                .add("$T.", isImmutableMapAvailable ? ImmutableMap.class : MapBuilder.class)
                .add(maybeTypeParameters(requestingClass));
        if (isImmutableMapBuilderWithExpectedSizeAvailable()) {
          instantiation.add("builderWithExpectedSize($L)", dependencies.size());
        } else if (isImmutableMapAvailable) {
          instantiation.add("builder()");
        } else {
          instantiation.add("newMapBuilder($L)", dependencies.size());
        }
        for (DependencyRequest dependency : dependencies.keySet()) {
          instantiation.add(".put($L)", keyAndValueExpression(dependency, requestingClass));
        }
        return Expression.create(
            isImmutableMapAvailable ? immutableMapType() : binding.key().type().xprocessing(),
            instantiation.add(".build()").build());
    }
  }

  private XType immutableMapType() {
    MapType mapType = MapType.from(binding.key());
    return processingEnv.getDeclaredType(
        processingEnv.requireTypeElement(XTypeNames.IMMUTABLE_MAP),
        mapType.keyType(),
        mapType.valueType());
  }

  private CodeBlock keyAndValueExpression(
      DependencyRequest dependency, XClassName requestingClass) {
    return CodeBlock.of(
        "$L, $L",
        useLazyClassKey
            ? getLazyClassMapKeyExpression(dependencies.get(dependency))
            : getMapKeyExpression(dependencies.get(dependency), requestingClass, processingEnv),
        componentRequestRepresentations
            .getDependencyExpression(bindingRequest(dependency), requestingClass)
            .codeBlock());
  }

  private Expression collectionsStaticFactoryInvocation(
      XClassName requestingClass, CodeBlock methodInvocation) {
    return Expression.create(
        binding.key().type().xprocessing(),
        CodeBlock.builder()
            .add("$T.", Collections.class)
            .add(maybeTypeParameters(requestingClass))
            .add(methodInvocation)
            .build());
  }

  private CodeBlock maybeTypeParameters(XClassName requestingClass) {
    XType bindingKeyType = binding.key().type().xprocessing();
    MapType mapType = MapType.from(binding.key());
    return isTypeAccessibleFrom(bindingKeyType, requestingClass.getPackageName())
        ? CodeBlock.of(
            "<$T, $T>",
            toJavaPoet(useLazyClassKey ? XTypeName.STRING : mapType.keyType().asTypeName()),
            mapType.valueType().getTypeName())
        : CodeBlock.of("");
  }

  private boolean isImmutableMapBuilderWithExpectedSizeAvailable() {
    return isImmutableMapAvailable()
        && processingEnv.requireTypeElement(XTypeNames.IMMUTABLE_MAP).getDeclaredMethods().stream()
            .anyMatch(method -> getSimpleName(method).contentEquals("builderWithExpectedSize"));
  }

  private boolean isImmutableMapAvailable() {
    return processingEnv.findTypeElement(XTypeNames.IMMUTABLE_MAP) != null;
  }

  @AssistedFactory
  static interface Factory {
    MapRequestRepresentation create(MultiboundMapBinding binding);
  }
}

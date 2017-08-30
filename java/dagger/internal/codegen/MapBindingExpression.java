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

package dagger.internal.codegen;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.Accessibility.isTypeAccessibleFrom;
import static dagger.internal.codegen.CodeBlocks.toParametersCodeBlock;
import static dagger.internal.codegen.ContributionBinding.Kind.SYNTHETIC_MULTIBOUND_MAP;
import static dagger.internal.codegen.MapKeys.getMapKeyExpression;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import dagger.internal.MapBuilder;
import java.util.Collections;
import java.util.Map;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

/** A {@link BindingExpression} for multibound maps. */
final class MapBindingExpression extends SimpleInvocationBindingExpression {
  /** Maximum number of key-value pairs that can be passed to ImmutableMap.of(K, V, K, V, ...). */
  private static final int MAX_IMMUTABLE_MAP_OF_KEY_VALUE_PAIRS = 5;

  private final ProvisionBinding binding;
  private final ImmutableMap<DependencyRequest, ContributionBinding> dependencies;
  private final ComponentBindingExpressions componentBindingExpressions;
  private final Elements elements;

  MapBindingExpression(
      ProvisionBinding binding,
      BindingGraph graph,
      ComponentBindingExpressions componentBindingExpressions,
      BindingExpression delegate,
      Elements elements) {
    super(delegate);
    ContributionBinding.Kind bindingKind = binding.bindingKind();
    checkArgument(bindingKind.equals(SYNTHETIC_MULTIBOUND_MAP), bindingKind);
    this.binding = binding;
    this.componentBindingExpressions = componentBindingExpressions;
    this.elements = elements;
    this.dependencies =
        Maps.toMap(
            binding.dependencies(),
            dep -> graph.resolvedBindings().get(dep.bindingKey()).contributionBinding());
  }

  @Override
  CodeBlock getInstanceDependencyExpression(
      DependencyRequest.Kind requestKind, ClassName requestingClass) {
    // TODO(ronshapiro): We should also make an ImmutableMap version of MapFactory
    boolean isImmutableMapAvailable = isImmutableMapAvailable();
    // TODO(ronshapiro, gak): Use Maps.immutableEnumMap() if it's available?
    if (isImmutableMapAvailable && dependencies.size() <= MAX_IMMUTABLE_MAP_OF_KEY_VALUE_PAIRS) {
      return CodeBlock.builder()
          .add("$T.", ImmutableMap.class)
          .add(maybeTypeParameters(requestingClass))
          .add(
              "of($L)",
              dependencies
                  .keySet()
                  .stream()
                  .map(dependency -> keyAndValueExpression(dependency, requestingClass))
                  .collect(toParametersCodeBlock()))
          .build();
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
        CodeBlock.Builder instantiation = CodeBlock.builder();
        instantiation
            .add("$T.", isImmutableMapAvailable ? ImmutableMap.class : MapBuilder.class)
            .add(maybeTypeParameters(requestingClass));
        if (isImmutableMapAvailable) {
          // TODO(ronshapiro): builderWithExpectedSize
          instantiation.add("builder()");
        } else {
          instantiation.add("newMapBuilder($L)", dependencies.size());
        }
        for (DependencyRequest dependency : dependencies.keySet()) {
          instantiation.add(".put($L)", keyAndValueExpression(dependency, requestingClass));
        }
        return instantiation.add(".build()").build();
    }
  }

  private CodeBlock keyAndValueExpression(DependencyRequest dependency, ClassName requestingClass) {
    return CodeBlock.of(
        "$L, $L",
        getMapKeyExpression(dependencies.get(dependency).mapKey().get()),
        componentBindingExpressions.getDependencyExpression(dependency, requestingClass));
  }

  private CodeBlock collectionsStaticFactoryInvocation(
      ClassName requestingClass, CodeBlock methodInvocation) {
    return CodeBlock.builder()
        .add("$T.", Collections.class)
        .add(maybeTypeParameters(requestingClass))
        .add(methodInvocation)
        .build();
  }

  private CodeBlock maybeTypeParameters(ClassName requestingClass) {
    TypeMirror bindingKeyType = binding.key().type();
    MapType mapType = MapType.from(binding.key());
    return isTypeAccessibleFrom(bindingKeyType, requestingClass.packageName())
        ? CodeBlock.of("<$T, $T>", mapType.keyType(), mapType.valueType())
        : CodeBlock.of("");
  }

  private boolean isImmutableMapAvailable() {
    return elements.getTypeElement(ImmutableMap.class.getCanonicalName()) != null;
  }

  @Override
  protected CodeBlock explicitTypeParameter(ClassName requestingClass) {
    if (isImmutableMapAvailable()) {
      TypeMirror keyType = binding.key().type();
      return CodeBlock.of(
          "<$T>",
          isTypeAccessibleFrom(keyType, requestingClass.packageName()) ? keyType : Map.class);
    }
    return CodeBlock.of("");
  }
}

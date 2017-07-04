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

import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.Accessibility.isTypeAccessibleFrom;
import static dagger.internal.codegen.CodeBlocks.toParametersCodeBlock;

import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;
import dagger.internal.SetBuilder;
import java.util.Collections;
import java.util.Set;
import java.util.function.Function;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

/**
 * A {@link RequestFulfillment} for {@link
 * dagger.internal.codegen.ContributionBinding.Kind#SYNTHETIC_MULTIBOUND_SET}
 */
final class SetBindingRequestFulfillment extends SimpleInvocationRequestFulfillment {
  private final ProvisionBinding binding;
  private final BindingGraph graph;
  private final HasBindingExpressions hasBindingExpressions;
  private final Elements elements;

  SetBindingRequestFulfillment(
      BindingKey bindingKey,
      ProvisionBinding binding,
      BindingGraph graph,
      HasBindingExpressions hasBindingExpressions,
      RequestFulfillment delegate,
      Elements elements) {
    super(bindingKey, delegate);
    this.binding = binding;
    this.graph = graph;
    this.hasBindingExpressions = hasBindingExpressions;
    this.elements = elements;
  }

  @Override
  CodeBlock getSimpleInvocation(DependencyRequest request, ClassName requestingClass) {
    Function<DependencyRequest, CodeBlock> getRequestFulfillmentForDependency =
        dependency ->
            hasBindingExpressions
                .getBindingExpression(dependency.bindingKey())
                .getSnippetForDependencyRequest(dependency, requestingClass);

    // TODO(ronshapiro): We should also make an ImmutableSet version of SetFactory
    boolean isImmutableSetAvailable = isImmutableSetAvailable();
    // TODO(ronshapiro, gak): Use Sets.immutableEnumSet() if it's available?
    if (isImmutableSetAvailable && binding.dependencies().stream().allMatch(this::isSingleValue)) {
      return CodeBlock.builder()
          .add("$T.", ImmutableSet.class)
          .add(maybeTypeParameter(request, requestingClass))
          .add(
              "of($L)",
              binding
                  .dependencies()
                  .stream()
                  .map(getRequestFulfillmentForDependency)
                  .collect(toParametersCodeBlock()))
          .build();
    }
    switch (binding.dependencies().size()) {
      case 0:
        return collectionsStaticFactoryInvocation(
            request, requestingClass, CodeBlock.of("emptySet()"));
      case 1:
        {
          DependencyRequest dependency = getOnlyElement(binding.dependencies());
          CodeBlock dependencySnippet =
              getRequestFulfillmentForDependency(dependency, requestingClass);
          if (isSingleValue(dependency)) {
            return collectionsStaticFactoryInvocation(
                request, requestingClass, CodeBlock.of("singleton($L)", dependencySnippet));
          } else if (isImmutableSetAvailable) {
            return CodeBlock.builder()
                .add("$T.", ImmutableSet.class)
                .add(maybeTypeParameter(request, requestingClass))
                .add("copyOf($L)", dependencySnippet)
                .build();
          }
        }
        // fall through
      default:
        CodeBlock.Builder instantiation = CodeBlock.builder();
        instantiation
            .add("$T.", isImmutableSetAvailable ? ImmutableSet.class : SetBuilder.class)
            .add(maybeTypeParameter(request, requestingClass));
        if (isImmutableSetAvailable) {
          instantiation.add("builder()");
        } else {
          instantiation.add("newSetBuilder($L)", binding.dependencies().size());
        }
        for (DependencyRequest dependency : binding.dependencies()) {
          String builderMethod = isSingleValue(dependency) ? "add" : "addAll";
          instantiation.add(
              ".$L($L)",
              builderMethod,
              getRequestFulfillmentForDependency(dependency, requestingClass));
        }
        return instantiation.add(".build()").build();
    }
  }

  private CodeBlock getRequestFulfillmentForDependency(
      DependencyRequest dependency, ClassName requestingClass) {
    return hasBindingExpressions
        .getBindingExpression(dependency.bindingKey())
        .getSnippetForDependencyRequest(dependency, requestingClass);
  }

  private static CodeBlock collectionsStaticFactoryInvocation(
      DependencyRequest request, ClassName requestingClass, CodeBlock methodInvocation) {
    return CodeBlock.builder()
        .add("$T.", Collections.class)
        .add(maybeTypeParameter(request, requestingClass))
        .add(methodInvocation)
        .build();
  }

  private static CodeBlock maybeTypeParameter(
      DependencyRequest request, ClassName requestingClass) {
    TypeMirror elementType = SetType.from(request.key()).elementType();
    return isTypeAccessibleFrom(elementType, requestingClass.packageName())
        ? CodeBlock.of("<$T>", elementType)
        : CodeBlock.of("");
  }

  private boolean isSingleValue(DependencyRequest dependency) {
    return graph
        .resolvedBindings()
        .get(dependency.bindingKey())
        .contributionBinding()
        .contributionType()
        .equals(ContributionType.SET);
  }

  private boolean isImmutableSetAvailable() {
    return elements.getTypeElement(ImmutableSet.class.getCanonicalName()) != null;
  }

  @Override
  protected CodeBlock explicitTypeParameter(ClassName requestingClass) {
    if (isImmutableSetAvailable()) {
      TypeMirror keyType = binding.key().type();
      return CodeBlock.of(
          "<$T>",
          isTypeAccessibleFrom(keyType, requestingClass.packageName())
              ? TypeName.get(keyType)
              : ClassName.get(Set.class));
    }
    return CodeBlock.of("");
  }
}

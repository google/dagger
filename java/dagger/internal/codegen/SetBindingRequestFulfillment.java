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
import static com.squareup.javapoet.CodeBlock.of;
import static dagger.internal.codegen.Accessibility.isTypeAccessibleFrom;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import dagger.internal.SetBuilder;
import java.util.Collections;
import javax.lang.model.type.TypeMirror;

/**
 * A {@link RequestFulfillment} for {@link
 * dagger.internal.codegen.ContributionBinding.Kind#SYNTHETIC_MULTIBOUND_SET}
 */
final class SetBindingRequestFulfillment extends SimpleInvocationRequestFulfillment {
  private final ProvisionBinding binding;
  private final BindingGraph graph;
  private final HasBindingExpressions hasBindingExpressions;

  SetBindingRequestFulfillment(
      BindingKey bindingKey,
      ProvisionBinding binding,
      BindingGraph graph,
      HasBindingExpressions hasBindingExpressions,
      RequestFulfillment delegate) {
    super(bindingKey, delegate);
    this.binding = binding;
    this.graph = graph;
    this.hasBindingExpressions = hasBindingExpressions;
  }

  @Override
  CodeBlock getSimpleInvocation(DependencyRequest request, ClassName requestingClass) {
    // TODO(ronshapiro): if you have ImmutableSet on your classpath, use ImmutableSet.Builder
    // otherwise, we can consider providing our own static factories for multibinding cases where
    // all of the dependencies are @IntoSet
    switch (binding.dependencies().size()) {
      case 0:
        return collectionsStaticFactoryInvocation(
            request, requestingClass, CodeBlock.of("emptySet()"));
      case 1:
        {
          DependencyRequest dependency = getOnlyElement(binding.dependencies());
          if (isSingleValue(dependency)) {
            return collectionsStaticFactoryInvocation(
                request,
                requestingClass,
                CodeBlock.of(
                    "singleton($L)",
                    getRequestFulfillmentForDependency(dependency, requestingClass)));
          }
        }
        // fall through
      default:
        CodeBlock.Builder instantiation = CodeBlock.builder();
        instantiation
            .add("$T.", SetBuilder.class)
            .add(maybeTypeParameter(request, requestingClass))
            .add("newSetBuilder($L)", binding.dependencies().size());
        for (DependencyRequest dependency : binding.dependencies()) {
          String builderMethod = isSingleValue(dependency) ? "add" : "addAll";
          instantiation.add(
              ".$L($L)",
              builderMethod,
              getRequestFulfillmentForDependency(dependency, requestingClass));
        }
        return instantiation.add(".create()").build();
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
        ? of("<$T>", elementType)
        : of("");
  }

  private boolean isSingleValue(DependencyRequest dependency) {
    return graph
        .resolvedBindings()
        .get(dependency.bindingKey())
        .contributionBinding()
        .contributionType()
        .equals(ContributionType.SET);
  }
}

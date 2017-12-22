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
import dagger.internal.SetBuilder;
import java.util.Collections;
import java.util.Set;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

/** A binding expression for multibound sets. */
final class SetBindingExpression extends SimpleInvocationBindingExpression {
  private final ProvisionBinding binding;
  private final BindingGraph graph;
  private final ComponentBindingExpressions componentBindingExpressions;
  private final Elements elements;

  SetBindingExpression(
      ProvisionBinding binding,
      BindingGraph graph,
      ComponentBindingExpressions componentBindingExpressions,
      BindingExpression delegate,
      DaggerTypes types,
      Elements elements) {
    super(delegate, types);
    this.binding = binding;
    this.graph = graph;
    this.componentBindingExpressions = componentBindingExpressions;
    this.elements = elements;
  }

  @Override
  Expression getInstanceDependencyExpression(ClassName requestingClass) {
    return Expression.create(binding.key().type(), setExpression(requestingClass));
  }

  private CodeBlock setExpression(ClassName requestingClass) {
    // TODO(ronshapiro): We should also make an ImmutableSet version of SetFactory
    boolean isImmutableSetAvailable = isImmutableSetAvailable();
    // TODO(ronshapiro, gak): Use Sets.immutableEnumSet() if it's available?
    if (isImmutableSetAvailable && binding.dependencies().stream().allMatch(this::isSingleValue)) {
      return CodeBlock.builder()
          .add("$T.", ImmutableSet.class)
          .add(maybeTypeParameter(requestingClass))
          .add(
              "of($L)",
              binding
                  .dependencies()
                  .stream()
                  .map(dependency -> getContributionExpression(dependency, requestingClass))
                  .collect(toParametersCodeBlock()))
          .build();
    }
    switch (binding.dependencies().size()) {
      case 0:
        return collectionsStaticFactoryInvocation(requestingClass, CodeBlock.of("emptySet()"));
      case 1:
        {
          DependencyRequest dependency = getOnlyElement(binding.dependencies());
          CodeBlock contributionExpression = getContributionExpression(dependency, requestingClass);
          if (isSingleValue(dependency)) {
            return collectionsStaticFactoryInvocation(
                requestingClass, CodeBlock.of("singleton($L)", contributionExpression));
          } else if (isImmutableSetAvailable) {
            return CodeBlock.builder()
                .add("$T.", ImmutableSet.class)
                .add(maybeTypeParameter(requestingClass))
                .add("copyOf($L)", contributionExpression)
                .build();
          }
        }
        // fall through
      default:
        CodeBlock.Builder instantiation = CodeBlock.builder();
        instantiation
            .add("$T.", isImmutableSetAvailable ? ImmutableSet.class : SetBuilder.class)
            .add(maybeTypeParameter(requestingClass));
        if (isImmutableSetAvailable) {
          instantiation.add("builder()");
        } else {
          instantiation.add("newSetBuilder($L)", binding.dependencies().size());
        }
        for (DependencyRequest dependency : binding.dependencies()) {
          String builderMethod = isSingleValue(dependency) ? "add" : "addAll";
          instantiation.add(
              ".$L($L)", builderMethod, getContributionExpression(dependency, requestingClass));
        }
        return instantiation.add(".build()").build();
    }
  }

  private CodeBlock getContributionExpression(
      DependencyRequest dependency, ClassName requestingClass) {
    return componentBindingExpressions
        .getDependencyExpression(dependency, requestingClass)
        .codeBlock();
  }

  private CodeBlock collectionsStaticFactoryInvocation(
      ClassName requestingClass, CodeBlock methodInvocation) {
    return CodeBlock.builder()
        .add("$T.", Collections.class)
        .add(maybeTypeParameter(requestingClass))
        .add(methodInvocation)
        .build();
  }

  private CodeBlock maybeTypeParameter(ClassName requestingClass) {
    TypeMirror elementType = SetType.from(binding.key()).elementType();
    return isTypeAccessibleFrom(elementType, requestingClass.packageName())
        ? CodeBlock.of("<$T>", elementType)
        : CodeBlock.of("");
  }

  private boolean isSingleValue(DependencyRequest dependency) {
    return graph
        .contributionBindings()
        .get(dependency.key())
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
          isTypeAccessibleFrom(keyType, requestingClass.packageName()) ? keyType : Set.class);
    }
    return CodeBlock.of("");
  }
}

/*
 * Copyright (C) 2016 The Dagger Authors.
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

import static dagger.internal.codegen.Accessibility.isRawTypeAccessible;
import static dagger.internal.codegen.Accessibility.isTypeAccessibleFrom;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.ClassName;
import java.util.HashMap;
import java.util.Map;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

/** A central repository of code expressions used to access any binding available to a component. */
final class ComponentBindingExpressions {

  // TODO(dpb,ronshapiro): refactor this and ComponentRequirementFields into a
  // HierarchicalComponentMap<K, V>, or perhaps this use a flattened ImmutableMap, built from its
  // parents? If so, maybe make BindingExpression.Factory create it.

  /**
   * A list of binding expression maps. The first element contains the bindings owned by this
   * component; the second contains the bindings owned by its parent; and so on.
   */
  private final ImmutableList<Map<BindingKey, BindingExpression>> bindingExpressionsMaps;
  private final Types types;

  private ComponentBindingExpressions(
      ImmutableList<Map<BindingKey, BindingExpression>> bindingExpressionsMaps, Types types) {
    this.bindingExpressionsMaps = bindingExpressionsMaps;
    this.types = types;
  }

  ComponentBindingExpressions(Types types) {
    this(ImmutableList.of(newBindingExpressionMap()), types);
  }

  /**
   * Returns an expression that evaluates to the value of a dependency request for a binding owned
   * by this component or an ancestor.
   *
   * @param requestingClass the class that will contain the expression
   * @throws IllegalStateException if there is no binding expression that satisfies the dependency
   *     request
   */
  Expression getDependencyExpression(
      BindingKey bindingKey, DependencyRequest.Kind requestKind, ClassName requestingClass) {
    return getBindingExpression(bindingKey).getDependencyExpression(requestKind, requestingClass);
  }

  /**
   * Returns an expression that evaluates to the value of a dependency request for a binding owned
   * by this component or an ancestor.
   *
   * @param requestingClass the class that will contain the expression
   * @throws IllegalStateException if there is no binding expression that satisfies the dependency
   *     request
   */
  Expression getDependencyExpression(DependencyRequest request, ClassName requestingClass) {
    return getDependencyExpression(request.bindingKey(), request.kind(), requestingClass);
  }

  /**
   * Returns an expression that evaluates to the value of a framework dependency for a binding owned
   * in this component or an ancestor.
   *
   * @param requestingClass the class that will contain the expression
   * @throws IllegalStateException if there is no binding expression that satisfies the dependency
   *     request
   */
  Expression getDependencyExpression(
      FrameworkDependency frameworkDependency, ClassName requestingClass) {
    return getDependencyExpression(
        frameworkDependency.bindingKey(),
        frameworkDependency.dependencyRequestKind(),
        requestingClass);
  }

  /**
   * Returns an expression that evaluates to the value of a dependency request, for passing to a
   * binding method, an {@code @Inject}-annotated constructor or member, or a proxy for one.
   *
   * <p>If the method is a generated static {@link InjectionMethods injection method}, each
   * parameter will be {@link Object} if the dependency's raw type is inaccessible. If that is the
   * case for this dependency, the returned expression will use a cast to evaluate to the raw type.
   *
   * @param requestingClass the class that will contain the expression
   */
  // TODO(b/64024402) Merge with getDependencyExpression(DependencyRequest, ClassName) if possible.
  Expression getDependencyArgumentExpression(
      DependencyRequest dependencyRequest, ClassName requestingClass) {

    TypeMirror dependencyType = dependencyRequest.key().type();
    Expression dependencyExpression = getDependencyExpression(dependencyRequest, requestingClass);

    if (!isTypeAccessibleFrom(dependencyType, requestingClass.packageName())
        && isRawTypeAccessible(dependencyType, requestingClass.packageName())) {
      return dependencyExpression.castTo(types.erasure(dependencyType));
    }

    return dependencyExpression;
  }

  /**
   * Returns an expression for the implementation of a component method with the given request.
   *
   * @throws IllegalStateException if there is no binding expression that satisfies the dependency
   *     request
   */
  Expression getComponentMethodExpression(DependencyRequest request, ClassName requestingClass) {
    return getBindingExpression(request.bindingKey())
        .getComponentMethodExpression(request, requestingClass);
  }

  private BindingExpression getBindingExpression(BindingKey bindingKey) {
    for (Map<BindingKey, BindingExpression> bindingExpressionsMap : bindingExpressionsMaps) {
      BindingExpression expression = bindingExpressionsMap.get(bindingKey);
      if (expression != null) {
        return expression;
      }
    }
    throw new IllegalStateException("no binding expression found for " + bindingKey);
  }

  /** Adds a binding expression for a single binding owned by this component. */
  void addBindingExpression(BindingExpression bindingExpression) {
    bindingExpressionsMaps
        .get(0)
        .put(bindingExpression.resolvedBindings().bindingKey(), bindingExpression);
  }

  /**
   * Returns a new object representing the bindings available from a child component of this one.
   */
  ComponentBindingExpressions forChildComponent() {
    return new ComponentBindingExpressions(
        FluentIterable.of(newBindingExpressionMap()).append(bindingExpressionsMaps).toList(),
        types);
  }

  private static Map<BindingKey, BindingExpression> newBindingExpressionMap() {
    return new HashMap<>();
  }
}

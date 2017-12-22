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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.Accessibility.isTypeAccessibleFrom;
import static dagger.internal.codegen.RequestKinds.requestType;

import com.squareup.javapoet.ClassName;
import dagger.model.RequestKind;
import dagger.model.Scope;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

/** A {@link BindingExpression} for {@code @Binds} methods. */
final class DelegateBindingExpression extends BindingExpression {
  private final ContributionBinding binding;
  private final ComponentBindingExpressions componentBindingExpressions;
  private final DaggerTypes types;
  private final BindsTypeChecker bindsTypeChecker;

  private DelegateBindingExpression(
      ResolvedBindings resolvedBindings,
      RequestKind requestKind,
      ComponentBindingExpressions componentBindingExpressions,
      DaggerTypes types,
      Elements elements) {
    super(resolvedBindings, requestKind);
    this.binding = checkNotNull(resolvedBindings.contributionBinding());
    this.componentBindingExpressions = checkNotNull(componentBindingExpressions);
    this.types = checkNotNull(types);
    this.bindsTypeChecker = new BindsTypeChecker(types, elements);
  }

  static BindingExpression create(
      BindingGraph graph,
      BindingExpression bindingExpression,
      ComponentBindingExpressions componentBindingExpressions,
      DaggerTypes types,
      Elements elements) {
    ResolvedBindings resolvedBindings = bindingExpression.resolvedBindings();
    ContributionBinding binding = resolvedBindings.contributionBinding();
    Binding delegateBinding =
        graph.contributionBindings().get(getOnlyElement(binding.dependencies()).key()).binding();
    ScopeKind bindsScope = ScopeKind.get(binding, graph, elements);
    ScopeKind delegateScope = ScopeKind.get(delegateBinding, graph, elements);
    if (bindsScope.isSimilarOrWeakerScopeThan(delegateScope)) {
      RequestKind requestKind = bindingExpression.requestKind();
      return new DelegateBindingExpression(
          resolvedBindings, requestKind, componentBindingExpressions, types, elements);
    }
    return bindingExpression;
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    Expression delegateExpression =
        componentBindingExpressions.getDependencyExpression(
            getOnlyElement(binding.dependencies()).key(), requestKind(), requestingClass);

    TypeMirror contributedType = binding.contributedType();
    switch (requestKind()) {
      case INSTANCE:
        return instanceRequiresCast(delegateExpression, requestingClass)
            ? delegateExpression.castTo(contributedType)
            : delegateExpression;
      default:
        return castToRawTypeIfNecessary(
            delegateExpression, requestType(requestKind(), contributedType, types));
    }
  }

  private boolean instanceRequiresCast(Expression delegateExpression, ClassName requestingClass) {
    // delegateExpression.type() could be Object if expression is satisfied with a raw
    // Provider's get() method.
    return !bindsTypeChecker.isAssignable(
        delegateExpression.type(), binding.contributedType(), binding.contributionType())
        && isTypeAccessibleFrom(binding.contributedType(), requestingClass.packageName());
  }

  /**
   * If {@code delegateExpression} can be assigned to {@code desiredType} safely, then {@code
   * delegateExpression} is returned unchanged. If the {@code delegateExpression} is already a raw
   * type, returns {@code delegateExpression} as well, as casting would have no effect. Otherwise,
   * returns a {@link Expression#castTo(TypeMirror) casted} version of {@code delegateExpression}
   * to the raw type of {@code desiredType}.
   */
  // TODO(ronshapiro): this probably can be generalized for usage in InjectionMethods
  private Expression castToRawTypeIfNecessary(
      Expression delegateExpression, TypeMirror desiredType) {
    if (types.isAssignable(delegateExpression.type(), desiredType)) {
      return delegateExpression;
    }
    return delegateExpression.castTo(types.erasure(desiredType));
  }

  private enum ScopeKind {
    UNSCOPED,
    RELEASABLE,
    SINGLE_CHECK,
    DOUBLE_CHECK,
    ;

    static ScopeKind get(Binding binding, BindingGraph graph, Elements elements) {
      if (!binding.scope().isPresent()) {
        return UNSCOPED;
      }

      Scope scope = binding.scope().get();
      if (graph.scopesRequiringReleasableReferenceManagers().contains(scope)) {
        return RELEASABLE;
      }
      return scope.isReusable() ? SINGLE_CHECK : DOUBLE_CHECK;
    }

    boolean isSimilarOrWeakerScopeThan(ScopeKind other) {
      return ordinal() <= other.ordinal();
    }
  }
}

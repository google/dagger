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
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.BindingRequest.bindingRequest;
import static dagger.internal.codegen.RequestKinds.requestType;
import static dagger.internal.codegen.langmodel.Accessibility.isTypeAccessibleFrom;
import static dagger.model.BindingKind.DELEGATE;

import com.squareup.javapoet.ClassName;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.RequestKind;
import javax.lang.model.type.TypeMirror;

/** A {@link BindingExpression} for {@code @Binds} methods. */
final class DelegateBindingExpression extends BindingExpression {
  private final ContributionBinding binding;
  private final RequestKind requestKind;
  private final ComponentBindingExpressions componentBindingExpressions;
  private final DaggerTypes types;
  private final BindsTypeChecker bindsTypeChecker;

  DelegateBindingExpression(
      ResolvedBindings resolvedBindings,
      RequestKind requestKind,
      ComponentBindingExpressions componentBindingExpressions,
      DaggerTypes types,
      DaggerElements elements) {
    this.binding = checkNotNull(resolvedBindings.contributionBinding());
    this.requestKind = checkNotNull(requestKind);
    this.componentBindingExpressions = checkNotNull(componentBindingExpressions);
    this.types = checkNotNull(types);
    this.bindsTypeChecker = new BindsTypeChecker(types, elements);
  }

  /**
   * Returns {@code true} if the {@code @Binds} binding's scope is stronger than the scope of the
   * binding it depends on.
   */
  static boolean isBindsScopeStrongerThanDependencyScope(
      ResolvedBindings resolvedBindings, BindingGraph graph) {
    ContributionBinding bindsBinding = resolvedBindings.contributionBinding();
    checkArgument(bindsBinding.kind().equals(DELEGATE));
    Binding dependencyBinding =
        graph
            .contributionBindings()
            .get(getOnlyElement(bindsBinding.dependencies()).key())
            .binding();
    ScopeKind bindsScope = ScopeKind.get(bindsBinding, graph);
    ScopeKind dependencyScope = ScopeKind.get(dependencyBinding, graph);
    return bindsScope.isStrongerScopeThan(dependencyScope);
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    Expression delegateExpression =
        componentBindingExpressions.getDependencyExpression(
            bindingRequest(getOnlyElement(binding.dependencies()).key(), requestKind),
            requestingClass);

    TypeMirror contributedType = binding.contributedType();
    switch (requestKind) {
      case INSTANCE:
        return instanceRequiresCast(delegateExpression, requestingClass)
            ? delegateExpression.castTo(contributedType)
            : delegateExpression;
      default:
        return castToRawTypeIfNecessary(
            delegateExpression, requestType(requestKind, contributedType, types));
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
    SINGLE_CHECK,
    DOUBLE_CHECK,
    ;

    static ScopeKind get(Binding binding, BindingGraph graph) {
      return binding
          .scope()
          .map(scope -> scope.isReusable() ? SINGLE_CHECK : DOUBLE_CHECK)
          .orElse(UNSCOPED);
    }

    boolean isStrongerScopeThan(ScopeKind other) {
      return this.ordinal() > other.ordinal();
    }
  }
}

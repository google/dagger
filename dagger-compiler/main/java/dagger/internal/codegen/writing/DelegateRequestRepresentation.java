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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.base.RequestKinds.requestType;
import static dagger.internal.codegen.binding.BindingRequest.bindingRequest;
import static dagger.internal.codegen.xprocessing.Accessibility.isTypeAccessibleFrom;
import static dagger.internal.codegen.xprocessing.XTypeNames.providerTypeNames;
import static dagger.internal.codegen.xprocessing.XTypes.isTypeOf;
import static dagger.internal.codegen.xprocessing.XTypes.rewrapType;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XCodeBlock;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.Binding;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.BindsTypeChecker;
import dagger.internal.codegen.binding.DelegateBinding;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.model.RequestKind;
import dagger.internal.codegen.xprocessing.XExpression;
import dagger.internal.codegen.xprocessing.XTypeNames;

/** A {@link dagger.internal.codegen.writing.RequestRepresentation} for {@code @Binds} methods. */
final class DelegateRequestRepresentation extends RequestRepresentation {
  private final DelegateBinding binding;
  private final RequestKind requestKind;
  private final ComponentRequestRepresentations componentRequestRepresentations;
  private final CompilerOptions compilerOptions;
  private final XProcessingEnv processingEnv;
  private final BindsTypeChecker bindsTypeChecker;

  @AssistedInject
  DelegateRequestRepresentation(
      @Assisted DelegateBinding binding,
      @Assisted RequestKind requestKind,
      ComponentRequestRepresentations componentRequestRepresentations,
      BindsTypeChecker bindsTypeChecker,
      CompilerOptions compilerOptions,
      XProcessingEnv processingEnv) {
    this.binding = checkNotNull(binding);
    this.requestKind = checkNotNull(requestKind);
    this.componentRequestRepresentations = componentRequestRepresentations;
    this.compilerOptions = compilerOptions;
    this.processingEnv = processingEnv;
    this.bindsTypeChecker = bindsTypeChecker;
  }

  /**
   * Returns {@code true} if the {@code @Binds} binding's scope is stronger than the scope of the
   * binding it depends on.
   */
  static boolean isBindsScopeStrongerThanDependencyScope(
      DelegateBinding bindsBinding, BindingGraph graph) {
    Binding dependencyBinding =
        graph.contributionBinding(getOnlyElement(bindsBinding.dependencies()).key());
    ScopeKind bindsScope = ScopeKind.get(bindsBinding);
    ScopeKind dependencyScope = ScopeKind.get(dependencyBinding);
    return bindsScope.isStrongerScopeThan(dependencyScope);
  }

  @Override
  XExpression getDependencyExpression(XClassName requestingClass) {
    XExpression delegateExpression =
        componentRequestRepresentations.getDependencyExpression(
            bindingRequest(getOnlyElement(binding.dependencies()).key(), requestKind),
            requestingClass);

    XType contributedType = binding.contributedType();
    switch (requestKind) {
      case INSTANCE:
        return instanceRequiresCast(binding, delegateExpression, requestingClass, bindsTypeChecker)
            ? delegateExpression.castTo(contributedType)
            : delegateExpression;
      default:
        XType requestedType = requestType(requestKind, contributedType, processingEnv);
        return castToRawTypeIfNecessary(
            delegateExpression,
            // Even though the user may have requested a javax/jakarta Provider, our generated code
            // and factories only work in the Dagger Provider type, so swap to that one before
            // doing a cast.
            isTypeOf(requestedType, providerTypeNames())
                ? rewrapType(requestedType, XTypeNames.DAGGER_PROVIDER)
                : requestedType);
    }
  }

  static boolean instanceRequiresCast(
      DelegateBinding binding,
      XExpression delegateExpression,
      XClassName requestingClass,
      BindsTypeChecker bindsTypeChecker) {
    // delegateExpression.type() could be Object if expression is satisfied with a raw
    // Provider's get() method.
    XType contributedType = binding.contributedType();
    return !bindsTypeChecker.isAssignable(
            delegateExpression.type(), contributedType, binding.contributionType())
        && isTypeAccessibleFrom(contributedType, requestingClass.getPackageName());
  }

  /**
   * If {@code delegateExpression} can be assigned to {@code desiredType} safely, then {@code
   * delegateExpression} is returned unchanged. If the {@code delegateExpression} is already a raw
   * type, returns {@code delegateExpression} as well, as casting would have no effect. Otherwise,
   * returns a {@link XExpression#castTo(XType) casted} version of {@code delegateExpression} to the
   * raw type of {@code desiredType}.
   */
  // TODO(ronshapiro): this probably can be generalized for usage in InjectionMethods
  private XExpression castToRawTypeIfNecessary(XExpression delegateExpression, XType desiredType) {
    if (delegateExpression.type().isAssignableTo(desiredType)) {
      return delegateExpression;
    }
    XExpression castedExpression = delegateExpression.castTo(desiredType.getRawType());
    // Casted raw type provider expression has to be wrapped parentheses, otherwise there
    // will be an error when DerivedFromFrameworkInstanceRequestRepresentation appends a `get()` to
    // it.
    // TODO(bcorso): change the logic to only add parenthesis when necessary.
    return XExpression.create(
        castedExpression.type(), XCodeBlock.of("(%L)", castedExpression.codeBlock()));
  }

  private enum ScopeKind {
    UNSCOPED,
    SINGLE_CHECK,
    DOUBLE_CHECK,
    ;

    static ScopeKind get(Binding binding) {
      return binding
          .scope()
          .map(scope -> scope.isReusable() ? SINGLE_CHECK : DOUBLE_CHECK)
          .orElse(UNSCOPED);
    }

    boolean isStrongerScopeThan(ScopeKind other) {
      return this.ordinal() > other.ordinal();
    }
  }

  @AssistedFactory
  static interface Factory {
    DelegateRequestRepresentation create(DelegateBinding binding, RequestKind requestKind);
  }
}

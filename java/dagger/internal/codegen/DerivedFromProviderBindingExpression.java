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

import com.squareup.javapoet.ClassName;
import dagger.model.Key;
import dagger.model.RequestKind;

/** A binding expression that depends on the expression for the {@link RequestKind#PROVIDER}. */
final class DerivedFromProviderBindingExpression extends BindingExpression {

  private final Key key;
  private final RequestKind requestKind;
  private final ComponentBindingExpressions componentBindingExpressions;
  private final DaggerTypes types;

  DerivedFromProviderBindingExpression(
      ResolvedBindings resolvedBindings,
      RequestKind requestKind,
      ComponentBindingExpressions componentBindingExpressions,
      DaggerTypes types) {
    this.key = resolvedBindings.key();
    this.requestKind = checkNotNull(requestKind);
    this.componentBindingExpressions = checkNotNull(componentBindingExpressions);
    this.types = checkNotNull(types);
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    return FrameworkType.PROVIDER.to(
        requestKind,
        componentBindingExpressions.getDependencyExpression(
            key, RequestKind.PROVIDER, requestingClass),
        types);
  }
}

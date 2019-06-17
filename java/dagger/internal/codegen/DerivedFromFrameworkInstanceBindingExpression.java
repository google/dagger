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
import static dagger.internal.codegen.BindingRequest.bindingRequest;

import com.squareup.javapoet.ClassName;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.Key;
import dagger.model.RequestKind;

/** A binding expression that depends on a framework instance. */
final class DerivedFromFrameworkInstanceBindingExpression extends BindingExpression {

  private final BindingRequest frameworkRequest;
  private final RequestKind requestKind;
  private final FrameworkType frameworkType;
  private final ComponentBindingExpressions componentBindingExpressions;
  private final DaggerTypes types;

  DerivedFromFrameworkInstanceBindingExpression(
      Key key,
      FrameworkType frameworkType,
      RequestKind requestKind,
      ComponentBindingExpressions componentBindingExpressions,
      DaggerTypes types) {
    this.frameworkRequest = bindingRequest(key, frameworkType);
    this.requestKind = checkNotNull(requestKind);
    this.frameworkType = checkNotNull(frameworkType);
    this.componentBindingExpressions = checkNotNull(componentBindingExpressions);
    this.types = checkNotNull(types);
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    return frameworkType.to(
        requestKind,
        componentBindingExpressions.getDependencyExpression(frameworkRequest, requestingClass),
        types);
  }

  @Override
  Expression getDependencyExpressionForComponentMethod(
      ComponentMethodDescriptor componentMethod, ComponentImplementation component) {
    Expression frameworkInstance =
        componentBindingExpressions.getDependencyExpressionForComponentMethod(
            frameworkRequest, componentMethod, component);
    return frameworkType.to(requestKind, frameworkInstance, types);
  }
}

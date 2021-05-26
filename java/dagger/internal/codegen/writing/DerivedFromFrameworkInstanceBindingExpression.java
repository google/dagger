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

package dagger.internal.codegen.writing;

import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.binding.BindingRequest.bindingRequest;

import com.squareup.javapoet.ClassName;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.BindingRequest;
import dagger.internal.codegen.binding.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.binding.FrameworkType;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.langmodel.DaggerTypes;

/** A binding expression that depends on a framework instance. */
final class DerivedFromFrameworkInstanceBindingExpression extends BindingExpression {
  private final BindingRequest bindingRequest;
  private final BindingRequest frameworkRequest;
  private final FrameworkType frameworkType;
  private final ComponentBindingExpressions componentBindingExpressions;
  private final DaggerTypes types;

  @AssistedInject
  DerivedFromFrameworkInstanceBindingExpression(
      @Assisted BindingRequest bindingRequest,
      @Assisted FrameworkType frameworkType,
      ComponentBindingExpressions componentBindingExpressions,
      DaggerTypes types) {
    this.bindingRequest = checkNotNull(bindingRequest);
    this.frameworkType = checkNotNull(frameworkType);
    this.frameworkRequest = bindingRequest(bindingRequest.key(), frameworkType);
    this.componentBindingExpressions = componentBindingExpressions;
    this.types = types;
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    return frameworkType.to(
        bindingRequest.requestKind(),
        componentBindingExpressions.getDependencyExpression(frameworkRequest, requestingClass),
        types);
  }

  @Override
  Expression getDependencyExpressionForComponentMethod(
      ComponentMethodDescriptor componentMethod, ComponentImplementation component) {
    Expression frameworkInstance =
        componentBindingExpressions.getDependencyExpressionForComponentMethod(
            frameworkRequest, componentMethod, component);
    return frameworkType.to(bindingRequest.requestKind(), frameworkInstance, types);
  }

  @AssistedFactory
  static interface Factory {
    DerivedFromFrameworkInstanceBindingExpression create(
        BindingRequest request, FrameworkType frameworkType);
  }
}

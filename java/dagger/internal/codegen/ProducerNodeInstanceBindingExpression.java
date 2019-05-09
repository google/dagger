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
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.Key;

/** Binding expression for producer node instances. */
final class ProducerNodeInstanceBindingExpression extends FrameworkInstanceBindingExpression {
  /** The component defining this binding. */
  private final ComponentImplementation componentImplementation;
  private final Key key;
  private final ProducerEntryPointView producerEntryPointView;

  ProducerNodeInstanceBindingExpression(
      ResolvedBindings resolvedBindings,
      FrameworkInstanceSupplier frameworkInstanceSupplier,
      DaggerTypes types,
      DaggerElements elements,
      ComponentImplementation componentImplementation) {
    super(resolvedBindings, frameworkInstanceSupplier, types, elements);
    this.componentImplementation = checkNotNull(componentImplementation);
    this.key = resolvedBindings.key();
    this.producerEntryPointView = new ProducerEntryPointView(types);
  }

  @Override
  protected FrameworkType frameworkType() {
    return FrameworkType.PRODUCER_NODE;
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    Expression result = super.getDependencyExpression(requestingClass);
    componentImplementation.addCancellableProducerKey(key);
    return result;
  }

  @Override
  Expression getDependencyExpressionForComponentMethod(
      ComponentMethodDescriptor componentMethod, ComponentImplementation component) {
    return producerEntryPointView
        .getProducerEntryPointField(this, componentMethod, component)
        .orElseGet(
            () -> super.getDependencyExpressionForComponentMethod(componentMethod, component));
  }
}

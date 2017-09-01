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

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;

/**
 * A {@link FrameworkInstanceBindingExpression} that is expressed with a {@link
 * javax.inject.Provider} for all {@link DependencyRequest.Kind}s except {@link
 * DependencyRequest.Kind#PRODUCER}, for which it uses a {@link
 * dagger.producers.internal.Producers#producerFromProvider(javax.inject.Provider) provider wrapped
 * by a producer}.
 */
final class ProviderOrProducerBindingExpression extends BindingExpression {
  private final FrameworkInstanceBindingExpression providerBindingExpression;
  private final FrameworkInstanceBindingExpression producerBindingExpression;

  ProviderOrProducerBindingExpression(
      FrameworkInstanceBindingExpression providerBindingExpression,
      FrameworkInstanceBindingExpression producerBindingExpression) {
    super(providerBindingExpression.resolvedBindings());
    this.providerBindingExpression = providerBindingExpression;
    this.producerBindingExpression = producerBindingExpression;
  }

  @Override
  CodeBlock getDependencyExpression(DependencyRequest.Kind requestKind, ClassName requestingClass) {
    switch (requestKind) {
      case PRODUCER:
        return producerBindingExpression.getDependencyExpression(requestKind, requestingClass);
      default:
        return providerBindingExpression.getDependencyExpression(requestKind, requestingClass);
    }
  }
}

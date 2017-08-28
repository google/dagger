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

import com.google.common.util.concurrent.Futures;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;

/**
 * A binding expression that can use a simple expression for instance requests, and delegates to
 * another expression for other requests.
 */
abstract class SimpleInvocationBindingExpression extends BindingExpression {
  private final BindingExpression delegate;

  SimpleInvocationBindingExpression(BindingExpression delegate) {
    super(delegate.resolvedBindings(), delegate.componentName());
    this.delegate = delegate;
  }

  /**
   * Returns an expression that evaluates to an instance of a dependency.
   *
   * @param requestingClass the class that will contain the expression
   */
  abstract CodeBlock getInstanceDependencyExpression(
      DependencyRequest.Kind requestKind, ClassName requestingClass);

  /**
   * Java 7 type inference is not as strong as in Java 8, and therefore some generated code must
   * make type parameters for {@link Futures#immediateFuture(Object)} explicit.
   *
   * <p>For example, {@code javac7} cannot detect that Futures.immediateFuture(ImmutableSet.of(T))}
   * can safely be assigned to {@code ListenableFuture<Set<T>>}.
   */
  protected CodeBlock explicitTypeParameter(ClassName requestingClass) {
    return CodeBlock.of("");
  }

  @Override
  final CodeBlock getDependencyExpression(
      DependencyRequest.Kind requestKind, ClassName requestingClass) {
    switch (requestKind) {
      case INSTANCE:
        return getInstanceDependencyExpression(requestKind, requestingClass);
      case FUTURE:
        return CodeBlock.builder()
            .add("$T.", Futures.class)
            .add(explicitTypeParameter(requestingClass))
            .add(
                "immediateFuture($L)",
                getInstanceDependencyExpression(requestKind, requestingClass))
            .build();
      default:
        return delegate.getDependencyExpression(requestKind, requestingClass);
    }
  }
}

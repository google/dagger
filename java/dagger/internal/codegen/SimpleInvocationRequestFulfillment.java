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
 * A {@link RequestFulfillment} that can fulfill its request with a simple call when possible, and
 * otherwise delegates to a backing provider field.
 */
abstract class SimpleInvocationRequestFulfillment extends RequestFulfillment {
  private final RequestFulfillment delegate;

  SimpleInvocationRequestFulfillment(BindingKey bindingKey, RequestFulfillment delegate) {
    super(bindingKey);
    this.delegate = delegate;
  }

  abstract CodeBlock getSimpleInvocation(DependencyRequest request, ClassName requestingClass);

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
  final CodeBlock getSnippetForDependencyRequest(
      DependencyRequest request, ClassName requestingClass) {
    switch (request.kind()) {
      case INSTANCE:
        return getSimpleInvocation(request, requestingClass);
      case FUTURE:
        return CodeBlock.builder()
            .add("$T.", Futures.class)
            .add(explicitTypeParameter(requestingClass))
            .add("immediateFuture($L)", getSimpleInvocation(request, requestingClass))
            .build();
      default:
        return delegate.getSnippetForDependencyRequest(request, requestingClass);
    }
  }

  @Override
  final CodeBlock getSnippetForFrameworkDependency(
      FrameworkDependency frameworkDependency, ClassName requestingClass) {
    return delegate.getSnippetForFrameworkDependency(frameworkDependency, requestingClass);
  }
}

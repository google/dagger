/*
 * Copyright (C) 2015 The Dagger Authors.
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

import static dagger.internal.codegen.BindingRequest.bindingRequest;
import static dagger.internal.codegen.javapoet.CodeBlocks.anonymousProvider;
import static dagger.model.RequestKind.INSTANCE;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import dagger.internal.codegen.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;
import dagger.internal.codegen.javapoet.Expression;

/**
 * A {@link javax.inject.Provider} creation expression for an anonymous inner class whose
 * {@code get()} method returns the expression for an instance binding request for its key.
 */
final class AnonymousProviderCreationExpression
    implements FrameworkInstanceCreationExpression {
  private final ContributionBinding binding;
  private final ComponentBindingExpressions componentBindingExpressions;
  private final ClassName requestingClass;

  AnonymousProviderCreationExpression(
      ContributionBinding binding,
      ComponentBindingExpressions componentBindingExpressions,
      ClassName requestingClass) {
    this.binding = binding;
    this.componentBindingExpressions = componentBindingExpressions;
    this.requestingClass = requestingClass;
  }

  @Override
  public CodeBlock creationExpression() {
    BindingRequest instanceExpressionRequest = bindingRequest(binding.key(), INSTANCE);
    Expression instanceExpression =
        componentBindingExpressions.getDependencyExpression(
            instanceExpressionRequest,
            // Not a real class name, but the actual requestingClass is an inner class within the
            // given class, not that class itself.
            requestingClass.nestedClass("Anonymous"));
    return anonymousProvider(instanceExpression);
  }
}

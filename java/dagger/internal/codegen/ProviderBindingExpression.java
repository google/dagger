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

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import java.util.Optional;

final class ProviderBindingExpression extends FrameworkInstanceBindingExpression {
  ProviderBindingExpression(
      BindingKey bindingKey,
      Optional<FieldSpec> fieldSpec,
      HasBindingExpressions hasBindingExpressions,
      MemberSelect memberSelect) {
    super(bindingKey, fieldSpec, hasBindingExpressions, memberSelect);
  }

  @Override
  CodeBlock getDependencyExpression(DependencyRequest request, ClassName requestingClass) {
    return FrameworkType.PROVIDER.to(request.kind(), getFrameworkTypeInstance(requestingClass));
  }

  @Override
  CodeBlock getDependencyExpression(
      FrameworkDependency frameworkDependency, ClassName requestingClass) {
    switch (frameworkDependency.bindingType()) {
      case PROVISION:
        return getFrameworkTypeInstance(requestingClass);
      case MEMBERS_INJECTION:
        throw new IllegalArgumentException();
      case PRODUCTION:
        return FrameworkType.PROVIDER.to(
            DependencyRequest.Kind.PRODUCER, getFrameworkTypeInstance(requestingClass));
      default:
        throw new AssertionError();
    }
  }

  @Override
  boolean isProducerFromProvider() {
    return false;
  }
}

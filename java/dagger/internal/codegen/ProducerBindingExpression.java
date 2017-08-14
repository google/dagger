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

import static com.google.common.base.Preconditions.checkArgument;
import static dagger.internal.codegen.BindingType.PRODUCTION;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import java.util.Optional;

final class ProducerBindingExpression extends FrameworkInstanceBindingExpression {
  private final boolean isProducerFromProvider;

  ProducerBindingExpression(
      BindingKey bindingKey,
      Optional<FieldSpec> fieldSpec,
      HasBindingExpressions hasBindingExpressions,
      MemberSelect memberSelect,
      boolean isProducerFromProvider) {
    super(bindingKey, fieldSpec, hasBindingExpressions, memberSelect);
    this.isProducerFromProvider = isProducerFromProvider;
  }

  @Override
  CodeBlock getDependencyExpression(DependencyRequest request, ClassName requestingClass) {
    return FrameworkType.PRODUCER.to(request.kind(), getFrameworkTypeInstance(requestingClass));
  }

  @Override
  CodeBlock getDependencyExpression(
      FrameworkDependency frameworkDependency, ClassName requestingClass) {
    checkArgument(
        frameworkDependency.bindingType().equals(PRODUCTION),
        "%s is not a production dependency",
        frameworkDependency);
    return getFrameworkTypeInstance(requestingClass);
  }

  @Override
  boolean isProducerFromProvider() {
    return isProducerFromProvider;
  }
}

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

/** A binding expression that uses a {@link javax.inject.Provider} instance. */
final class ProviderBindingExpression extends FrameworkInstanceBindingExpression {
  ProviderBindingExpression(
      BindingKey bindingKey,
      Optional<FieldSpec> fieldSpec,
      GeneratedComponentModel generatedComponentModel,
      MemberSelect memberSelect) {
    super(bindingKey, fieldSpec, generatedComponentModel, memberSelect);
  }

  @Override
  CodeBlock getDependencyExpression(DependencyRequest.Kind requestKind, ClassName requestingClass) {
    return FrameworkType.PROVIDER.to(requestKind, getFrameworkTypeInstance(requestingClass));
  }

  @Override
  boolean isProducerFromProvider() {
    return false;
  }
}

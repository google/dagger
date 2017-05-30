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
import dagger.internal.codegen.DependencyRequest.Kind;

final class SubcomponentBuilderRequestFulfillment extends RequestFulfillment {
  private final RequestFulfillment delegate;
  private final String subcomponentBuilderName;

  SubcomponentBuilderRequestFulfillment(
      BindingKey bindingKey, RequestFulfillment delegate, String subcomponentBuilderName) {
    super(bindingKey);
    this.delegate = delegate;
    this.subcomponentBuilderName = subcomponentBuilderName;
  }

  @Override
  CodeBlock getSnippetForDependencyRequest(DependencyRequest request, ClassName requestingClass) {
    if (request.kind().equals(Kind.INSTANCE)) {
      return CodeBlock.of("new $LBuilder()", subcomponentBuilderName);
    }
    return delegate.getSnippetForDependencyRequest(request, requestingClass);
  }

  @Override
  CodeBlock getSnippetForFrameworkDependency(
      FrameworkDependency frameworkDependency, ClassName requestingClass) {
    return delegate.getSnippetForFrameworkDependency(frameworkDependency, requestingClass);
  }
}

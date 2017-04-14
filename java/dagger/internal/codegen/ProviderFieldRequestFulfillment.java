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

import static com.google.common.base.Preconditions.checkArgument;
import static dagger.internal.codegen.BindingKey.Kind.CONTRIBUTION;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import dagger.producers.internal.Producers;

/** Fulfills requests for {@link ProvisionBinding} instances. */
final class ProviderFieldRequestFulfillment extends RequestFulfillment {
  private final MemberSelect providerFieldSelect;

  ProviderFieldRequestFulfillment(BindingKey bindingKey, MemberSelect frameworkFieldSelect) {
    super(bindingKey);
    checkArgument(bindingKey.kind().equals(CONTRIBUTION));
    this.providerFieldSelect = frameworkFieldSelect;
  }

  @Override
  public CodeBlock getSnippetForDependencyRequest(
      DependencyRequest request, ClassName requestingClass) {
    return FrameworkType.PROVIDER.to(
        request.kind(), providerFieldSelect.getExpressionFor(requestingClass));
  }

  @Override
  CodeBlock getSnippetForFrameworkDependency(
      FrameworkDependency frameworkDependency, ClassName requestingClass) {
    switch (frameworkDependency.bindingType()) {
      case PROVISION:
        return providerFieldSelect.getExpressionFor(requestingClass);
      case MEMBERS_INJECTION:
        throw new IllegalArgumentException();
      case PRODUCTION:
        return CodeBlock.of(
            "$T.producerFromProvider($L)",
            Producers.class,
            providerFieldSelect.getExpressionFor(requestingClass));
      default:
        throw new AssertionError();
    }
  }
}

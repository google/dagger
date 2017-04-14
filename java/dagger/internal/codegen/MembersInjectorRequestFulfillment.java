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
import static dagger.internal.codegen.BindingKey.Kind.MEMBERS_INJECTION;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import dagger.internal.codegen.DependencyRequest.Kind;

/** Fulfills requests for {@link MembersInjectionBinding} instances. */
final class MembersInjectorRequestFulfillment extends RequestFulfillment {
  private final MemberSelect membersInjectorFieldSelect;

  MembersInjectorRequestFulfillment(
      BindingKey bindingKey, MemberSelect membersInjectorFieldSelect) {
    super(bindingKey);
    checkArgument(bindingKey.kind().equals(MEMBERS_INJECTION));
    this.membersInjectorFieldSelect = membersInjectorFieldSelect;
  }

  @Override
  public CodeBlock getSnippetForDependencyRequest(
      DependencyRequest request, ClassName requestingClass) {
    checkArgument(request.kind().equals(Kind.MEMBERS_INJECTOR));
    return membersInjectorFieldSelect.getExpressionFor(requestingClass);
  }

  @Override
  CodeBlock getSnippetForFrameworkDependency(
      FrameworkDependency frameworkDependency, ClassName requestingClass) {
    checkArgument(frameworkDependency.bindingType().equals(BindingType.MEMBERS_INJECTION));
    return membersInjectorFieldSelect.getExpressionFor(requestingClass);
  }
}

/*
 * Copyright (C) 2018 The Dagger Authors.
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

import static dagger.internal.codegen.CodeBlocks.anonymousProvider;
import static dagger.internal.codegen.RequestKinds.requestType;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;
import dagger.model.RequestKind;
import javax.lang.model.type.TypeMirror;

/** A {@link BindingExpression} that returns an anonymous inner {@code Provider} instance. */
final class AnonymousProviderBindingExpression extends BindingExpression {

  private final ComponentBindingExpressions componentBindingExpressions;
  private final DaggerTypes types;
  private final ContributionBinding binding;

  AnonymousProviderBindingExpression(
      ResolvedBindings resolvedBindings,
      ComponentBindingExpressions componentBindingExpressions,
      DaggerTypes types) {
    this.componentBindingExpressions = componentBindingExpressions;
    this.types = types;
    this.binding = resolvedBindings.contributionBinding();
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    TypeMirror providedType = types.accessibleType(binding.contributedType(), requestingClass);
    return Expression.create(
        requestType(RequestKind.PROVIDER, providedType, types),
        anonymousProvider(
            TypeName.get(providedType),
            CodeBlock.of(
                "return $L;",
                componentBindingExpressions
                    .getDependencyExpression(binding.key(), RequestKind.INSTANCE, requestingClass)
                    .codeBlock())));
  }

  @Override
  boolean requiresMethodEncapsulation() {
    return true;
  }
}

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

import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.BindingRequest.bindingRequest;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.Key;
import dagger.model.RequestKind;
import javax.lang.model.SourceVersion;

final class ImmediateFutureBindingExpression extends BindingExpression {

  private final ComponentBindingExpressions componentBindingExpressions;
  private final DaggerTypes types;
  private final SourceVersion sourceVersion;
  private final Key key;

  ImmediateFutureBindingExpression(
      ResolvedBindings resolvedBindings,
      ComponentBindingExpressions componentBindingExpressions,
      DaggerTypes types,
      SourceVersion sourceVersion) {
    this.componentBindingExpressions = checkNotNull(componentBindingExpressions);
    this.types = checkNotNull(types);
    this.sourceVersion = checkNotNull(sourceVersion);
    this.key = resolvedBindings.key();
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    return Expression.create(
        types.wrapType(key.type(), ListenableFuture.class),
        CodeBlock.of("$T.immediateFuture($L)", Futures.class, instanceExpression(requestingClass)));
  }

  private CodeBlock instanceExpression(ClassName requestingClass) {
    Expression expression =
        componentBindingExpressions.getDependencyExpression(
            bindingRequest(key, RequestKind.INSTANCE), requestingClass);
    if (sourceVersion.compareTo(SourceVersion.RELEASE_7) <= 0) {
      // Java 7 type inference is not as strong as in Java 8, and therefore some generated code must
      // cast.
      //
      // For example, javac7 cannot detect that Futures.immediateFuture(ImmutableSet.of("T"))
      // can safely be assigned to ListenableFuture<Set<T>>.
      if (!types.isSameType(expression.type(), key.type())) {
        return CodeBlock.of(
            "($T) $L", types.accessibleType(key.type(), requestingClass), expression.codeBlock());
      }
    }
    return expression.codeBlock();
  }
}

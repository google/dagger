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

package dagger.internal.codegen.writing;

import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.binding.BindingRequest.bindingRequest;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.spi.model.Key;
import dagger.spi.model.RequestKind;
import javax.lang.model.SourceVersion;

final class ImmediateFutureRequestRepresentation extends RequestRepresentation {
  private final Key key;
  private final ComponentRequestRepresentations componentRequestRepresentations;
  private final DaggerTypes types;
  private final SourceVersion sourceVersion;

  @AssistedInject
  ImmediateFutureRequestRepresentation(
      @Assisted Key key,
      ComponentRequestRepresentations componentRequestRepresentations,
      DaggerTypes types,
      SourceVersion sourceVersion) {
    this.key = key;
    this.componentRequestRepresentations = checkNotNull(componentRequestRepresentations);
    this.types = checkNotNull(types);
    this.sourceVersion = checkNotNull(sourceVersion);
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    return Expression.create(
        types.wrapType(key.type().java(), ListenableFuture.class),
        CodeBlock.of("$T.immediateFuture($L)", Futures.class, instanceExpression(requestingClass)));
  }

  private CodeBlock instanceExpression(ClassName requestingClass) {
    Expression expression =
        componentRequestRepresentations.getDependencyExpression(
            bindingRequest(key, RequestKind.INSTANCE), requestingClass);
    if (sourceVersion.compareTo(SourceVersion.RELEASE_7) <= 0) {
      // Java 7 type inference is not as strong as in Java 8, and therefore some generated code must
      // cast.
      //
      // For example, javac7 cannot detect that Futures.immediateFuture(ImmutableSet.of("T"))
      // can safely be assigned to ListenableFuture<Set<T>>.
      if (!types.isSameType(expression.type(), key.type().java())) {
        return CodeBlock.of(
            "($T) $L",
            types.accessibleType(key.type().java(), requestingClass),
            expression.codeBlock());
      }
    }
    return expression.codeBlock();
  }

  @AssistedFactory
  static interface Factory {
    ImmediateFutureRequestRepresentation create(Key key);
  }
}

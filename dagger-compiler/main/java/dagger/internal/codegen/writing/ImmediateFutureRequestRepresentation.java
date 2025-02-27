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
import static dagger.internal.codegen.langmodel.Accessibility.accessibleTypeName;
import static dagger.internal.codegen.xprocessing.XProcessingEnvs.isPreJava8SourceVersion;
import static dagger.internal.codegen.xprocessing.XProcessingEnvs.wrapType;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import com.google.common.util.concurrent.Futures;
import com.squareup.javapoet.CodeBlock;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.xprocessing.XTypeNames;

final class ImmediateFutureRequestRepresentation extends RequestRepresentation {
  private final RequestRepresentation instanceRequestRepresentation;
  private final XType type;
  private final XProcessingEnv processingEnv;

  @AssistedInject
  ImmediateFutureRequestRepresentation(
      @Assisted RequestRepresentation instanceRequestRepresentation,
      @Assisted XType type,
      XProcessingEnv processingEnv) {
    this.instanceRequestRepresentation = checkNotNull(instanceRequestRepresentation);
    this.type = checkNotNull(type);
    this.processingEnv = processingEnv;
  }

  @Override
  Expression getDependencyExpression(XClassName requestingClass) {
    return Expression.create(
        wrapType(XTypeNames.LISTENABLE_FUTURE, type, processingEnv),
        CodeBlock.of("$T.immediateFuture($L)", Futures.class, instanceExpression(requestingClass)));
  }

  private CodeBlock instanceExpression(XClassName requestingClass) {
    Expression expression = instanceRequestRepresentation.getDependencyExpression(requestingClass);
    if (isPreJava8SourceVersion(processingEnv)) {
      // Java 7 type inference is not as strong as in Java 8, and therefore some generated code must
      // cast.
      //
      // For example, javac7 cannot detect that Futures.immediateFuture(ImmutableSet.of("T"))
      // can safely be assigned to ListenableFuture<Set<T>>.
      if (!expression.type().isSameType(type)) {
        return CodeBlock.of(
            "($T) $L",
            accessibleTypeName(type, requestingClass, processingEnv),
            expression.codeBlock());
      }
    }
    return expression.codeBlock();
  }

  @AssistedFactory
  static interface Factory {
    ImmediateFutureRequestRepresentation create(
        RequestRepresentation instanceRequestRepresentation, XType type);
  }
}

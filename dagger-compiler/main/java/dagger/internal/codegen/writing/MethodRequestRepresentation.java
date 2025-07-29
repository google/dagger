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

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XCodeBlock;
import androidx.room.compiler.processing.XProcessingEnv;
import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;
import dagger.internal.codegen.xprocessing.XExpression;
import dagger.internal.codegen.xprocessing.XExpressionType;

/** A binding expression that wraps another in a nullary method on the component. */
abstract class MethodRequestRepresentation extends RequestRepresentation {
  private final ShardImplementation shardImplementation;

  protected MethodRequestRepresentation(
      ShardImplementation shardImplementation, XProcessingEnv processingEnv) {
    this.shardImplementation = shardImplementation;
  }

  @Override
  XExpression getDependencyExpression(XClassName requestingClass) {
    return XExpression.create(
        returnType(),
        requestingClass.equals(shardImplementation.name())
            ? methodCall()
            : XCodeBlock.of("%L.%L", shardImplementation.shardFieldReference(), methodCall()));
  }

  /** Returns the return type for the dependency request. */
  protected abstract XExpressionType returnType();

  /** Returns the method call. */
  protected abstract XCodeBlock methodCall();
}

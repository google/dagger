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

import androidx.room.compiler.processing.XProcessingEnv;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import dagger.internal.codegen.binding.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.javapoet.ExpressionType;
import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;

/** A binding expression that wraps another in a nullary method on the component. */
abstract class MethodRequestRepresentation extends RequestRepresentation {
  private final ShardImplementation shardImplementation;
  private final ProducerEntryPointView producerEntryPointView;

  protected MethodRequestRepresentation(
      ShardImplementation shardImplementation, XProcessingEnv processingEnv) {
    this.shardImplementation = shardImplementation;
    this.producerEntryPointView = new ProducerEntryPointView(shardImplementation, processingEnv);
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    return Expression.create(
        returnType(),
        requestingClass.equals(shardImplementation.name())
            ? methodCall()
            : CodeBlock.of("$L.$L", shardImplementation.shardFieldReference(), methodCall()));
  }

  @Override
  Expression getDependencyExpressionForComponentMethod(
      ComponentMethodDescriptor componentMethod, ComponentImplementation component) {
    return producerEntryPointView
        .getProducerEntryPointField(this, componentMethod, component.name())
        .orElseGet(
            () -> super.getDependencyExpressionForComponentMethod(componentMethod, component));
  }

  /** Returns the return type for the dependency request. */
  protected abstract ExpressionType returnType();

  /** Returns the method call. */
  protected abstract CodeBlock methodCall();
}

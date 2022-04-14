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

import static dagger.internal.codegen.writing.ComponentImplementation.FieldSpecKind.FRAMEWORK_FIELD;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static dagger.internal.codegen.xprocessing.XProcessingEnvs.wrapType;
import static javax.lang.model.element.Modifier.PRIVATE;

import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import dagger.internal.codegen.binding.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;
import dagger.producers.Producer;
import dagger.producers.internal.CancellationListener;
import dagger.producers.internal.Producers;
import dagger.spi.model.RequestKind;
import java.util.Optional;

/**
 * A factory of {@linkplain Producers#entryPointViewOf(Producer, CancellationListener) entry point
 * views} of {@link Producer}s.
 */
final class ProducerEntryPointView {
  private final ShardImplementation shardImplementation;
  private final XProcessingEnv processingEnv;

  ProducerEntryPointView(ShardImplementation shardImplementation, XProcessingEnv processingEnv) {
    this.shardImplementation = shardImplementation;
    this.processingEnv = processingEnv;
  }

  /**
   * Returns an expression for an {@linkplain Producers#entryPointViewOf(Producer,
   * CancellationListener) entry point view} of a producer if the component method returns a {@link
   * Producer} or {@link com.google.common.util.concurrent.ListenableFuture}.
   *
   * <p>This is intended to be a replacement implementation for {@link
   * dagger.internal.codegen.writing.RequestRepresentation#getDependencyExpressionForComponentMethod(ComponentMethodDescriptor,
   * ComponentImplementation)}, and in cases where {@link Optional#empty()} is returned, callers
   * should call {@code super.getDependencyExpressionForComponentMethod()}.
   */
  Optional<Expression> getProducerEntryPointField(
      RequestRepresentation producerExpression,
      ComponentMethodDescriptor componentMethod,
      ClassName requestingClass) {
    if (shardImplementation.componentDescriptor().isProduction()
        && (componentMethod.dependencyRequest().get().kind().equals(RequestKind.FUTURE)
            || componentMethod.dependencyRequest().get().kind().equals(RequestKind.PRODUCER))) {
      MemberSelect field = createField(producerExpression, componentMethod);
      return Optional.of(
          Expression.create(fieldType(componentMethod), field.getExpressionFor(requestingClass)));
    } else {
      // If the component isn't a production component, it won't implement CancellationListener and
      // as such we can't create an entry point. But this binding must also just be a Producer from
      // Provider anyway in that case, so there shouldn't be an issue.
      // TODO(b/116855531): Is it really intended that a non-production component can have Producer
      // entry points?
      return Optional.empty();
    }
  }

  private MemberSelect createField(
      RequestRepresentation producerExpression, ComponentMethodDescriptor componentMethod) {
    // TODO(cgdecker): Use a FrameworkFieldInitializer for this?
    // Though I don't think we need the once-only behavior of that, since I think
    // getComponentMethodImplementation will only be called once anyway
    String methodName = getSimpleName(componentMethod.methodElement());
    FieldSpec field =
        FieldSpec.builder(
                fieldType(componentMethod).getTypeName(),
                shardImplementation.getUniqueFieldName(methodName + "EntryPoint"),
                PRIVATE)
            .build();
    shardImplementation.addField(FRAMEWORK_FIELD, field);

    CodeBlock fieldInitialization =
        CodeBlock.of(
            "this.$N = $T.entryPointViewOf($L, $L);",
            field,
            Producers.class,
            producerExpression.getDependencyExpression(shardImplementation.name()).codeBlock(),
            // Always pass in the componentShard reference here rather than the owning shard for
            // this key because this needs to be the root CancellationListener.
            shardImplementation.isComponentShard()
                ? "this"
                : shardImplementation
                    .getComponentImplementation()
                    .getComponentShard()
                    .shardFieldReference());
    shardImplementation.addInitialization(fieldInitialization);

    return MemberSelect.localField(shardImplementation, field.name);
  }

  // TODO(cgdecker): Can we use producerExpression.getDependencyExpression().type() instead of
  // needing to (re)compute this?
  private XType fieldType(ComponentMethodDescriptor componentMethod) {
    return wrapType(
        TypeNames.PRODUCER,
        componentMethod.dependencyRequest().get().key().type().xprocessing(),
        processingEnv);
  }
}

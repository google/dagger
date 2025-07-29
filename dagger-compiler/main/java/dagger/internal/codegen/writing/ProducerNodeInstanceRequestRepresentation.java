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
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.binding.FrameworkType;
import dagger.internal.codegen.binding.KeyVariableNamer;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.model.Key;
import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;
import dagger.internal.codegen.writing.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;
import dagger.internal.codegen.xprocessing.XExpression;
import dagger.internal.codegen.xprocessing.XTypeNames;
import java.util.Optional;

/** Binding expression for producer node instances. */
final class ProducerNodeInstanceRequestRepresentation
    extends FrameworkInstanceRequestRepresentation {
  private final ShardImplementation shardImplementation;
  private final FrameworkFieldInitializer producerEntryPointViewFieldInitializer;
  private final Key key;

  @AssistedInject
  ProducerNodeInstanceRequestRepresentation(
      @Assisted ContributionBinding binding,
      @Assisted FrameworkInstanceSupplier frameworkInstanceSupplier,
      CompilerOptions compilerOptions,
      XProcessingEnv processingEnv,
      ComponentImplementation componentImplementation) {
    super(binding, frameworkInstanceSupplier, processingEnv);
    this.shardImplementation = componentImplementation.shardImplementation(binding);
    this.key = binding.key();
    this.producerEntryPointViewFieldInitializer =
        new FrameworkFieldInitializer(
            compilerOptions,
            componentImplementation,
            binding,
            new ProducerEntryPointViewCreationExpression());
  }

  @Override
  protected FrameworkType frameworkType() {
    return FrameworkType.PRODUCER_NODE;
  }

  @Override
  XExpression getDependencyExpression(XClassName requestingClass) {
    XExpression result = super.getDependencyExpression(requestingClass);
    shardImplementation.addCancellation(
        key,
        XCodeBlock.of(
            "%T.cancel(%L, %N);",
            XTypeNames.PRODUCERS,
            result.codeBlock(),
            ComponentImplementation.MAY_INTERRUPT_IF_RUNNING_PARAM));
    return result;
  }

  @Override
  XExpression getDependencyExpressionForComponentMethod(
      ComponentMethodDescriptor componentMethod, ComponentImplementation component) {
    return requiresEntryPointView(componentMethod)
        ? XExpression.create(
            getDependencyExpression(shardImplementation.name()).type(),
            producerEntryPointViewFieldInitializer
                .memberSelect()
                .getExpressionFor(component.name()))
        : super.getDependencyExpressionForComponentMethod(componentMethod, component);
  }

  private boolean requiresEntryPointView(ComponentMethodDescriptor componentMethod) {
    switch (componentMethod.dependencyRequest().get().kind()) {
      case PRODUCER:
      case FUTURE:
        // If the component isn't a production component, it won't implement CancellationListener
        // and as such we can't create an entry point. But this binding must also just be a Producer
        // from Provider anyway in that case, so there shouldn't be an issue.
        // TODO(b/116855531): Is it really intended that a non-production component can have
        // Producer entry points?
        return shardImplementation.componentDescriptor().isProduction();
      default:
        return false;
    }
  }

  private class ProducerEntryPointViewCreationExpression
      implements FrameworkInstanceCreationExpression {
    @Override
    public XCodeBlock creationExpression() {
      return XCodeBlock.of(
          "%T.entryPointViewOf(%L, %L)",
          XTypeNames.PRODUCERS,
          getDependencyExpression(shardImplementation.name()).codeBlock(),
          // Always pass in the componentShard reference here rather than the owning shard for
          // this key because this needs to be the root CancellationListener.
          shardImplementation.isComponentShard()
              ? XCodeBlock.of("this")
              : shardImplementation
                  .getComponentImplementation()
                  .getComponentShard()
                  .shardFieldReference());
    }

    @Override
    public Optional<String> preferredFieldName() {
      return Optional.of(KeyVariableNamer.name(key) + "EntryPoint");
    }

    @Override
    public Optional<XClassName> alternativeFrameworkClass() {
      return Optional.of(XTypeNames.PRODUCER);
    }
  }

  @AssistedFactory
  static interface Factory {
    ProducerNodeInstanceRequestRepresentation create(
        ContributionBinding binding, FrameworkInstanceSupplier frameworkInstanceSupplier);
  }
}

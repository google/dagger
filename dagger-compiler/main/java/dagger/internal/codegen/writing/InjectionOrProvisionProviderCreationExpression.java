/*
 * Copyright (C) 2015 The Dagger Authors.
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
import static dagger.internal.codegen.binding.SourceFiles.generatedClassNameForBinding;
import static dagger.internal.codegen.extension.DaggerCollectors.toOptional;
import static dagger.internal.codegen.model.BindingKind.ASSISTED_FACTORY;
import static dagger.internal.codegen.model.BindingKind.INJECTION;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XCodeBlock;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;
import dagger.internal.codegen.writing.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;
import dagger.internal.codegen.xprocessing.XCodeBlocks;
import dagger.internal.codegen.xprocessing.XTypeNames;
import java.util.Optional;

/**
 * A {@link Provider} creation expression for an {@link javax.inject.Inject @Inject}-constructed
 * class or a {@link dagger.Provides @Provides}-annotated module method.
 */
// TODO(dpb): Resolve with ProducerCreationExpression.
final class InjectionOrProvisionProviderCreationExpression
    implements FrameworkInstanceCreationExpression {

  private final ContributionBinding binding;
  private final ShardImplementation shardImplementation;
  private final ComponentRequestRepresentations componentRequestRepresentations;
  private final XProcessingEnv processingEnv;

  @AssistedInject
  InjectionOrProvisionProviderCreationExpression(
      @Assisted ContributionBinding binding,
      ComponentImplementation componentImplementation,
      ComponentRequestRepresentations componentRequestRepresentations,
      XProcessingEnv processingEnv) {
    this.binding = checkNotNull(binding);
    this.shardImplementation = componentImplementation.shardImplementation(binding);
    this.componentRequestRepresentations = componentRequestRepresentations;
    this.processingEnv = processingEnv;
  }

  @Override
  public XCodeBlock creationExpression() {
    XClassName factoryImpl = generatedClassNameForBinding(binding);
    XCodeBlock createFactory =
        XCodeBlock.of(
            "%T.%L(%L)",
            factoryImpl,
            // A different name is used for assisted factories due to backwards compatibility
            // issues when migrating from the javax Provider.
            binding.kind().equals(ASSISTED_FACTORY) ? "createFactoryProvider" : "create",
            componentRequestRepresentations.getCreateMethodArgumentsCodeBlock(
                binding, shardImplementation.name()));

    // If this is for an AssistedFactory, then we may need to change the call in case we're building
    // against a library built at an older version of Dagger before the changes to make factories
    // return a Dagger Provider instead of a javax.inject.Provider.
    if (binding.kind().equals(ASSISTED_FACTORY)) {
      XTypeElement factoryType = processingEnv.findTypeElement(factoryImpl);
      // If we can't find the factory, then assume it is being generated this run, which means
      // it should be the newer version and not need wrapping. If it is missing for some other
      // reason, then that likely means there will just be some other compilation failure.
      if (factoryType != null) {
        Optional<XMethodElement> createMethod = factoryType.getDeclaredMethods().stream()
            .filter(method -> method.isStatic()
                && getSimpleName(method).equals("createFactoryProvider"))
            .collect(toOptional());
        // Only convert it if the newer method doesn't exist.
        if (createMethod.isEmpty()) {
          createFactory =
              XCodeBlock.of(
                  "%T.asDaggerProvider(%T.create(%L))",
                  XTypeNames.DAGGER_PROVIDERS,
                  factoryImpl,
                  componentRequestRepresentations.getCreateMethodArgumentsCodeBlock(
                      binding, shardImplementation.name()));
        }
      }
    }

    // When scoping a parameterized factory for an @Inject class, Java 7 cannot always infer the
    // type properly, so cast to a raw framework type before scoping.
    if (binding.kind().equals(INJECTION)
        && binding.unresolved().isPresent()
        && binding.scope().isPresent()) {
      return XCodeBlocks.cast(createFactory, XTypeNames.DAGGER_PROVIDER);
    } else {
      return createFactory;
    }
  }

  @AssistedFactory
  static interface Factory {
    InjectionOrProvisionProviderCreationExpression create(ContributionBinding binding);
  }
}

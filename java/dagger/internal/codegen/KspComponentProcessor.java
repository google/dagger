/*
 * Copyright (C) 2022 The Dagger Authors.
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

import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XProcessingStep;
import androidx.room.compiler.processing.XRoundEnv;
import androidx.room.compiler.processing.ksp.KspBasicAnnotationProcessor;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.ksp.processing.SymbolProcessor;
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment;
import com.google.devtools.ksp.processing.SymbolProcessorProvider;
import dagger.spi.model.BindingGraphPlugin;
import java.util.Arrays;
import java.util.Optional;

/**
 * The KSP processor responsible for generating the classes that drive the Dagger implementation.
 */
public final class KspComponentProcessor extends KspBasicAnnotationProcessor {
  private final DelegateComponentProcessor delegate = new DelegateComponentProcessor();
  private final Optional<ImmutableSet<BindingGraphPlugin>> testingPlugins;

  private KspComponentProcessor(
      SymbolProcessorEnvironment symbolProcessorEnvironment,
      Optional<ImmutableSet<BindingGraphPlugin>> testingPlugins) {
    super(symbolProcessorEnvironment, DelegateComponentProcessor.PROCESSING_ENV_CONFIG);
    this.testingPlugins = testingPlugins;
  }

  @Override
  public void initialize(XProcessingEnv env) {
    delegate.initialize(
        env,
        testingPlugins,
        // The legacy BindingGraphPlugin is only supported with Javac.
        /* legacyTestingPlugins= */ Optional.empty());
  }

  @Override
  public Iterable<XProcessingStep> processingSteps() {
    return delegate.processingSteps();
  }

  @Override
  public void postRound(XProcessingEnv env, XRoundEnv roundEnv) {
    delegate.postRound(env, roundEnv);
  }

  /** Provides the {@link KspComponentProcessor}. */
  public static final class Provider implements SymbolProcessorProvider {
    /**
     * Creates a component processor that uses given {@link BindingGraphPlugin}s instead of loading
     * them from a {@link java.util.ServiceLoader}.
     */
    @VisibleForTesting
    public static Provider withTestPlugins(BindingGraphPlugin... testingPlugins) {
      return withTestPlugins(Arrays.asList(testingPlugins));
    }

    /**
     * Creates a component processor that uses given {@link BindingGraphPlugin}s instead of loading
     * them from a {@link java.util.ServiceLoader}.
     */
    @VisibleForTesting
    public static Provider withTestPlugins(Iterable<BindingGraphPlugin> testingPlugins) {
      return new Provider(Optional.of(ImmutableSet.copyOf(testingPlugins)));
    }

    private final Optional<ImmutableSet<BindingGraphPlugin>> testingPlugins;

    public Provider() {
      this(Optional.empty());
    }

    private Provider(Optional<ImmutableSet<BindingGraphPlugin>> testingPlugins) {
      this.testingPlugins = testingPlugins;
    }

    @Override
    public SymbolProcessor create(SymbolProcessorEnvironment symbolProcessorEnvironment) {
      return new KspComponentProcessor(symbolProcessorEnvironment, testingPlugins);
    }
  }
}

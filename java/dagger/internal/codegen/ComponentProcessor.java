/*
 * Copyright (C) 2014 The Dagger Authors.
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

import static net.ltgt.gradle.incap.IncrementalAnnotationProcessorType.ISOLATING;

import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XProcessingStep;
import androidx.room.compiler.processing.XRoundEnv;
import androidx.room.compiler.processing.javac.JavacBasicAnnotationProcessor;
import com.google.auto.service.AutoService;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions;
import dagger.spi.model.BindingGraphPlugin;
import java.util.Arrays;
import java.util.Optional;
import javax.annotation.processing.Processor;
import javax.lang.model.SourceVersion;
import net.ltgt.gradle.incap.IncrementalAnnotationProcessor;

/**
 * The Javac annotation processor responsible for generating the classes that drive the Dagger
 * implementation.
 */
@IncrementalAnnotationProcessor(ISOLATING)
@AutoService(Processor.class)
public final class ComponentProcessor extends JavacBasicAnnotationProcessor {
  /**
   * Creates a component processor that uses given {@link BindingGraphPlugin}s instead of loading
   * them from a {@link java.util.ServiceLoader}.
   */
  @VisibleForTesting
  public static ComponentProcessor withTestPlugins(BindingGraphPlugin... testingPlugins) {
    return withTestPlugins(Arrays.asList(testingPlugins));
  }

  /**
   * Creates a component processor that uses given {@link BindingGraphPlugin}s instead of loading
   * them from a {@link java.util.ServiceLoader}.
   */
  @VisibleForTesting
  public static ComponentProcessor withTestPlugins(Iterable<BindingGraphPlugin> testingPlugins) {
    return new ComponentProcessor(
        Optional.of(ImmutableSet.copyOf(testingPlugins)), Optional.empty());
  }

  /**
   * Creates a component processor that uses given {@link BindingGraphPlugin}s instead of loading
   * them from a {@link java.util.ServiceLoader}.
   */
  @VisibleForTesting
  public static ComponentProcessor forTesting(dagger.spi.BindingGraphPlugin... testingPlugins) {
    return forTesting(Arrays.asList(testingPlugins));
  }

  /**
   * Creates a component processor that uses given {@link BindingGraphPlugin}s instead of loading
   * them from a {@link java.util.ServiceLoader}.
   */
  @VisibleForTesting
  public static ComponentProcessor forTesting(
      Iterable<dagger.spi.BindingGraphPlugin> testingPlugins) {
    return new ComponentProcessor(
        Optional.empty(), Optional.of(ImmutableSet.copyOf(testingPlugins)));
  }

  private final DelegateComponentProcessor delegate = new DelegateComponentProcessor();
  private final Optional<ImmutableSet<BindingGraphPlugin>> testingPlugins;
  private final Optional<ImmutableSet<dagger.spi.BindingGraphPlugin>> legacyTestingPlugins;

  public ComponentProcessor() {
    this(Optional.empty(), Optional.empty());
  }

  private ComponentProcessor(
      Optional<ImmutableSet<BindingGraphPlugin>> testingPlugins,
      Optional<ImmutableSet<dagger.spi.BindingGraphPlugin>> legacyTestingPlugins) {
    super(options -> DelegateComponentProcessor.PROCESSING_ENV_CONFIG);
    this.testingPlugins = testingPlugins;
    this.legacyTestingPlugins = legacyTestingPlugins;
  }

  @Override
  public void initialize(XProcessingEnv env) {
    delegate.initialize(env, testingPlugins, legacyTestingPlugins);
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public ImmutableSet<String> getSupportedOptions() {
    return ImmutableSet.<String>builder()
        .addAll(ProcessingEnvironmentCompilerOptions.supportedOptions())
        .addAll(delegate.validationBindingGraphPlugins.allSupportedOptions())
        .addAll(delegate.externalBindingGraphPlugins.allSupportedOptions())
        .build();
  }

  @Override
  public Iterable<XProcessingStep> processingSteps() {
    return delegate.processingSteps();
  }

  @Override
  public void postRound(XProcessingEnv env, XRoundEnv roundEnv) {
    delegate.postRound(env, roundEnv);
  }
}

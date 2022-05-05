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
import androidx.room.compiler.processing.XProcessingEnvConfig;
import androidx.room.compiler.processing.XProcessingStep;
import androidx.room.compiler.processing.XRoundEnv;
import androidx.room.compiler.processing.javac.JavacBasicAnnotationProcessor;
import com.google.auto.service.AutoService;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CheckReturnValue;
import dagger.BindsInstance;
import dagger.Component;
import dagger.internal.codegen.base.ClearableCache;
import dagger.internal.codegen.base.SourceFileGenerationException;
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.binding.InjectBindingRegistry;
import dagger.internal.codegen.binding.MembersInjectionBinding;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.bindinggraphvalidation.BindingGraphValidationModule;
import dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions;
import dagger.internal.codegen.componentgenerator.ComponentGeneratorModule;
import dagger.internal.codegen.processingstep.ProcessingStepsModule;
import dagger.internal.codegen.validation.BindingMethodValidatorsModule;
import dagger.internal.codegen.validation.ExternalBindingGraphPlugins;
import dagger.internal.codegen.validation.InjectBindingRegistryModule;
import dagger.internal.codegen.validation.ValidationBindingGraphPlugins;
import dagger.spi.BindingGraphPlugin;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.Processor;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.lang.model.SourceVersion;
import net.ltgt.gradle.incap.IncrementalAnnotationProcessor;

/**
 * The annotation processor responsible for generating the classes that drive the Dagger 2.0
 * implementation.
 *
 * <p>TODO(gak): give this some better documentation
 */
@IncrementalAnnotationProcessor(ISOLATING)
@AutoService(Processor.class)
public class ComponentProcessor extends JavacBasicAnnotationProcessor {
  private static XProcessingEnvConfig envConfig(Map<String, String> options)  {
    return new XProcessingEnvConfig.Builder().disableAnnotatedElementValidation(true).build();
  }

  private final Optional<ImmutableSet<BindingGraphPlugin>> testingPlugins;

  @Inject InjectBindingRegistry injectBindingRegistry;
  @Inject SourceFileGenerator<ProvisionBinding> factoryGenerator;
  @Inject SourceFileGenerator<MembersInjectionBinding> membersInjectorGenerator;
  @Inject ImmutableList<XProcessingStep> processingSteps;
  @Inject ValidationBindingGraphPlugins validationBindingGraphPlugins;
  @Inject ExternalBindingGraphPlugins externalBindingGraphPlugins;
  @Inject Set<ClearableCache> clearableCaches;

  public ComponentProcessor() {
    super(ComponentProcessor::envConfig);
    this.testingPlugins = Optional.empty();
  }

  private ComponentProcessor(Iterable<BindingGraphPlugin> testingPlugins) {
    super(ComponentProcessor::envConfig);
    this.testingPlugins = Optional.of(ImmutableSet.copyOf(testingPlugins));
  }

  /**
   * Creates a component processor that uses given {@link BindingGraphPlugin}s instead of loading
   * them from a {@link java.util.ServiceLoader}.
   */
  @VisibleForTesting
  public static ComponentProcessor forTesting(BindingGraphPlugin... testingPlugins) {
    return forTesting(Arrays.asList(testingPlugins));
  }

  /**
   * Creates a component processor that uses given {@link BindingGraphPlugin}s instead of loading
   * them from a {@link java.util.ServiceLoader}.
   */
  @VisibleForTesting
  public static ComponentProcessor forTesting(Iterable<BindingGraphPlugin> testingPlugins) {
    return new ComponentProcessor(testingPlugins);
  }

  @Override
  public void initialize(XProcessingEnv env) {
    ProcessorComponent.factory()
        .create(env, testingPlugins.orElseGet(this::loadExternalPlugins))
        .inject(this);
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public ImmutableSet<String> getSupportedOptions() {
    return ImmutableSet.<String>builder()
        .addAll(ProcessingEnvironmentCompilerOptions.supportedOptions())
        .addAll(validationBindingGraphPlugins.allSupportedOptions())
        .addAll(externalBindingGraphPlugins.allSupportedOptions())
        .build();
  }

  @Override
  public Iterable<XProcessingStep> processingSteps() {
    validationBindingGraphPlugins.initializePlugins();
    externalBindingGraphPlugins.initializePlugins();

    return processingSteps;
  }

  private ImmutableSet<BindingGraphPlugin> loadExternalPlugins() {
    return ServiceLoaders.load(processingEnv, BindingGraphPlugin.class);
  }

  @Singleton
  @Component(
      modules = {
        BindingGraphValidationModule.class,
        BindingMethodValidatorsModule.class,
        ComponentGeneratorModule.class,
        InjectBindingRegistryModule.class,
        ProcessingEnvironmentModule.class,
        ProcessingRoundCacheModule.class,
        ProcessingStepsModule.class,
        SourceFileGeneratorsModule.class,
      })
  interface ProcessorComponent {
    void inject(ComponentProcessor processor);

    static Factory factory() {
      return DaggerComponentProcessor_ProcessorComponent.factory();
    }

    @Component.Factory
    interface Factory {
      @CheckReturnValue
      ProcessorComponent create(
          @BindsInstance XProcessingEnv xProcessingEnv,
          @BindsInstance ImmutableSet<BindingGraphPlugin> externalPlugins);
    }
  }

  @Override
  public void postRound(XProcessingEnv env, XRoundEnv roundEnv) {
    if (!roundEnv.isProcessingOver()) {
      try {
        injectBindingRegistry.generateSourcesForRequiredBindings(
            factoryGenerator, membersInjectorGenerator);
      } catch (SourceFileGenerationException e) {
        e.printMessageTo(env.getMessager());
      }
    }
    clearableCaches.forEach(ClearableCache::clearCache);
  }
}

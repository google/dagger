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

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.auto.service.AutoService;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.googlejavaformat.java.filer.FormattingFiler;
import dagger.Binds;
import dagger.BindsInstance;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.internal.codegen.BindingGraphPlugins.TestingPlugins;
import dagger.spi.BindingGraphPlugin;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.lang.model.SourceVersion;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * The annotation processor responsible for generating the classes that drive the Dagger 2.0
 * implementation.
 *
 * <p>TODO(gak): give this some better documentation
 */
@AutoService(Processor.class)
public class ComponentProcessor extends BasicAnnotationProcessor {
  private final Optional<ImmutableSet<BindingGraphPlugin>> testingPlugins;

  @Inject InjectBindingRegistry injectBindingRegistry;
  @Inject FactoryGenerator factoryGenerator;
  @Inject MembersInjectorGenerator membersInjectorGenerator;
  @Inject ImmutableList<ProcessingStep> processingSteps;
  @Inject BindingGraphPlugins spiPlugins;
  @Inject @Validation BindingGraphPlugins validationPlugins;

  public ComponentProcessor() {
    this.testingPlugins = Optional.empty();
  }

  private ComponentProcessor(Iterable<BindingGraphPlugin> testingPlugins) {
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
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public Set<String> getSupportedOptions() {
    ImmutableSet.Builder<String> options = ImmutableSet.builder();
    options.addAll(CompilerOptions.SUPPORTED_OPTIONS);
    options.addAll(spiPlugins.allSupportedOptions());
    options.addAll(validationPlugins.allSupportedOptions());
    return options.build();
  }

  @Override
  protected Iterable<? extends ProcessingStep> initSteps() {
    Messager messager = processingEnv.getMessager();
    DaggerElements elements = new DaggerElements(processingEnv);
    CompilerOptions compilerOptions = CompilerOptions.create(processingEnv, elements);
    ProcessorComponent.Builder builder =
        DaggerComponentProcessor_ProcessorComponent.builder()
            .types(processingEnv.getTypeUtils())
            .elements(elements)
            .sourceVersion(processingEnv.getSourceVersion())
            .messager(messager)
            .processingOptions(processingEnv.getOptions())
            .compilerOptions(compilerOptions)
            .testingPlugins(testingPlugins);

    Filer filer;
    if (compilerOptions.headerCompilation()) {
      builder.filer(processingEnv.getFiler());
    } else {
      builder.filer(new FormattingFiler(processingEnv.getFiler()));
    }

    ProcessorComponent component = builder.build();
    component.inject(this);
    spiPlugins.initializePlugins();
    validationPlugins.initializePlugins();
    return processingSteps;
  }

  @Singleton
  @Component(
    modules = {
      BindingGraphPluginsModule.class,
      BindingGraphValidationModule.class,
      BindingMethodValidatorsModule.class,
      ProcessingStepsModule.class,
    }
  )
  interface ProcessorComponent {
    void inject(ComponentProcessor processor);

    @CanIgnoreReturnValue
    @Component.Builder
    interface Builder {
      @BindsInstance Builder messager(Messager messager);
      @BindsInstance Builder filer(Filer filer);
      @BindsInstance Builder types(Types types);
      @BindsInstance Builder elements(Elements elements);

      @BindsInstance
      Builder sourceVersion(SourceVersion sourceVersion);

      @BindsInstance Builder compilerOptions(CompilerOptions compilerOptions);
      @BindsInstance Builder processingOptions(@ProcessingOptions Map<String, String> options);

      @BindsInstance
      Builder testingPlugins(
          @TestingPlugins Optional<ImmutableSet<BindingGraphPlugin>> testingPlugins);

      @CheckReturnValue ProcessorComponent build();
    }
  }

  @Module
  interface ProcessingStepsModule {
    @Provides
    static ImmutableList<ProcessingStep> processingSteps(
        MapKeyProcessingStep mapKeyProcessingStep,
        ForReleasableReferencesValidator forReleasableReferencesValidator,
        CanReleaseReferencesProcessingStep canReleaseReferencesProcessingStep,
        InjectProcessingStep injectProcessingStep,
        MonitoringModuleProcessingStep monitoringModuleProcessingStep,
        ProductionExecutorModuleProcessingStep productionExecutorModuleProcessingStep,
        MultibindingAnnotationsProcessingStep multibindingAnnotationsProcessingStep,
        BindsInstanceProcessingStep bindsInstanceProcessingStep,
        ModuleProcessingStep moduleProcessingStep,
        ComponentProcessingStep componentProcessingStep,
        ComponentHjarProcessingStep componentHjarProcessingStep,
        BindingMethodProcessingStep bindingMethodProcessingStep,
        CompilerOptions compilerOptions) {
      return ImmutableList.of(
          mapKeyProcessingStep,
          forReleasableReferencesValidator,
          canReleaseReferencesProcessingStep,
          injectProcessingStep,
          monitoringModuleProcessingStep,
          productionExecutorModuleProcessingStep,
          multibindingAnnotationsProcessingStep,
          bindsInstanceProcessingStep,
          moduleProcessingStep,
          compilerOptions.headerCompilation()
              ? componentHjarProcessingStep
              : componentProcessingStep,
          bindingMethodProcessingStep);
    }

    @Binds
    InjectBindingRegistry injectBindingRegistry(InjectBindingRegistryImpl impl);
  }

  @Override
  protected void postRound(RoundEnvironment roundEnv) {
    if (!roundEnv.processingOver()) {
      try {
        injectBindingRegistry.generateSourcesForRequiredBindings(
            factoryGenerator, membersInjectorGenerator);
      } catch (SourceFileGenerationException e) {
        e.printMessageTo(processingEnv.getMessager());
      }
    }
  }
}

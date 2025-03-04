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

import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XProcessingEnvConfig;
import androidx.room.compiler.processing.XProcessingStep;
import androidx.room.compiler.processing.XRoundEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CheckReturnValue;
import dagger.Binds;
import dagger.BindsInstance;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.internal.codegen.base.ClearableCache;
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.base.SourceFileHjarGenerator;
import dagger.internal.codegen.binding.ComponentDescriptor;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.binding.InjectBindingRegistry;
import dagger.internal.codegen.binding.MembersInjectionBinding;
import dagger.internal.codegen.binding.ModuleDescriptor;
import dagger.internal.codegen.binding.MonitoringModules;
import dagger.internal.codegen.binding.ProductionBinding;
import dagger.internal.codegen.bindinggraphvalidation.BindingGraphValidationModule;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.componentgenerator.ComponentGeneratorModule;
import dagger.internal.codegen.kotlin.KotlinMetadataFactory;
import dagger.internal.codegen.processingstep.ProcessingStepsModule;
import dagger.internal.codegen.validation.AnyBindingMethodValidator;
import dagger.internal.codegen.validation.BindingMethodValidatorsModule;
import dagger.internal.codegen.validation.ComponentCreatorValidator;
import dagger.internal.codegen.validation.ComponentValidator;
import dagger.internal.codegen.validation.External;
import dagger.internal.codegen.validation.ExternalBindingGraphPlugins;
import dagger.internal.codegen.validation.InjectBindingRegistryModule;
import dagger.internal.codegen.validation.InjectValidator;
import dagger.internal.codegen.validation.ValidationBindingGraphPlugins;
import dagger.internal.codegen.writing.FactoryGenerator;
import dagger.internal.codegen.writing.MembersInjectorGenerator;
import dagger.internal.codegen.writing.ModuleGenerator;
import dagger.internal.codegen.writing.ModuleProxies.ModuleConstructorProxyGenerator;
import dagger.internal.codegen.writing.ProducerFactoryGenerator;
import dagger.multibindings.IntoSet;
import dagger.spi.model.BindingGraphPlugin;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.tools.Diagnostic.Kind;

/** An implementation of Dagger's component processor that is shared between Javac and KSP. */
final class DelegateComponentProcessor {
  static final XProcessingEnvConfig PROCESSING_ENV_CONFIG =
      new XProcessingEnvConfig.Builder().disableAnnotatedElementValidation(true).build();

  @Inject InjectBindingRegistry injectBindingRegistry;
  @Inject SourceFileGenerator<ContributionBinding> factoryGenerator;
  @Inject SourceFileGenerator<MembersInjectionBinding> membersInjectorGenerator;
  @Inject ImmutableList<XProcessingStep> processingSteps;
  @Inject ValidationBindingGraphPlugins validationBindingGraphPlugins;
  @Inject ExternalBindingGraphPlugins externalBindingGraphPlugins;
  @Inject Set<ClearableCache> clearableCaches;

  public void initialize(
      XProcessingEnv env,
      Optional<ImmutableSet<BindingGraphPlugin>> testingPlugins,
      Optional<ImmutableSet<dagger.spi.BindingGraphPlugin>> legacyTestingPlugins) {
    ImmutableSet<BindingGraphPlugin> plugins =
        testingPlugins.orElseGet(() -> ServiceLoaders.loadServices(env, BindingGraphPlugin.class));
    ImmutableSet<dagger.spi.BindingGraphPlugin> legacyPlugins =
        legacyTestingPlugins.orElseGet(
            () -> ServiceLoaders.loadServices(env, dagger.spi.BindingGraphPlugin.class));
    if (env.getBackend() != XProcessingEnv.Backend.JAVAC) {
      legacyPlugins.forEach(
          legacyPlugin ->
              env.getMessager()
                  .printMessage(
                      Kind.ERROR,
                      "Cannot use legacy dagger.spi.BindingGraphPlugin while compiling with KSP: "
                          + legacyPlugin.pluginName()
                          + ". Either compile with KAPT or migrate the plugin to implement "
                          + "dagger.spi.model.BindingGraphPlugin."));
      // Even though we've reported an error, processing will still continue for the remainder of
      // the processing round to try to catch other errors. We set the javac plugins to empty to
      // skip processing since it would just result in ClassCastExceptions in KSP.
      legacyPlugins = ImmutableSet.of();
    }
    DaggerDelegateComponentProcessor_Injector.factory()
        .create(env, plugins, legacyPlugins)
        .inject(this);
    validationBindingGraphPlugins.initializePlugins();
    externalBindingGraphPlugins.initializePlugins();
  }

  public Iterable<XProcessingStep> processingSteps() {

    return processingSteps;
  }

  public void onProcessingRoundBegin() {
    externalBindingGraphPlugins.onProcessingRoundBegin();
  }

  public void postRound(XProcessingEnv env, XRoundEnv roundEnv) {
    if (!roundEnv.isProcessingOver()) {
      injectBindingRegistry.generateSourcesForRequiredBindings(
          factoryGenerator, membersInjectorGenerator);
    } else {
      validationBindingGraphPlugins.endPlugins();
      externalBindingGraphPlugins.endPlugins();
    }
    clearableCaches.forEach(ClearableCache::clearCache);
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
  interface Injector {
    void inject(DelegateComponentProcessor processor);

    @Component.Factory
    interface Factory {
      @CheckReturnValue
      Injector create(
          @BindsInstance XProcessingEnv processingEnv,
          @BindsInstance @External ImmutableSet<BindingGraphPlugin> externalPlugins,
          @BindsInstance @External
              ImmutableSet<dagger.spi.BindingGraphPlugin> legacyExternalPlugins);
    }
  }

  @Module
  interface ProcessingRoundCacheModule {
    @Binds
    @IntoSet
    ClearableCache anyBindingMethodValidator(AnyBindingMethodValidator cache);

    @Binds
    @IntoSet
    ClearableCache injectValidator(InjectValidator cache);

    @Binds
    @IntoSet
    ClearableCache moduleDescriptorFactory(ModuleDescriptor.Factory cache);

    @Binds
    @IntoSet
    ClearableCache componentDescriptorFactory(ComponentDescriptor.Factory cache);

    @Binds
    @IntoSet
    ClearableCache monitoringModules(MonitoringModules cache);

    @Binds
    @IntoSet
    ClearableCache componentValidator(ComponentValidator cache);

    @Binds
    @IntoSet
    ClearableCache componentCreatorValidator(ComponentCreatorValidator cache);

    @Binds
    @IntoSet
    ClearableCache kotlinMetadata(KotlinMetadataFactory cache);
  }

  @Module
  interface SourceFileGeneratorsModule {
    @Provides
    static SourceFileGenerator<ContributionBinding> factoryGenerator(
        FactoryGenerator generator,
        CompilerOptions compilerOptions,
        XProcessingEnv processingEnv) {
      return hjarWrapper(generator, compilerOptions, processingEnv);
    }

    @Provides
    static SourceFileGenerator<ProductionBinding> producerFactoryGenerator(
        ProducerFactoryGenerator generator,
        CompilerOptions compilerOptions,
        XProcessingEnv processingEnv) {
      return hjarWrapper(generator, compilerOptions, processingEnv);
    }

    @Provides
    static SourceFileGenerator<MembersInjectionBinding> membersInjectorGenerator(
        MembersInjectorGenerator generator,
        CompilerOptions compilerOptions,
        XProcessingEnv processingEnv) {
      return hjarWrapper(generator, compilerOptions, processingEnv);
    }

    @Provides
    @ModuleGenerator
    static SourceFileGenerator<XTypeElement> moduleConstructorProxyGenerator(
        ModuleConstructorProxyGenerator generator,
        CompilerOptions compilerOptions,
        XProcessingEnv processingEnv) {
      return hjarWrapper(generator, compilerOptions, processingEnv);
    }
  }

  private static <T> SourceFileGenerator<T> hjarWrapper(
      SourceFileGenerator<T> generator,
      CompilerOptions compilerOptions,
      XProcessingEnv processingEnv) {
    return compilerOptions.headerCompilation()
        ? SourceFileHjarGenerator.wrap(generator, processingEnv)
        : generator;
  }
}

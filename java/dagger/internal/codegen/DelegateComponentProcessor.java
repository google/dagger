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
import dagger.internal.codegen.componentgenerator.ComponentGeneratorModule;
import dagger.internal.codegen.processingstep.ProcessingStepsModule;
import dagger.internal.codegen.validation.BindingMethodValidatorsModule;
import dagger.internal.codegen.validation.External;
import dagger.internal.codegen.validation.ExternalBindingGraphPlugins;
import dagger.internal.codegen.validation.InjectBindingRegistryModule;
import dagger.internal.codegen.validation.ValidationBindingGraphPlugins;
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
  @Inject SourceFileGenerator<ProvisionBinding> factoryGenerator;
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
      // We've already reported warnings on the invalid legacy plugins above. We can't actually
      // process these plugins in KSP, so just skip them to allow processing of the valid plugins.
      legacyPlugins = ImmutableSet.of();
    }
    DaggerDelegateComponentProcessor_Injector.factory()
        .create(env, plugins, legacyPlugins)
        .inject(this);
  }

  public Iterable<XProcessingStep> processingSteps() {
    validationBindingGraphPlugins.initializePlugins();
    externalBindingGraphPlugins.initializePlugins();

    return processingSteps;
  }

  public void postRound(XProcessingEnv env, XRoundEnv roundEnv) {
    if (!roundEnv.isProcessingOver()) {
      try {
        injectBindingRegistry.generateSourcesForRequiredBindings(
            factoryGenerator, membersInjectorGenerator);
      } catch (SourceFileGenerationException e) {
        e.printMessageTo(env.getMessager());
      }
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
          @BindsInstance
          @External
          ImmutableSet<dagger.spi.BindingGraphPlugin> legacyExternalPlugins);
    }
  }
}

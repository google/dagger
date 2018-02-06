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

package dagger.internal.codegen;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import dagger.Module;
import dagger.Provides;
import dagger.spi.BindingGraphPlugin;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.inject.Singleton;

/**
 * Provides and configures the {@link BindingGraphPlugin}s available on the annotation processing
 * path.
 */
@Module
interface BindingGraphPluginsModule {
  @Provides
  @Singleton
  static ImmutableList<BindingGraphPlugin> bindingGraphPlugins(
      Filer filer, @ProcessingOptions Map<String, String> processingOptions) {
    ClassLoader classLoader = BindingGraphPluginsModule.class.getClassLoader();
    ImmutableList<BindingGraphPlugin> bindingGraphPlugins =
        ImmutableList.copyOf(ServiceLoader.load(BindingGraphPlugin.class, classLoader));
    for (BindingGraphPlugin plugin : bindingGraphPlugins) {
      plugin.initFiler(filer);
      Set<String> supportedOptions = plugin.supportedOptions();
      if (!supportedOptions.isEmpty()) {
        plugin.initOptions(Maps.filterKeys(processingOptions, supportedOptions::contains));
      }
    }
    return bindingGraphPlugins;
  }
}

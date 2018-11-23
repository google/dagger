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

import com.google.common.collect.ImmutableSet;
import dagger.Module;
import dagger.Provides;
import dagger.internal.codegen.BindingGraphPlugins.TestingPlugins;
import dagger.spi.BindingGraphPlugin;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import javax.annotation.processing.Filer;
import javax.inject.Singleton;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/** Contains the bindings for {@link BindingGraphPlugins} from external SPI providers. */
@Module
abstract class SpiModule {
  private SpiModule() {}

  @Provides
  @Singleton
  static BindingGraphPlugins spiPlugins(
      @TestingPlugins Optional<ImmutableSet<BindingGraphPlugin>> testingPlugins,
      Filer filer,
      Types types,
      Elements elements,
      @ProcessingOptions Map<String, String> processingOptions,
      DiagnosticReporterFactory diagnosticReporterFactory) {
    return new BindingGraphPlugins(
        testingPlugins.orElseGet(SpiModule::loadPlugins),
        filer,
        types,
        elements,
        processingOptions,
        diagnosticReporterFactory);
  }

  private static ImmutableSet<BindingGraphPlugin> loadPlugins() {
    return ImmutableSet.copyOf(
        ServiceLoader.load(BindingGraphPlugin.class, BindingGraphPlugins.class.getClassLoader()));
  }
}

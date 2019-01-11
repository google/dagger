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

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.common.collect.ImmutableSet;
import dagger.Module;
import dagger.Provides;
import dagger.spi.BindingGraphPlugin;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Optional;
import java.util.ServiceLoader;
import javax.inject.Qualifier;
import javax.inject.Singleton;

/** Contains the bindings for {@link BindingGraphValidator} from external SPI providers. */
@Module
abstract class SpiModule {
  private SpiModule() {}

  @Provides
  @Singleton
  static ImmutableSet<BindingGraphPlugin> externalPlugins(
      @TestingPlugins Optional<ImmutableSet<BindingGraphPlugin>> testingPlugins) {
    return testingPlugins.orElseGet(
        () ->
            ImmutableSet.copyOf(
                ServiceLoader.load(
                    BindingGraphPlugin.class, BindingGraphValidator.class.getClassLoader())));
  }

  @Qualifier
  @Retention(RUNTIME)
  @Target({FIELD, PARAMETER, METHOD})
  @interface TestingPlugins {}
}

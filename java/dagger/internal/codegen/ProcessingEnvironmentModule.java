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

import com.google.googlejavaformat.java.filer.FormattingFiler;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.Reusable;
import dagger.internal.codegen.SpiModule.ProcessorClassLoader;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions;
import dagger.internal.codegen.compileroption.ProcessingOptions;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.statistics.DaggerStatisticsRecorder;
import dagger.spi.BindingGraphPlugin;
import java.util.Map;
import java.util.Optional;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.util.Types;

/** Bindings that depend on the {@link ProcessingEnvironment}. */
@Module
interface ProcessingEnvironmentModule {
  @Binds
  @Reusable // to avoid parsing options more than once
  CompilerOptions bindCompilerOptions(
      ProcessingEnvironmentCompilerOptions processingEnvironmentCompilerOptions);

  @Provides
  @ProcessingOptions
  static Map<String, String> processingOptions(ProcessingEnvironment processingEnvironment) {
    return processingEnvironment.getOptions();
  }

  @Provides
  static Messager messager(ProcessingEnvironment processingEnvironment) {
    return processingEnvironment.getMessager();
  }

  @Provides
  static Filer filer(CompilerOptions compilerOptions, ProcessingEnvironment processingEnvironment) {
    if (compilerOptions.headerCompilation() || !compilerOptions.formatGeneratedSource()) {
      return processingEnvironment.getFiler();
    } else {
      return new FormattingFiler(processingEnvironment.getFiler());
    }
  }

  @Provides
  static Types types(ProcessingEnvironment processingEnvironment) {
    return processingEnvironment.getTypeUtils();
  }

  @Provides
  static SourceVersion sourceVersion(ProcessingEnvironment processingEnvironment) {
    return processingEnvironment.getSourceVersion();
  }

  @Provides
  static DaggerElements daggerElements(ProcessingEnvironment processingEnvironment) {
    return new DaggerElements(processingEnvironment);
  }

  @Provides
  static Optional<DaggerStatisticsRecorder> daggerStatisticsRecorder(
      ProcessingEnvironment processingEnvironment) {
    return Optional.empty();
  }

  @Provides
  @ProcessorClassLoader
  static ClassLoader processorClassloader(ProcessingEnvironment processingEnvironment) {
    return BindingGraphPlugin.class.getClassLoader();
  }

}

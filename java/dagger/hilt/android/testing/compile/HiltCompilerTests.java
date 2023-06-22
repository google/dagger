/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.hilt.android.testing.compile;

import static java.util.stream.Collectors.toMap;

import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.util.CompilationResultSubject;
import androidx.room.compiler.processing.util.ProcessorTestExtKt;
import androidx.room.compiler.processing.util.Source;
import androidx.room.compiler.processing.util.compiler.TestCompilationArguments;
import androidx.room.compiler.processing.util.compiler.TestCompilationResult;
import androidx.room.compiler.processing.util.compiler.TestKotlinCompilerKt;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.ksp.processing.SymbolProcessorProvider;
import com.google.testing.compile.Compiler;
import dagger.hilt.android.processor.internal.androidentrypoint.AndroidEntryPointProcessor;
import dagger.hilt.android.processor.internal.androidentrypoint.KspAndroidEntryPointProcessor;
import dagger.hilt.android.processor.internal.customtestapplication.CustomTestApplicationProcessor;
import dagger.hilt.processor.internal.HiltProcessingEnvConfigs;
import dagger.hilt.processor.internal.aggregateddeps.AggregatedDepsProcessor;
import dagger.hilt.processor.internal.aliasof.AliasOfProcessor;
import dagger.hilt.processor.internal.aliasof.KspAliasOfProcessor;
import dagger.hilt.processor.internal.definecomponent.DefineComponentProcessor;
import dagger.hilt.processor.internal.earlyentrypoint.EarlyEntryPointProcessor;
import dagger.hilt.processor.internal.generatesrootinput.GeneratesRootInputProcessor;
import dagger.hilt.processor.internal.originatingelement.OriginatingElementProcessor;
import dagger.hilt.processor.internal.root.ComponentTreeDepsProcessor;
import dagger.hilt.processor.internal.root.RootProcessor;
import dagger.hilt.processor.internal.uninstallmodules.UninstallModulesProcessor;
import dagger.internal.codegen.ComponentProcessor;
import dagger.testing.compile.CompilerTests;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.processing.Processor;
import org.junit.rules.TemporaryFolder;

/** {@link Compiler} instances for testing Android Hilt. */
public final class HiltCompilerTests {
  /** Returns the {@link XProcessingEnv.Backend} for the given {@link CompilationResultSubject}. */
  public static XProcessingEnv.Backend backend(CompilationResultSubject subject) {
    return CompilerTests.backend(subject);
  }

  /** Returns a {@link Source.KotlinSource} with the given file name and content. */
  public static Source.KotlinSource kotlinSource(
      String fileName, ImmutableCollection<String> srcLines) {
    return CompilerTests.kotlinSource(fileName, srcLines);
  }

  /** Returns a {@link Source.KotlinSource} with the given file name and content. */
  public static Source.KotlinSource kotlinSource(String fileName, String... srcLines) {
    return CompilerTests.kotlinSource(fileName, srcLines);
  }

  /** Returns a {@link Source.JavaSource} with the given file name and content. */
  public static Source.JavaSource javaSource(
      String fileName, ImmutableCollection<String> srcLines) {
    return CompilerTests.javaSource(fileName, srcLines);
  }

  /** Returns a {@link Source.JavaSource} with the given file name and content. */
  public static Source.JavaSource javaSource(String fileName, String... srcLines) {
    return CompilerTests.javaSource(fileName, srcLines);
  }

  /** Returns a {@link Compiler} instance with the given sources. */
  public static HiltCompiler hiltCompiler(Source... sources) {
    return hiltCompiler(ImmutableList.copyOf(sources));
  }

  /** Returns a {@link Compiler} instance with the given sources. */
  public static HiltCompiler hiltCompiler(ImmutableCollection<Source> sources) {
    return HiltCompiler.builder().sources(sources).build();
  }

  public static Compiler compiler(Processor... extraProcessors) {
    return compiler(Arrays.asList(extraProcessors));
  }

  public static Compiler compiler(Collection<? extends Processor> extraProcessors) {
    Map<Class<?>, Processor> processors =
        defaultProcessors().stream()
            .collect(toMap((Processor e) -> e.getClass(), (Processor e) -> e));

    // Adds extra processors, and allows overriding any processors of the same class.
    extraProcessors.stream().forEach(processor -> processors.put(processor.getClass(), processor));

    return CompilerTests.compiler().withProcessors(processors.values());
  }

  public static void compileWithKapt(
      List<Source> sources,
      TemporaryFolder tempFolder,
      Consumer<TestCompilationResult> onCompilationResult) {
    compileWithKapt(sources, ImmutableMap.of(), tempFolder, onCompilationResult);
  }

  public static void compileWithKapt(
      List<Source> sources,
      Map<String, String> processorOptions,
      TemporaryFolder tempFolder,
      Consumer<TestCompilationResult> onCompilationResult) {
    TestCompilationResult result = TestKotlinCompilerKt.compile(
        tempFolder.getRoot(),
        new TestCompilationArguments(
            sources,
            /*classpath=*/ ImmutableList.of(CompilerTests.compilerDepsJar()),
            /*inheritClasspath=*/ false,
            /*javacArguments=*/ ImmutableList.of(),
            /*kotlincArguments=*/ ImmutableList.of(),
            /*kaptProcessors=*/ defaultProcessors(),
            /*symbolProcessorProviders=*/ ImmutableList.of(),
            /*processorOptions=*/ processorOptions));
    onCompilationResult.accept(result);
  }

  static ImmutableList<Processor> defaultProcessors() {
    return ImmutableList.of(
        new AggregatedDepsProcessor(),
        new AliasOfProcessor(),
        new AndroidEntryPointProcessor(),
        new ComponentProcessor(),
        new ComponentTreeDepsProcessor(),
        new CustomTestApplicationProcessor(),
        new DefineComponentProcessor(),
        new EarlyEntryPointProcessor(),
        new GeneratesRootInputProcessor(),
        new OriginatingElementProcessor(),
        new RootProcessor(),
        new UninstallModulesProcessor());
  }

  private static ImmutableList<SymbolProcessorProvider> kspDefaultProcessors() {
    // TODO(bcorso): Add the rest of the KSP processors here.
    return ImmutableList.of(
        new KspAndroidEntryPointProcessor.Provider(),
        new KspAliasOfProcessor.Provider());
  }

  /** Used to compile Hilt sources and inspect the compiled results. */
  @AutoValue
  public abstract static class HiltCompiler {
    static Builder builder() {
      return new AutoValue_HiltCompilerTests_HiltCompiler.Builder();
    }

    /** Returns the sources being compiled */
    abstract ImmutableCollection<Source> sources();

    /** Returns a builder with the current values of this {@link Compiler} as default. */
    abstract Builder toBuilder();

    public void compile(Consumer<CompilationResultSubject> onCompilationResult) {
      ProcessorTestExtKt.runProcessorTest(
          sources().asList(),
          /* classpath= */ ImmutableList.of(CompilerTests.compilerDepsJar()),
          /* options= */ ImmutableMap.of(),
          /* javacArguments= */ ImmutableList.of(),
          /* kotlincArguments= */ ImmutableList.of(
              "-P", "plugin:org.jetbrains.kotlin.kapt3:correctErrorTypes=true"),
          /* config= */ HiltProcessingEnvConfigs.CONFIGS,
          /* javacProcessors= */ defaultProcessors(),
          /* symbolProcessorProviders= */ kspDefaultProcessors(),
          result -> {
            onCompilationResult.accept(result);
            return null;
          });
    }

    /** Used to build a {@link DaggerCompiler}. */
    @AutoValue.Builder
    public abstract static class Builder {
      abstract Builder sources(ImmutableCollection<Source> sources);

      abstract HiltCompiler build();
    }
  }

  private HiltCompilerTests() {}
}

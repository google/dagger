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

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
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
import dagger.hilt.android.processor.internal.customtestapplication.KspCustomTestApplicationProcessor;
import dagger.hilt.processor.internal.BaseProcessingStep;
import dagger.hilt.processor.internal.HiltProcessingEnvConfigs;
import dagger.hilt.processor.internal.aggregateddeps.AggregatedDepsProcessor;
import dagger.hilt.processor.internal.aggregateddeps.KspAggregatedDepsProcessor;
import dagger.hilt.processor.internal.aliasof.AliasOfProcessor;
import dagger.hilt.processor.internal.aliasof.KspAliasOfProcessor;
import dagger.hilt.processor.internal.definecomponent.DefineComponentProcessor;
import dagger.hilt.processor.internal.definecomponent.KspDefineComponentProcessor;
import dagger.hilt.processor.internal.earlyentrypoint.EarlyEntryPointProcessor;
import dagger.hilt.processor.internal.earlyentrypoint.KspEarlyEntryPointProcessor;
import dagger.hilt.processor.internal.generatesrootinput.GeneratesRootInputProcessor;
import dagger.hilt.processor.internal.generatesrootinput.KspGeneratesRootInputProcessor;
import dagger.hilt.processor.internal.originatingelement.KspOriginatingElementProcessor;
import dagger.hilt.processor.internal.originatingelement.OriginatingElementProcessor;
import dagger.hilt.processor.internal.root.ComponentTreeDepsProcessor;
import dagger.hilt.processor.internal.root.KspComponentTreeDepsProcessor;
import dagger.hilt.processor.internal.root.KspRootProcessor;
import dagger.hilt.processor.internal.root.RootProcessor;
import dagger.hilt.processor.internal.uninstallmodules.KspUninstallModulesProcessor;
import dagger.hilt.processor.internal.uninstallmodules.UninstallModulesProcessor;
import dagger.internal.codegen.ComponentProcessor;
import dagger.internal.codegen.KspComponentProcessor;
import dagger.testing.compile.CompilerTests;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.processing.Processor;
import org.junit.rules.TemporaryFolder;

/** {@link Compiler} instances for testing Android Hilt. */
public final class HiltCompilerTests {
  private static final ImmutableList<String> DEFAULT_JAVAC_OPTIONS = ImmutableList.of();

  private static final ImmutableList<String> DEFAULT_KOTLINC_OPTIONS =
      ImmutableList.of(
          "-jvm-target=11",
          "-Xjvm-default=all",
          "-P",
          "plugin:org.jetbrains.kotlin.kapt3:correctErrorTypes=true");

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
    extraProcessors.forEach(processor -> processors.put(processor.getClass(), processor));

    return CompilerTests.compiler().withProcessors(processors.values());
  }

  public static void compileWithKapt(
      List<Source> sources,
      TemporaryFolder tempFolder,
      Consumer<TestCompilationResult> onCompilationResult) {
    compileWithKapt(
        sources, ImmutableMap.of(), ImmutableList.of(), tempFolder, onCompilationResult);
  }

  public static void compileWithKapt(
      List<Source> sources,
      Map<String, String> processorOptions,
      TemporaryFolder tempFolder,
      Consumer<TestCompilationResult> onCompilationResult) {
    compileWithKapt(
        sources, processorOptions, ImmutableList.of(), tempFolder, onCompilationResult);
  }

  public static void compileWithKapt(
      List<Source> sources,
      List<Processor> additionalProcessors,
      TemporaryFolder tempFolder,
      Consumer<TestCompilationResult> onCompilationResult) {
    compileWithKapt(
        sources, ImmutableMap.of(), additionalProcessors, tempFolder, onCompilationResult);
  }

  public static void compileWithKapt(
      List<Source> sources,
      Map<String, String> processorOptions,
      List<Processor> additionalProcessors,
      TemporaryFolder tempFolder,
      Consumer<TestCompilationResult> onCompilationResult) {
    TestCompilationResult result =
        TestKotlinCompilerKt.compile(
            tempFolder.getRoot(),
            new TestCompilationArguments(
                sources,
                /* classpath= */ ImmutableList.of(CompilerTests.compilerDepsJar()),
                /* inheritClasspath= */ false,
                /* javacArguments= */ DEFAULT_JAVAC_OPTIONS,
                /* kotlincArguments= */ DEFAULT_KOTLINC_OPTIONS,
                /* kaptProcessors= */ ImmutableList.<Processor>builder()
                    .addAll(defaultProcessors())
                    .addAll(additionalProcessors)
                    .build(),
                /* symbolProcessorProviders= */ ImmutableList.of(),
                /* processorOptions= */ processorOptions));
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
        new UninstallModulesProcessor(),
        new GeneratesRootInputProcessor(),
        new OriginatingElementProcessor(),
        new RootProcessor());
  }

  private static ImmutableList<SymbolProcessorProvider> kspDefaultProcessors() {
    // TODO(bcorso): Add the rest of the KSP processors here.
    return ImmutableList.of(
        new KspAggregatedDepsProcessor.Provider(),
        new KspAliasOfProcessor.Provider(),
        new KspAndroidEntryPointProcessor.Provider(),
        new KspComponentProcessor.Provider(),
        new KspComponentTreeDepsProcessor.Provider(),
        new KspCustomTestApplicationProcessor.Provider(),
        new KspDefineComponentProcessor.Provider(),
        new KspEarlyEntryPointProcessor.Provider(),
        new KspUninstallModulesProcessor.Provider(),
        new KspGeneratesRootInputProcessor.Provider(),
        new KspOriginatingElementProcessor.Provider(),
        new KspRootProcessor.Provider());
  }

  /** Used to compile Hilt sources and inspect the compiled results. */
  @AutoValue
  public abstract static class HiltCompiler {
    static Builder builder() {
      return new AutoValue_HiltCompilerTests_HiltCompiler.Builder()
          // Set the builder defaults.
          .processorOptions(ImmutableMap.of())
          .additionalJavacProcessors(ImmutableList.of())
          .additionalKspProcessors(ImmutableList.of())
          .processingSteps(ImmutableList.of())
          .javacArguments(ImmutableList.of());
    }

    /** Returns the sources being compiled */
    abstract ImmutableCollection<Source> sources();

    /** Returns the annotation processors options. */
    abstract ImmutableMap<String, String> processorOptions();

    /** Returns the extra Javac processors. */
    abstract ImmutableCollection<Processor> additionalJavacProcessors();

    /** Returns the extra KSP processors. */
    abstract ImmutableCollection<SymbolProcessorProvider> additionalKspProcessors();

    /** Returns the command-line options */
    abstract ImmutableCollection<String> javacArguments();

    /** Returns a new {@link HiltCompiler} instance with the annotation processors options. */
    public HiltCompiler withProcessorOptions(ImmutableMap<String, String> processorOptions) {
      return toBuilder().processorOptions(processorOptions).build();
    }

    /** Returns the processing steps suppliers. */
    abstract ImmutableCollection<Function<XProcessingEnv, BaseProcessingStep>> processingSteps();

    public HiltCompiler withProcessingSteps(
        Function<XProcessingEnv, BaseProcessingStep>... mapping) {
      return toBuilder().processingSteps(ImmutableList.copyOf(mapping)).build();
    }

    /** Returns a new {@link HiltCompiler} instance with the additional Javac processors. */
    public HiltCompiler withAdditionalJavacProcessors(Processor... processors) {
      return toBuilder().additionalJavacProcessors(ImmutableList.copyOf(processors)).build();
    }

    /** Returns a new {@link HiltCompiler} instance with the additional KSP processors. */
    public HiltCompiler withAdditionalKspProcessors(SymbolProcessorProvider... processors) {
      return toBuilder().additionalKspProcessors(ImmutableList.copyOf(processors)).build();
    }

    /** Returns a new {@link HiltCompiler} instance with command-line options. */
    public HiltCompiler withJavacArguments(String... arguments) {
      return toBuilder().javacArguments(ImmutableList.copyOf(arguments)).build();
    }

    /** Returns a new {@link HiltCompiler} instance with command-line options. */
    public HiltCompiler withJavacArguments(ImmutableCollection<String> arguments) {
      return toBuilder().javacArguments(arguments).build();
    }

    /** Returns a builder with the current values of this {@link Compiler} as default. */
    abstract Builder toBuilder();

    public void compile(Consumer<CompilationResultSubject> onCompilationResult) {
      compileInternal(onCompilationResult, DEFAULT_KOTLINC_OPTIONS);
    }

    private void compileInternal(
        Consumer<CompilationResultSubject> onCompilationResult,
        ImmutableList<String> kotlincArguments) {
      ProcessorTestExtKt.runProcessorTest(
          sources().asList(),
          /* classpath= */ ImmutableList.of(CompilerTests.compilerDepsJar()),
          /* options= */ processorOptions(),
          /* javacArguments= */
          ImmutableList.<String>builder()
              .addAll(DEFAULT_JAVAC_OPTIONS)
              .addAll(javacArguments())
              .build(),
          /* kotlincArguments= */ kotlincArguments,
          /* config= */ HiltProcessingEnvConfigs.CONFIGS,
          /* javacProcessors= */ ImmutableList.<Processor>builder()
              .addAll(mergeProcessors(defaultProcessors(), additionalJavacProcessors()))
              .addAll(
                  processingSteps().stream()
                      .map(HiltCompilerProcessors.JavacProcessor::new)
                      .collect(toImmutableList()))
              .build(),
          /* symbolProcessorProviders= */ ImmutableList.<SymbolProcessorProvider>builder()
              .addAll(mergeProcessors(kspDefaultProcessors(), additionalKspProcessors()))
              .addAll(
                  processingSteps().stream()
                      .map(HiltCompilerProcessors.KspProcessor.Provider::new)
                      .collect(toImmutableList()))
              .build(),
          result -> {
            onCompilationResult.accept(result);
            return null;
          });
    }

    private static <T> ImmutableList<T> mergeProcessors(
        Collection<T> defaultProcessors, Collection<T> extraProcessors) {
      Map<Class<?>, T> processors =
          defaultProcessors.stream().collect(toMap((T e) -> e.getClass(), (T e) -> e));
      // Adds extra processors, and allows overriding any processors of the same class.
      extraProcessors.forEach(processor -> processors.put(processor.getClass(), processor));
      return ImmutableList.copyOf(processors.values());
    }

    /** Used to build a {@link HiltCompiler}. */
    @AutoValue.Builder
    public abstract static class Builder {
      abstract Builder sources(ImmutableCollection<Source> sources);
      abstract Builder processorOptions(ImmutableMap<String, String> processorOptions);
      abstract Builder additionalJavacProcessors(ImmutableCollection<Processor> processors);
      abstract Builder additionalKspProcessors(
          ImmutableCollection<SymbolProcessorProvider> processors);
      abstract Builder javacArguments(ImmutableCollection<String> arguments);

      abstract Builder processingSteps(
          ImmutableCollection<Function<XProcessingEnv, BaseProcessingStep>> processingSteps);

      abstract HiltCompiler build();
    }
  }

  private HiltCompilerTests() {}
}

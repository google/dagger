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

package dagger.testing.compile;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.MoreCollectors.onlyElement;
import static com.google.common.collect.Streams.stream;
import static com.google.testing.compile.Compiler.javac;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static java.util.stream.Collectors.toMap;

import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XProcessingEnvConfig;
import androidx.room.compiler.processing.XProcessingStep;
import androidx.room.compiler.processing.util.CompilationResultSubject;
import androidx.room.compiler.processing.util.ProcessorTestExtKt;
import androidx.room.compiler.processing.util.Source;
import androidx.room.compiler.processing.util.XTestInvocation;
import androidx.room.compiler.processing.util.compiler.TestCompilationArguments;
import androidx.room.compiler.processing.util.compiler.TestCompilationResult;
import androidx.room.compiler.processing.util.compiler.TestKotlinCompilerKt;
import com.google.auto.value.AutoValue;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import com.google.devtools.ksp.processing.SymbolProcessorProvider;
import com.google.testing.compile.Compiler;
import dagger.internal.codegen.ComponentProcessor;
import dagger.internal.codegen.KspComponentProcessor;
import dagger.spi.model.BindingGraphPlugin;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import javax.annotation.processing.Processor;
import org.junit.rules.TemporaryFolder;

/** A helper class for working with java compiler tests. */
public final class CompilerTests {
  // TODO(bcorso): Share this with java/dagger/internal/codegen/DelegateComponentProcessor.java
  static final XProcessingEnvConfig PROCESSING_ENV_CONFIG =
      new XProcessingEnvConfig.Builder().disableAnnotatedElementValidation(true).build();

  // TODO(bcorso): Share this with javatests/dagger/internal/codegen/Compilers.java
  private static final ImmutableMap<String, String> DEFAULT_PROCESSOR_OPTIONS =
      ImmutableMap.of(
          "dagger.experimentalDaggerErrorMessages", "enabled");

  /** Returns the {@link XProcessingEnv.Backend} for the given {@link CompilationResultSubject}. */
  public static XProcessingEnv.Backend backend(CompilationResultSubject subject) {
    // TODO(bcorso): Create a more official API for this in XProcessing testing.
    String output = subject.getCompilationResult().toString();
    if (output.startsWith("CompilationResult (with ksp)")) {
      return XProcessingEnv.Backend.KSP;
    } else if (output.startsWith("CompilationResult (with javac)")
                   || output.startsWith("CompilationResult (with kapt)")) {
      return XProcessingEnv.Backend.JAVAC;
    }
    throw new AssertionError("Unexpected backend for subject.");
  }

  /** Returns a {@link Source.KotlinSource} with the given file name and content. */
  public static Source.KotlinSource kotlinSource(
      String fileName, ImmutableCollection<String> srcLines) {
    return (Source.KotlinSource) Source.Companion.kotlin(fileName, String.join("\n", srcLines));
  }

  /** Returns a {@link Source.KotlinSource} with the given file name and content. */
  public static Source.KotlinSource kotlinSource(String fileName, String... srcLines) {
    return (Source.KotlinSource) Source.Companion.kotlin(fileName, String.join("\n", srcLines));
  }

  /** Returns a {@link Source.JavaSource} with the given file name and content. */
  public static Source.JavaSource javaSource(
      String fileName, ImmutableCollection<String> srcLines) {
    return (Source.JavaSource) Source.Companion.java(fileName, String.join("\n", srcLines));
  }

  /** Returns a {@link Source.JavaSource} with the given file name and content. */
  public static Source.JavaSource javaSource(String fileName, String... srcLines) {
    return (Source.JavaSource) Source.Companion.java(fileName, String.join("\n", srcLines));
  }

  /** Returns a {@link Compiler} instance with the given sources. */
  public static DaggerCompiler daggerCompiler(Source... sources) {
    return daggerCompiler(ImmutableList.copyOf(sources));
  }

  /** Returns a {@link Compiler} instance with the given sources. */
  public static DaggerCompiler daggerCompiler(ImmutableCollection<Source> sources) {
    return DaggerCompiler.builder().sources(sources).build();
  }

  /**
   * Used to compile regular java or kotlin sources and inspect the elements processed in the test
   * processing environment.
   */
  public static InvocationCompiler invocationCompiler(Source... sources) {
    return new AutoValue_CompilerTests_InvocationCompiler(
        ImmutableList.copyOf(sources), DEFAULT_PROCESSOR_OPTIONS);
  }

  /** */
  @AutoValue
  public abstract static class InvocationCompiler {
    /** Returns the sources being compiled */
    abstract ImmutableList<Source> sources();

    /** Returns the annotation processor options */
    abstract ImmutableMap<String, String> processorOptions();

    public void compile(Consumer<XTestInvocation> onInvocation) {
      ProcessorTestExtKt.runProcessorTest(
          sources(),
          /* classpath= */ ImmutableList.of(),
          processorOptions(),
          /* javacArguments= */ ImmutableList.of(),
          /* kotlincArguments= */ ImmutableList.of(),
          /* config= */ PROCESSING_ENV_CONFIG,
          invocation -> {
            onInvocation.accept(invocation);
            return null;
          });
    }
  }

  /** Used to compile Dagger sources and inspect the compiled results. */
  @AutoValue
  public abstract static class DaggerCompiler {
    static Builder builder() {
      Builder builder = new AutoValue_CompilerTests_DaggerCompiler.Builder();
      // Set default values
      return builder
          .processorOptions(DEFAULT_PROCESSOR_OPTIONS)
          .additionalJavacProcessors(ImmutableList.of())
          .additionalKspProcessors(ImmutableList.of())
          .processingStepSuppliers(ImmutableSet.of())
          .bindingGraphPluginSuppliers(ImmutableSet.of());
    }

    /** Returns the sources being compiled */
    abstract ImmutableCollection<Source> sources();

    /** Returns the annotation processor options */
    abstract ImmutableMap<String, String> processorOptions();

    /** Returns the extra Javac processors. */
    abstract ImmutableCollection<Processor> additionalJavacProcessors();

    /** Returns the extra KSP processors. */
    abstract ImmutableCollection<SymbolProcessorProvider> additionalKspProcessors();

    /** Returns the processing steps suppliers. */
    abstract ImmutableCollection<Supplier<XProcessingStep>> processingStepSuppliers();

    /** Returns the processing steps. */
    private ImmutableList<XProcessingStep> processingSteps() {
      return processingStepSuppliers().stream().map(Supplier::get).collect(toImmutableList());
    }

    /** Returns the {@link BindingGraphPlugin} suppliers. */
    abstract ImmutableCollection<Supplier<BindingGraphPlugin>> bindingGraphPluginSuppliers();

    /** Returns the {@link BindingGraphPlugin}s. */
    private ImmutableList<BindingGraphPlugin> bindingGraphPlugins() {
      return bindingGraphPluginSuppliers().stream().map(Supplier::get).collect(toImmutableList());
    }

    /** Returns a builder with the current values of this {@link Compiler} as default. */
    abstract Builder toBuilder();

    /**
     * Returns a new {@link Compiler} instance with the given processor options.
     *
     * <p>Note that the default processor options are still applied unless they are explicitly
     * overridden by the given processing options.
     */
    public DaggerCompiler withProcessingOptions(Map<String, String> processorOptions) {
      // Add default processor options first to allow overridding with new key-value pairs.
      Map<String, String> newProcessorOptions = new HashMap<>(DEFAULT_PROCESSOR_OPTIONS);
      newProcessorOptions.putAll(processorOptions);
      return toBuilder().processorOptions(newProcessorOptions).build();
    }

    /** Returns a new {@link HiltCompiler} instance with the additional Javac processors. */
    public DaggerCompiler withAdditionalJavacProcessors(Processor... processors) {
      return toBuilder().additionalJavacProcessors(ImmutableList.copyOf(processors)).build();
    }

    /** Returns a new {@link HiltCompiler} instance with the additional KSP processors. */
    public DaggerCompiler withAdditionalKspProcessors(SymbolProcessorProvider... processors) {
      return toBuilder().additionalKspProcessors(ImmutableList.copyOf(processors)).build();
    }

    /** Returns a new {@link Compiler} instance with the given processing steps. */
    public DaggerCompiler withProcessingSteps(Supplier<XProcessingStep>... suppliers) {
      return toBuilder().processingStepSuppliers(ImmutableList.copyOf(suppliers)).build();
    }

    public DaggerCompiler withBindingGraphPlugins(Supplier<BindingGraphPlugin>... suppliers) {
      return toBuilder().bindingGraphPluginSuppliers(ImmutableList.copyOf(suppliers)).build();
    }

    public void compile(Consumer<CompilationResultSubject> onCompilationResult) {
      ProcessorTestExtKt.runProcessorTest(
          sources().asList(),
          /* classpath= */ ImmutableList.of(),
          processorOptions(),
          /* javacArguments= */ ImmutableList.of(),
          /* kotlincArguments= */ ImmutableList.of(
              "-P", "plugin:org.jetbrains.kotlin.kapt3:correctErrorTypes=true"),
          /* config= */ PROCESSING_ENV_CONFIG,
          /* javacProcessors= */ mergeProcessors(
              ImmutableList.of(
                  ComponentProcessor.withTestPlugins(bindingGraphPlugins()),
                  new CompilerProcessors.JavacProcessor(processingSteps())),
              additionalJavacProcessors()),
          /* symbolProcessorProviders= */ mergeProcessors(
              ImmutableList.of(
                  KspComponentProcessor.Provider.withTestPlugins(bindingGraphPlugins()),
                  new CompilerProcessors.KspProcessor.Provider(processingSteps())),
              additionalKspProcessors()),
          result -> {
            onCompilationResult.accept(result);
            return null;
          });
    }

    private static <T> ImmutableList<T> mergeProcessors(
        Collection<T> defaultProcessors, Collection<T> extraProcessors) {
      Map<Class<?>, T> processors =
          defaultProcessors.stream()
              .collect(toMap(Object::getClass, (T e) -> e, (p1, p2) -> p2, HashMap::new));
      // Adds extra processors, and allows overriding any processors of the same class.
      extraProcessors.forEach(processor -> processors.put(processor.getClass(), processor));
      return ImmutableList.copyOf(processors.values());
    }

    /** Used to build a {@link DaggerCompiler}. */
    @AutoValue.Builder
    public abstract static class Builder {
      abstract Builder sources(ImmutableCollection<Source> sources);
      abstract Builder processorOptions(Map<String, String> processorOptions);

      abstract Builder additionalJavacProcessors(ImmutableCollection<Processor> processors);

      abstract Builder additionalKspProcessors(
          ImmutableCollection<SymbolProcessorProvider> processors);

      abstract Builder processingStepSuppliers(
          ImmutableCollection<Supplier<XProcessingStep>> processingStepSuppliers);
      abstract Builder bindingGraphPluginSuppliers(
          ImmutableCollection<Supplier<BindingGraphPlugin>> bindingGraphPluginSuppliers);
      abstract DaggerCompiler build();
    }
  }

  /** Returns the {@plainlink File jar file} containing the compiler deps. */
  public static File compilerDepsJar() {
    try {
      return stream(Files.fileTraverser().breadthFirst(getRunfilesDir()))
          .filter(file -> file.getName().endsWith("_compiler_deps_deploy.jar"))
          .collect(onlyElement());
    } catch (NoSuchElementException e) {
      throw new IllegalStateException(
          "No compiler deps jar found. Are you using the Dagger compiler_test macro?", e);
    }
  }

  /** Returns a {@link Compiler} with the compiler deps jar added to the class path. */
  public static Compiler compiler() {
    return javac().withClasspath(ImmutableList.of(compilerDepsJar()));
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
            /*classpath=*/ ImmutableList.of(compilerDepsJar()),
            /*inheritClasspath=*/ false,
            /*javacArguments=*/ ImmutableList.of(),
            /*kotlincArguments=*/ ImmutableList.of(),
            /*kaptProcessors=*/ ImmutableList.of(new ComponentProcessor()),
            /*symbolProcessorProviders=*/ ImmutableList.of(),
            /*processorOptions=*/ processorOptions));
    onCompilationResult.accept(result);
  }

  private static File getRunfilesDir() {
    return getRunfilesPath().toFile();
  }

  private static Path getRunfilesPath() {
    Path propPath = getRunfilesPath(System.getProperties());
    if (propPath != null) {
      return propPath;
    }

    Path envPath = getRunfilesPath(System.getenv());
    if (envPath != null) {
      return envPath;
    }

    Path cwd = Paths.get("").toAbsolutePath();
    return cwd.getParent();
  }

  private static Path getRunfilesPath(Map<?, ?> map) {
    String runfilesPath = (String) map.get("TEST_SRCDIR");
    return isNullOrEmpty(runfilesPath) ? null : Paths.get(runfilesPath);
  }

  private CompilerTests() {}
}

/*
 * Copyright (C) 2016 The Dagger Authors.
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

import static com.google.common.base.StandardSystemProperty.JAVA_CLASS_PATH;
import static com.google.common.base.StandardSystemProperty.PATH_SEPARATOR;
import static com.google.testing.compile.Compiler.javac;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

import com.google.auto.value.processor.AutoAnnotationProcessor;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.testing.compile.Compiler;
import java.io.File;
import java.util.Arrays;
import javax.annotation.processing.Processor;

/** {@link Compiler} instances for testing Dagger. */
public final class Compilers {
  private static final String GUAVA = "guava";

  static final ImmutableList<File> CLASS_PATH_WITHOUT_GUAVA_OPTION =
      Splitter.on(PATH_SEPARATOR.value()).splitToList(JAVA_CLASS_PATH.value()).stream()
          .filter(jar -> !jar.contains(GUAVA))
          // Remove Bazel's runner deploy jar which leaks Guava classes into the classpath and
          // the compile testing tests.
          .filter(jar -> !jar.contains("Runner_deploy.jar"))
          // Remove the kotlin-compiler jar which leaks Guava classes into the classpath and
          // the compile testing tests.
          .filter(jar -> !jar.contains("kotlin-compiler.jar"))
          .map(File::new)
          .collect(collectingAndThen(toList(), ImmutableList::copyOf));

  static final ImmutableList<String> DEFAULT_JAVACOPTS =
      ImmutableList.of(
          "-Adagger.experimentalDaggerErrorMessages=enabled");

  /**
   * Returns a compiler that runs the Dagger and {@code @AutoAnnotation} processors, along with
   * extras.
   */
  public static Compiler daggerCompiler(Processor... extraProcessors) {
    ImmutableList.Builder<Processor> processors = ImmutableList.builder();
    processors.add(new ComponentProcessor(), new AutoAnnotationProcessor());
    processors.add(extraProcessors);
    return javac().withProcessors(processors.build()).withOptions(DEFAULT_JAVACOPTS);
  }

  public static Compiler compilerWithOptions(CompilerMode... compilerModes) {
    ImmutableList.Builder<String> options = ImmutableList.builder();
    for (CompilerMode compilerMode : compilerModes) {
      options = options.addAll(compilerMode.javacopts());
    }
    return compilerWithOptions(options.build());
  }

  public static Compiler compilerWithOptions(String... options) {
    return compilerWithOptions(Arrays.asList(options));
  }

  public static Compiler compilerWithOptions(Iterable<String> options) {
    return daggerCompiler()
        .withOptions(ImmutableList.builder().addAll(DEFAULT_JAVACOPTS).addAll(options).build());
  }

  private Compilers() {}
}

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
import static java.util.stream.Collectors.joining;

import com.google.auto.value.processor.AutoAnnotationProcessor;
import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.testing.compile.Compiler;
import javax.annotation.processing.Processor;

/** {@link Compiler} instances for testing Dagger. */
final class Compilers {
  private static final String GUAVA = "guava";

  static final ImmutableList<String> CLASS_PATH_WITHOUT_GUAVA_OPTION =
      ImmutableList.of(
          "-classpath",
          Splitter.on(PATH_SEPARATOR.value()).splitToList(JAVA_CLASS_PATH.value()).stream()
              .filter(jar -> !jar.contains(GUAVA))
              .collect(joining(PATH_SEPARATOR.value())));

  /**
   * Returns a compiler that runs the Dagger and {@code @AutoAnnotation} processors, along with
   * extras.
   */
  static Compiler daggerCompiler(Processor... extraProcessors) {
    ImmutableList.Builder<Processor> processors = ImmutableList.builder();
    processors.add(new ComponentProcessor(), new AutoAnnotationProcessor());
    processors.add(extraProcessors);
    return javac().withProcessors(processors.build());
  }

  static Compiler compilerWithOptions(CompilerMode... compilerModes) {
    FluentIterable<String> options = FluentIterable.of();
    for (CompilerMode compilerMode : compilerModes) {
      options = options.append(compilerMode.javacopts());
    }
    return daggerCompiler().withOptions(options);
  }
}

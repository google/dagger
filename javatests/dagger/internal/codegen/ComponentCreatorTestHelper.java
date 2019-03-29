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

import static dagger.internal.codegen.Compilers.daggerCompiler;
import static dagger.internal.codegen.ComponentCreatorKind.FACTORY;
import static dagger.internal.codegen.ErrorMessages.creatorMessagesFor;
import static java.util.stream.Collectors.joining;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.Arrays;
import java.util.stream.Stream;
import javax.tools.JavaFileObject;

/**
 * Base class for component creator codegen tests that are written in terms of builders and
 * transformed, either by automatic string processing or using a {@code JavaFileBuilder}, to test
 * factories as well.
 */
abstract class ComponentCreatorTestHelper {

  private final CompilerMode compilerMode;

  protected final ComponentCreatorKind creatorKind;
  protected final ErrorMessages.ComponentCreatorMessages messages;

  ComponentCreatorTestHelper(
      CompilerMode compilerMode, ComponentCreatorAnnotation componentCreatorAnnotation) {
    this.compilerMode = compilerMode;
    this.creatorKind = componentCreatorAnnotation.creatorKind();
    this.messages = creatorMessagesFor(componentCreatorAnnotation);
  }

  // For tests where code for both builders and factories can be largely equivalent, i.e. when there
  // is nothing to set, just preprocess the lines to change code written for a builder to code for a
  // factory.
  // For more complicated code, use a JavaFileBuilder to add different code depending on the creator
  // kind.

  /**
   * Processes the given lines, replacing builder-related names with factory-related names if the
   * creator kind is {@code FACTORY}.
   */
  String process(String... lines) {
    Stream<String> stream = Arrays.stream(lines);
    if (creatorKind.equals(FACTORY)) {
      stream =
          stream.map(
              line ->
                  line.replace("Builder", "Factory")
                      .replace("builder", "factory")
                      .replace("build", "create"));
    }
    return stream.collect(joining("\n"));
  }

  /**
   * Returns a Java file with the {@linkplain #process(String...)} processed} versions of the given
   * lines.
   */
  JavaFileObject preprocessedJavaFile(String fullyQualifiedName, String... lines) {
    return JavaFileObjects.forSourceString(fullyQualifiedName, process(lines));
  }

  /** Returns a file builder for the current creator kind. */
  JavaFileBuilder javaFileBuilder(String qualifiedName) {
    return new JavaFileBuilder(qualifiedName).withSettings(compilerMode, creatorKind);
  }

  /** Compiles the given files with the set compiler mode's javacopts. */
  Compilation compile(JavaFileObject... files) {
    return daggerCompiler().withOptions(compilerMode.javacopts()).compile(files);
  }
}

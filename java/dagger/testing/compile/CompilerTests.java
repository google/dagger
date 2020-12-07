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

import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.testing.compile.Compiler;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * A helper class for working with java compiler tests.
 */
public final class CompilerTests {
  private CompilerTests() {}

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
}

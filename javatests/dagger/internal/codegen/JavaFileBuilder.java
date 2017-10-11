/*
 * Copyright (C) 2017 The Dagger Authors.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;

/**
 * A fluent API to build a {@link JavaFileObject} appropriate for the current {@linkplain
 * CompilerMode compiler mode}.
 *
 * <p>After creating a builder, you can add lines to the file. Call {@link #addLines(String...)} to
 * add lines irrespective of the compiler mode. If you want to add different lines for each mode,
 * call {@link #addLinesIn(CompilerMode, String...)}.
 */
final class JavaFileBuilder {
  private final CompilerMode compilerMode;
  private final String qualifiedName;
  private final ImmutableList.Builder<String> sourceLines = ImmutableList.builder();

  /** Creates a builder for a file whose top level type has a given qualified name. */
  JavaFileBuilder(CompilerMode compilerMode, String qualifiedName) {
    checkArgument(!qualifiedName.isEmpty());
    this.compilerMode = checkNotNull(compilerMode);
    this.qualifiedName = qualifiedName;
  }

  /** Adds lines no matter what the {@link CompilerMode} is. */
  JavaFileBuilder addLines(String... lines) {
    sourceLines.add(lines);
    return this;
  }

  /** Adds lines if in the given mode. */
  JavaFileBuilder addLinesIn(CompilerMode mode, String... lines) {
    if (compilerMode.equals(mode)) {
      sourceLines.add(lines);
    }
    return this;
  }

  /** Builds the {@link JavaFileObject}. */
  JavaFileObject build() {
    return JavaFileObjects.forSourceLines(qualifiedName, sourceLines.build());
  }
}

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

import com.google.common.collect.ImmutableList;
import com.google.testing.compile.JavaFileObjects;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.tools.JavaFileObject;

/**
 * A fluent API to build a {@link JavaFileObject} appropriate for a current set of settings, such as
 * compiler mode.
 *
 * <p>After creating a builder, you can add lines to the file. Call {@link #addLines(String...)} to
 * add lines irrespective of the settings. If you want to add different lines for different possible
 * settings, call {@link #addLinesIf(Object, String...)} to add those lines only if the given
 * setting has been added via {@link #withSetting(Object)} or {@link #withSettings(Object...)}.
 */
final class JavaFileBuilder {
  private final String qualifiedName;
  private final Set<Object> settings = new HashSet<>();

  private final ImmutableList.Builder<String> sourceLines = ImmutableList.builder();

  /** Creates a builder for a file whose top level type has a given qualified name. */
  JavaFileBuilder(String qualifiedName) {
    checkArgument(!qualifiedName.isEmpty());
    this.qualifiedName = qualifiedName;
  }

  // TODO(cgdecker): Get rid of the special constructor/method for CompilerMode

  /** Creates a builder for a file whose top level type has a given qualified name. */
  JavaFileBuilder(CompilerMode compilerMode, String qualifiedName) {
    this(qualifiedName);
    settings.add(compilerMode);
  }

  /** Adds the given setting as one that code should be generated for. */
  JavaFileBuilder withSetting(Object setting) {
    this.settings.add(setting);
    return this;
  }

  /** Adds the given settings as one that code should be generated for. */
  JavaFileBuilder withSettings(Object s1, Object s2, Object... more) {
    settings.add(s1);
    settings.add(s2);
    Collections.addAll(settings, more);
    return this;
  }

  /** Adds lines no matter what the {@link CompilerMode} is. */
  JavaFileBuilder addLines(String... lines) {
    sourceLines.add(lines);
    return this;
  }

  /** Adds lines if in the given mode. */
  JavaFileBuilder addLinesIn(CompilerMode mode, String... lines) {
    return addLinesIf(mode, lines);
  }

  /** Adds lines if in the given setting is set. */
  JavaFileBuilder addLinesIf(Object setting, String... lines) {
    if (settings.contains(setting)) {
      sourceLines.add(lines);
    }
    return this;
  }

  /** Builds the {@link JavaFileObject}. */
  JavaFileObject build() {
    return JavaFileObjects.forSourceLines(qualifiedName, sourceLines.build());
  }
}

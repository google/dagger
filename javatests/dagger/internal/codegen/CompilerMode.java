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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;

/** The configuration options for compiler modes. */
// TODO(bcorso): Consider moving the java version into its own separate enum.
public enum CompilerMode {
  DEFAULT_MODE,
  FAST_INIT_MODE("-Adagger.fastInit=enabled"),
  ;

  /** Returns the compiler modes as a list of parameters for parameterized tests */
  public static final ImmutableList<Object[]> TEST_PARAMETERS =
      ImmutableList.copyOf(
          new Object[][] {
            {CompilerMode.DEFAULT_MODE},
            {CompilerMode.FAST_INIT_MODE},
          });

  private final ImmutableList<String> javacopts;

  private CompilerMode(String... javacopts) {
    this.javacopts = ImmutableList.copyOf(javacopts);
  }

  public boolean isKotlinCodegenEnabled() {
    return false;
  }

  /**
   * Returns the javacopts as a map of key-value pairs.
   *
   * @throws IllegalStateException if the javacopts are not of the form "-Akey=value".
   */
  public ImmutableMap<String, String> processorOptions() {
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    for (String javacopt : javacopts) {
      // Throw if there's a javacopt in this mode that is not an annotation processor option.
      checkState(javacopt.startsWith("-A"));
      List<String> splits = Splitter.on('=').splitToList(javacopt.substring(2));
      checkState(splits.size() == 2);
      builder.put(splits.get(0), splits.get(1));
    }
    return builder.buildOrThrow();
  }

  /** Returns the javacopts for this compiler mode. */
  public FluentIterable<String> javacopts() {
    return FluentIterable.from(javacopts);
  }
}

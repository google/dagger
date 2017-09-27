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

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;

/** The configuration options for compiler modes. */
enum CompilerMode {
  DEFAULT,
  EXPERIMENTAL_ANDROID("-Adagger.experimentalAndroidMode=enabled");

  /** Returns the compiler modes as a list of parameters for parameterized tests */
  static final ImmutableList<Object[]> TEST_PARAMETERS =
      ImmutableList.copyOf(
          new Object[][] {
            {CompilerMode.DEFAULT}, {CompilerMode.EXPERIMENTAL_ANDROID},
          });

  private final ImmutableList<String> javacopts;

  private CompilerMode(String... javacopts) {
    this.javacopts = ImmutableList.copyOf(javacopts);
  }

  /** Returns the javacopts for this compiler mode. */
  FluentIterable<String> javacopts() {
    return FluentIterable.from(javacopts);
  }
}

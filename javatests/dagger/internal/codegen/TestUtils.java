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

import com.google.common.base.Joiner;
import java.util.regex.Pattern;

/** Utility methods useful for codegen tests. */
final class TestUtils {

  private static final Joiner MESSAGE_JOINER = Joiner.on("\n  ");

  /**
   * Returns the lines joined by {@code "\n  "}. Useful for passing to {@link
   * com.google.testing.compile.CompilationSubject#hadErrorContaining(String)}, etc.
   */
  static String message(String... lines) {
    return MESSAGE_JOINER.join(lines);
  }

  /**
   * Returns a pattern that matches strings that end with the lines joined by {@code "\n  "}. Useful
   * for passing to {@link
   * com.google.testing.compile.CompilationSubject#hadErrorContainingMatch(Pattern)}, etc.
   */
  static Pattern endsWithMessage(String... lines) {
    return Pattern.compile(Pattern.quote(message(lines)) + "$");
  }
}

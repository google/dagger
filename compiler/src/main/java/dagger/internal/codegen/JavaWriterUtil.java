/*
 * Copyright (C) 2014 Google, Inc.
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

import com.google.common.collect.ImmutableList;
import com.squareup.javawriter.JavaWriter;

import java.util.Map;
import java.util.Map.Entry;

/**
 * Utilities for working with {@link JavaWriter} instances.
 *
 * @author Gregory Kick
 * @since 2.0
 */
// TODO(gak): push changes upstream to obviate the need for such utilities
class JavaWriterUtil {
  /**
   * Given a mapping from variable name to type, returns a list of tokens suitable for methods such
   * as {@link JavaWriter#beginMethod(String, String, java.util.Set, String...)}.
   */
  static ImmutableList<String> flattenVariableMap(Map<String, String> variableMap) {
    ImmutableList.Builder<String> tokenList = ImmutableList.builder();
    for (Entry<String, String> variableEntry : variableMap.entrySet()) {
      tokenList.add(variableEntry.getValue(), variableEntry.getKey());
    }
    return tokenList.build();
  }

  private JavaWriterUtil() {}
}

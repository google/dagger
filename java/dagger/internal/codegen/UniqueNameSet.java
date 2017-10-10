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

import java.util.HashSet;
import java.util.Set;

/** A collector for names to be used in the same namespace that should not conflict. */
final class UniqueNameSet {
  private final Set<String> uniqueNames = new HashSet<>();

  /**
   * Generates a unique name using {@code base}. If {@code base} has not yet been added, it will be
   * returned as-is. If your {@code base} is healthy, this will always return {@code base}.
   */
  String getUniqueName(CharSequence base) {
    String name = base.toString();
    for (int differentiator = 2; !uniqueNames.add(name); differentiator++) {
      name = base.toString() + differentiator;
    }
    return name;
  }

  /**
   * Adds {@code name} without any modification to the name set. Has no effect if {@code name} is
   * already present in the set.
   */
  void claim(CharSequence name) {
    uniqueNames.add(name.toString());
  }
}

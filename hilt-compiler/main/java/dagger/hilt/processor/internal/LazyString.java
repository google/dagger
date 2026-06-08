/*
 * Copyright (C) 2026 The Dagger Authors.
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

package dagger.hilt.processor.internal;

import java.util.function.Supplier;

/** A wrapper that computes a string lazily on the first call to {@link #toString()}. */
public final class LazyString {
  private final Supplier<String> supplier;
  private String value;

  private LazyString(Supplier<String> supplier) {
    this.supplier = supplier;
  }

  public static LazyString of(Supplier<String> supplier) {
    return new LazyString(supplier);
  }

  @Override
  public String toString() {
    if (value == null) {
      value = supplier.get();
    }
    return value;
  }
}

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
package dagger2.internal.codegen;

import com.google.common.base.Function;

/**
 * A formatter which transforms an instance of a particular type into a string
 * representation.
 *
 * @param <T> the type of the object to be transformed.
 * @author Christian Gruber
 * @since 2.0
 */
abstract class Formatter<T> implements Function<T, String> {

  /**
   * Performs the transformation of an object into a string representation.
   */
  public abstract String format(T object);

  /**
   * Performs the transformation of an object into a string representation in
   * conformity with the {@link Function}{@code <T, String>} contract, delegating
   * to {@link #format(Object)}.
   *
   * @deprecated Call {@link #format(T)} instead.  This method exists to make
   * formatters easy to use when functions are required, but shouldn't be called directly.
   */
  @SuppressWarnings("javadoc")
  @Deprecated
  @Override final public String apply(T object) {
    return format(object);
  }
}

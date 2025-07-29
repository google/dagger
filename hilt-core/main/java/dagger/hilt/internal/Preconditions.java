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

package dagger.hilt.internal;

/**
 * A partial copy of Guava's {@code com.google.common.base.Preconditions} meant to be used by
 * generated code. TODO(danysantiago): Consolidate with dagger.internal.Preconditions
 */
public final class Preconditions {

  /**
   * Ensures that an object reference passed as a parameter to the calling method is not null.
   *
   * @param reference an object reference
   * @return the non-null reference that was validated
   * @throws NullPointerException if {@code reference} is null
   */
  public static <T> T checkNotNull(T reference) {
    if (reference == null) {
      throw new NullPointerException();
    }
    return reference;
  }

  /**
   * Ensures that an object reference passed as a parameter to the calling method is not null.
   *
   * @param reference an object reference
   * @param errorMessage the exception message to use if the check fails
   * @return the non-null reference that was validated
   * @throws NullPointerException if {@code reference} is null
   */
  public static <T> T checkNotNull(T reference, String errorMessage) {
    if (reference == null) {
      throw new NullPointerException(errorMessage);
    }
    return reference;
  }

  /**
   * Ensures the truth of an expression involving one or more parameters to the calling method.
   *
   * @param expression a boolean expression
   * @param errorMessageTemplate a template for the exception message should the check fail. The
   *     message is formed by replacing each occurrence of {@code "%s"} with the corresponding
   *     argument value from {@code args}.
   * @param args the arguments to be substituted into the message template.
   * @throws IllegalArgumentException if {@code expression} is false
   */
  public static void checkArgument(
      boolean expression, String errorMessageTemplate, Object... args) {
    if (!expression) {
      throw new IllegalArgumentException(String.format(errorMessageTemplate, args));
    }
  }

  /**
   * Ensures the truth of an expression involving one or more parameters to the calling method.
   *
   * @param expression a boolean expression
   * @param errorMessageTemplate a template for the exception message should the check fail. The
   *     message is formed by replacing each occurrence of {@code "%s"} with the corresponding
   *     argument value from {@code args}.
   * @param args the arguments to be substituted into the message template.
   * @throws IllegalStateException if {@code expression} is false
   */
  public static void checkState(boolean expression, String errorMessageTemplate, Object... args) {
    if (!expression) {
      throw new IllegalStateException(String.format(errorMessageTemplate, args));
    }
  }

  private Preconditions() {}
}

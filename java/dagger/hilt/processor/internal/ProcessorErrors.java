/*
 * Copyright (C) 2019 The Dagger Authors.
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


import androidx.room.compiler.processing.XElement;
import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import java.util.Collection;
import javax.annotation.Nullable;

/** Static helper methods for throwing errors during code generation. */
public final class ProcessorErrors {
  /**
   * Ensures the truth of an expression involving the state of the calling instance, but not
   * involving any parameters to the calling method.
   *
   * @param expression a boolean expression
   * @param errorMessage the exception message to use if the check fails; will be converted to a
   *     string using {@link String#valueOf(Object)}
   * @throws BadInputException if {@code expression} is false
   */
  public static void checkState(boolean expression, @Nullable Object errorMessage) {
    if (!expression) {
      throw new BadInputException(String.valueOf(errorMessage));
    }
  }

  /**
   * Ensures the truth of an expression involving the state of the calling instance, but not
   * involving any parameters to the calling method.
   *
   * @param expression a boolean expression
   * @param errorMessageTemplate a template for the exception message should the check fail. The
   *     message is formed by replacing each {@code %s} placeholder in the template with an
   *     argument. These are matched by position - the first {@code %s} gets {@code
   *     errorMessageArgs[0]}, etc. Unmatched arguments will be appended to the formatted message in
   *     square braces. Unmatched placeholders will be left as-is.
   * @param errorMessageArgs the arguments to be substituted into the message template. Arguments
   *     are converted to strings using {@link String#valueOf(Object)}.
   * @throws BadInputException if {@code expression} is false
   * @throws NullPointerException if the check fails and either {@code errorMessageTemplate} or
   *     {@code errorMessageArgs} is null (don't let this happen)
   */
  @FormatMethod
  public static void checkState(
      boolean expression,
      @Nullable @FormatString String errorMessageTemplate,
      @Nullable Object... errorMessageArgs) {
    if (!expression) {
      throw new BadInputException(String.format(errorMessageTemplate, errorMessageArgs));
    }
  }

  /**
   * Ensures the truth of an expression involving the state of the calling instance, but not
   * involving any parameters to the calling method.
   *
   * @param expression a boolean expression
   * @param badElement the element that was at fault
   * @param errorMessage the exception message to use if the check fails; will be converted to a
   *     string using {@link String#valueOf(Object)}
   * @throws BadInputException if {@code expression} is false
   */
  public static void checkState(
      boolean expression, XElement badElement, @Nullable Object errorMessage) {
    Preconditions.checkNotNull(badElement);
    if (!expression) {
      throw new BadInputException(String.valueOf(errorMessage), badElement);
    }
  }

  /**
   * Ensures the truth of an expression involving the state of the calling instance, but not
   * involving any parameters to the calling method.
   *
   * <p>e.g. checkState(foo.isABar(), "Failed because of %s is not a bar", foo);
   *
   * @param expression a boolean expression
   * @param badElement the element that was at fault
   * @param errorMessageTemplate a template for the exception message should the check fail. The
   *     message is formed by replacing each {@code %s} placeholder in the template with an
   *     argument. These are matched by position - the first {@code %s} gets {@code
   *     errorMessageArgs[0]}, etc. Unmatched arguments will be appended to the formatted message in
   *     square braces. Unmatched placeholders will be left as-is.
   * @param errorMessageArgs the arguments to be substituted into the message template. Arguments
   *     are converted to strings using {@link String#valueOf(Object)}.
   * @throws BadInputException if {@code expression} is false
   * @throws NullPointerException if the check fails and either {@code errorMessageTemplate} or
   *     {@code errorMessageArgs} is null (don't let this happen)
   */
  @FormatMethod
  public static void checkState(
      boolean expression,
      XElement badElement,
      @Nullable @FormatString String errorMessageTemplate,
      @Nullable Object... errorMessageArgs) {
    Preconditions.checkNotNull(badElement);
    if (!expression) {
      throw new BadInputException(
          String.format(errorMessageTemplate, errorMessageArgs), badElement);
    }
  }

  /**
   * Ensures the truth of an expression involving the state of the calling instance, but not
   * involving any parameters to the calling method.
   *
   * @param expression a boolean expression
   * @param badElements the elements that were at fault
   * @param errorMessageTemplate a template for the exception message should the check fail. The
   *     message is formed by replacing each {@code %s} placeholder in the template with an
   *     argument. These are matched by position - the first {@code %s} gets {@code
   *     errorMessageArgs[0]}, etc. Unmatched arguments will be appended to the formatted message in
   *     square braces. Unmatched placeholders will be left as-is.
   * @param errorMessageArgs the arguments to be substituted into the message template. Arguments
   *     are converted to strings using {@link String#valueOf(Object)}.
   * @throws BadInputException if {@code expression} is false
   * @throws NullPointerException if the check fails and either {@code errorMessageTemplate} or
   *     {@code errorMessageArgs} is null (don't let this happen)
   */
  // TODO(bcorso): Rename this checkState once the javac API is removed (overloading doesn't work
  // here since they have the same erasured signature).
  @FormatMethod
  public static void checkStateX(
      boolean expression,
      Collection<? extends XElement> badElements,
      @Nullable @FormatString String errorMessageTemplate,
      @Nullable Object... errorMessageArgs) {
    Preconditions.checkNotNull(badElements);
    if (!expression) {
      Preconditions.checkState(!badElements.isEmpty());
      throw new BadInputException(
          String.format(errorMessageTemplate, errorMessageArgs), badElements);
    }
  }

  private ProcessorErrors() {}
}

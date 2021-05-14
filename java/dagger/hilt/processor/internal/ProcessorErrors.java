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

import com.google.auto.common.MoreTypes;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import java.util.Collection;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

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
  public static void checkState(
      boolean expression,
      @Nullable Object errorMessage) {
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
      boolean expression,
      Element badElement,
      @Nullable Object errorMessage) {
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
      Element badElement,
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
   * @param badElements the element that were at fault
   * @param errorMessage the exception message to use if the check fails; will be converted to a
   *     string using {@link String#valueOf(Object)}
   * @throws BadInputException if {@code expression} is false
   */
  public static void checkState(
      boolean expression,
      Collection<? extends Element> badElements,
      @Nullable Object errorMessage) {
    Preconditions.checkNotNull(badElements);
    if (!expression) {
      Preconditions.checkState(!badElements.isEmpty());
      throw new BadInputException(String.valueOf(errorMessage), badElements);
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
  @FormatMethod
  public static void checkState(
      boolean expression,
      Collection<? extends Element> badElements,
      @Nullable @FormatString String errorMessageTemplate,
      @Nullable Object... errorMessageArgs) {
    Preconditions.checkNotNull(badElements);
    if (!expression) {
      Preconditions.checkState(!badElements.isEmpty());
      throw new BadInputException(
          String.format(errorMessageTemplate, errorMessageArgs), badElements);
    }
  }

  /**
   * Ensures that the given element is not an error kind and does not inherit from an error kind.
   *
   * @param element the element to check
   * @throws ErrorTypeException if {@code element} inherits from an error kind.
   */
  public static void checkNotErrorKind(TypeElement element) {
    TypeMirror currType = element.asType();
    ImmutableList.Builder<String> typeHierarchy = ImmutableList.builder();
    while (currType.getKind() != TypeKind.NONE) {
      typeHierarchy.add(currType.toString());
      if (currType.getKind() == TypeKind.ERROR) {
        throw new ErrorTypeException(
            String.format(
                "%s, type hierarchy contains error kind, %s."
                + "\n\tThe partially resolved hierarchy is:\n\t\t%s",
                element,
                currType,
                typeHierarchy.build().stream().collect(Collectors.joining(" -> "))),
            element);
      }
      currType = MoreTypes.asTypeElement(currType).getSuperclass();
    }
  }

  private ProcessorErrors() {}
}

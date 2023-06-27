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

import static androidx.room.compiler.processing.XElementKt.isTypeElement;
import static dagger.internal.codegen.xprocessing.XElements.asTypeElement;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XMessager;
import androidx.room.compiler.processing.XProcessingEnv;
import com.google.auto.value.AutoValue;
import com.google.common.base.Throwables;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.tools.Diagnostic.Kind;

/** Utility class to handle keeping track of errors during processing. */
final class ProcessorErrorHandler {

  private static final String FAILURE_PREFIX = "[Hilt] ";

  // Special characters to make the tag red and bold to draw attention since
  // this error can get drowned out by other errors resulting from missing
  // symbols when we can't generate code.
  private static final String FAILURE_SUFFIX =
      "\n\033[1;31m[Hilt] Processing did not complete. See error above for details.\033[0m";

  private final XProcessingEnv processingEnv;
  private final XMessager messager;
  private final List<HiltError> hiltErrors = new ArrayList<>();

  ProcessorErrorHandler(XProcessingEnv processingEnv) {
    this.processingEnv = processingEnv;
    this.messager = processingEnv.getMessager();
  }

  /**
   * Records an error message for some exception to the messager. This can be used to handle
   * exceptions gracefully that would otherwise be propagated out of the {@code process} method. The
   * message is stored in order to allow the build to continue as far as it can. The build will be
   * failed with a {@link Kind#ERROR} in {@link #checkErrors} if an error was recorded with this
   * method.
   */
  void recordError(Throwable t) {
    // Store messages to allow the build to continue as far as it can. The build will
    // be failed in checkErrors when processing is over.

    if (t instanceof BadInputException) {
      BadInputException badInput = (BadInputException) t;
      if (badInput.getBadElements().isEmpty()) {
        hiltErrors.add(HiltError.of(badInput.getMessage()));
      }
      for (XElement element : badInput.getBadElements()) {
        hiltErrors.add(HiltError.of(badInput.getMessage(), element));
      }
    } else if (t instanceof ErrorTypeException) {
      ErrorTypeException badInput = (ErrorTypeException) t;
      hiltErrors.add(HiltError.of(badInput.getMessage(), badInput.getBadElement()));
    } else if (t.getMessage() != null) {
      hiltErrors.add(HiltError.of(t.getMessage() + ": " + Throwables.getStackTraceAsString(t)));
    } else {
      hiltErrors.add(HiltError.of(t.getClass() + ": " + Throwables.getStackTraceAsString(t)));
    }
  }

  /** Checks for any recorded errors. This should be called at the end of process every round. */
  void checkErrors() {
    if (!hiltErrors.isEmpty()) {
      hiltErrors.forEach(
          hiltError -> {
            if (hiltError.element().isPresent()) {
              XElement element = hiltError.element().get();
              if (isTypeElement(element)) {
                // If the error type is a TypeElement, get a new one just in case it was thrown in a
                // previous round we can report the correct instance. Otherwise, this leads to
                // issues in AndroidStudio when linking an error to the proper element.
                // TODO(bcorso): Consider only allowing TypeElement errors when delaying errors,
                // or maybe even removing delayed errors altogether.
                element =
                    processingEnv.requireTypeElement(asTypeElement(element).getQualifiedName());
              }
              messager.printMessage(Kind.ERROR, hiltError.message(), element);
            } else {
              messager.printMessage(Kind.ERROR, hiltError.message());
            }
          });
      hiltErrors.clear();
    }
  }

  public boolean isEmpty() {
    return hiltErrors.isEmpty();
  }

  @AutoValue
  abstract static class HiltError {
    static HiltError of(String message) {
      return of(message, Optional.empty());
    }

    static HiltError of(String message, XElement element) {
      return of(message, Optional.of(element));
    }

    private static HiltError of(String message, Optional<XElement> element) {
      return new AutoValue_ProcessorErrorHandler_HiltError(
          FAILURE_PREFIX + message + FAILURE_SUFFIX, element);
    }

    abstract String message();

    abstract Optional<XElement> element();
  }
}

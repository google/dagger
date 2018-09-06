/*
 * Copyright (C) 2014 The Dagger Authors.
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

import static com.google.common.base.Preconditions.checkNotNull;
import static javax.tools.Diagnostic.Kind.ERROR;

import com.squareup.javapoet.ClassName;
import java.util.Optional;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;

/**
 * An exception thrown to indicate that a source file could not be generated.
 *
 * <p>This exception <b>should not</b> be used to report detectable, logical errors as it may mask
 * other errors that might have been caught upon further processing. Use a {@link ValidationReport}
 * for that.
 */
final class SourceFileGenerationException extends Exception {
  private final Element associatedElement;

  SourceFileGenerationException(
      Optional<ClassName> generatedClassName, Throwable cause, Element associatedElement) {
    super(createMessage(generatedClassName, cause.getMessage()), cause);
    this.associatedElement = checkNotNull(associatedElement);
  }

  private static String createMessage(Optional<ClassName> generatedClassName, String message) {
    return String.format("Could not generate %s: %s.",
        generatedClassName.isPresent()
            ? generatedClassName.get()
            : "unknown file",
        message);
  }

  void printMessageTo(Messager messager) {
    messager.printMessage(ERROR, getMessage(), associatedElement);
  }
}

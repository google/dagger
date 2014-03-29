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

import static com.google.common.base.Preconditions.checkNotNull;
import static javax.tools.Diagnostic.Kind.ERROR;

import com.google.common.base.Optional;

import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;

/**
 * An exception thrown to indicate that a source file could not be generated.
 *
 * <p>This exception <b>should not</b> be used to report detectable, logical errors as it may mask
 * other errors that might have been caught upon further processing.  Use a {@link ValidationReport}
 * for that.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class SourceFileGenerationException extends Exception implements PrintableErrorMessage {
  private final ClassName generatedClassName;
  private final Optional<? extends Element> associatedElement;

  SourceFileGenerationException(ClassName generatedClassName, Throwable cause,
      Optional<? extends Element> associatedElement) {
    super(createMessage(generatedClassName, cause.getMessage()), cause);
    this.generatedClassName = checkNotNull(generatedClassName);
    this.associatedElement = checkNotNull(associatedElement);
  }

  SourceFileGenerationException(ClassName generatedClassName, Throwable cause) {
    this(generatedClassName, cause, Optional.<Element>absent());
  }

  SourceFileGenerationException(ClassName generatedClassName, Throwable cause,
      Element associatedElement) {
    this(generatedClassName, cause, Optional.of(associatedElement));
  }

  public ClassName generatedClassName() {
    return generatedClassName;
  }

  public Optional<? extends Element> associatedElement() {
    return associatedElement;
  }

  private static String createMessage(ClassName generatedClassName, String message) {
    return String.format("Could not generate %s: %s.", generatedClassName, message);
  }

  @Override
  public void printMessageTo(Messager messager) {
    if (associatedElement.isPresent()) {
      messager.printMessage(ERROR, getMessage(), associatedElement.get());
    } else {
      messager.printMessage(ERROR, getMessage());
    }
  }
}

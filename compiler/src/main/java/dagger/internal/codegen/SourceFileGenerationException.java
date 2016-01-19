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

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import dagger.internal.codegen.writer.ClassName;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;

import static com.google.common.base.Preconditions.checkNotNull;
import static javax.tools.Diagnostic.Kind.ERROR;

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
final class SourceFileGenerationException extends Exception {
  // TODO(ronshapiro): remove these unused values
  private final ImmutableSet<ClassName> generatedClassNames;
  private final Optional<? extends Element> associatedElement;

  SourceFileGenerationException(Iterable<ClassName> generatedClassNames, Throwable cause,
      Optional<? extends Element> associatedElement) {
    super(createMessage(generatedClassNames, cause.getMessage()), cause);
    this.generatedClassNames = ImmutableSet.copyOf(generatedClassNames);
    this.associatedElement = checkNotNull(associatedElement);
  }

  SourceFileGenerationException(Iterable<ClassName> generatedClassNames, Throwable cause) {
    this(generatedClassNames, cause, Optional.<Element>absent());
  }

  SourceFileGenerationException(Iterable<ClassName> generatedClassNames, Throwable cause,
      Element associatedElement) {
    this(generatedClassNames, cause, Optional.of(associatedElement));
  }

  SourceFileGenerationException(
      Optional<com.squareup.javapoet.ClassName> generatedClassName,
      Throwable cause,
      Optional<? extends Element> associatedElement) {
    super(createMessage(generatedClassName, cause.getMessage()), cause);
    this.generatedClassNames = ImmutableSet.of();
    this.associatedElement = checkNotNull(associatedElement);
  }

  public ImmutableSet<ClassName> generatedClassNames() {
    return generatedClassNames;
  }

  public Optional<? extends Element> associatedElement() {
    return associatedElement;
  }

  private static String createMessage(Iterable<ClassName> generatedClassNames, String message) {
    return String.format("Could not generate %s: %s.",
        Iterables.isEmpty(generatedClassNames)
            ? "unknown files"
            : Iterables.toString(generatedClassNames),
        message);
  }

  private static String createMessage(
      Optional<com.squareup.javapoet.ClassName> generatedClassName, String message) {
    return String.format("Could not generate %s: %s.",
        generatedClassName.isPresent()
            ? generatedClassName.get()
            : "unknown file",
        message);
  }

  void printMessageTo(Messager messager) {
    if (associatedElement.isPresent()) {
      messager.printMessage(ERROR, getMessage(), associatedElement.get());
    } else {
      messager.printMessage(ERROR, getMessage());
    }
  }
}

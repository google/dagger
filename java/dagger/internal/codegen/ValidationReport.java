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

import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.NOTE;
import static javax.tools.Diagnostic.Kind.WARNING;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import java.util.Optional;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.util.SimpleElementVisitor6;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;

/** A collection of issues to report for source code. */
@AutoValue
abstract class ValidationReport<T extends Element> {

  /**
   * The subject of the report. Should be an element within a compilation unit being processed by
   * this compilation task.
   */
  abstract T subject();

  /** The items to report for the {@linkplain #subject() subject}. */
  abstract ImmutableSet<Item> items();

  /** Other reports associated with this one. */
  abstract ImmutableSet<ValidationReport<?>> subreports();

  /** Returns {@code true} if there are no errors in this report or any subreports. */
  boolean isClean() {
    for (Item item : items()) {
      switch (item.kind()) {
        case ERROR:
          return false;
        default:
          break;
      }
    }
    for (ValidationReport<?> subreport : subreports()) {
      if (!subreport.isClean()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Prints all {@linkplain #items() messages} to {@code messager} (and recurs for subreports). If a
   * message's {@linkplain Item#element() element} is contained within the report's {@linkplain
   * #subject() subject}, associates the message with the message's element. Otherwise, since
   * {@link Diagnostic} reporting is expected to be associated with elements that are currently
   * being compiled, associates the message with the subject itself and prepends a reference to the
   * item's element.
   */
  void printMessagesTo(Messager messager) {
    for (Item item : items()) {
      if (isEnclosedIn(subject(), item.element())) {
        if (item.annotation().isPresent()) {
          if (item.annotationValue().isPresent()) {
            messager.printMessage(
                item.kind(),
                item.message(),
                item.element(),
                item.annotation().get(),
                item.annotationValue().get());
          } else {
            messager.printMessage(
                item.kind(), item.message(), item.element(), item.annotation().get());
          }
        } else {
          messager.printMessage(item.kind(), item.message(), item.element());
        }
      } else {
        String message = String.format("[%s] %s", elementString(item.element()), item.message());
        messager.printMessage(item.kind(), message, subject());
      }
    }
    for (ValidationReport<?> subreport : subreports()) {
      subreport.printMessagesTo(messager);
    }
  }

  private static String elementString(Element element) {
    return element.accept(
        new SimpleElementVisitor6<String, Void>() {
          @Override
          protected String defaultAction(Element e, Void p) {
            return e.toString();
          }

          @Override
          public String visitExecutable(ExecutableElement e, Void p) {
            return e.getEnclosingElement().accept(this, null) + '.' + e.toString();
          }
        },
        null);
  }

  private static boolean isEnclosedIn(Element parent, Element child) {
    Element current = child;
    while (current != null) {
      if (current.equals(parent)) {
        return true;
      }
      current = current.getEnclosingElement();
    }
    return false;
  }

  @AutoValue
  static abstract class Item {
    abstract String message();
    abstract Kind kind();
    abstract Element element();
    abstract Optional<AnnotationMirror> annotation();
    abstract Optional<AnnotationValue> annotationValue();
  }

  static <T extends Element> Builder<T> about(T subject) {
    return new Builder<>(subject);
  }

  @CanIgnoreReturnValue
  static final class Builder<T extends Element> {
    private final T subject;
    private final ImmutableSet.Builder<Item> items = ImmutableSet.builder();
    private final ImmutableSet.Builder<ValidationReport<?>> subreports = ImmutableSet.builder();

    private Builder(T subject) {
      this.subject = subject;
    }

    @CheckReturnValue
    T getSubject() {
      return subject;
    }

    Builder<T> addItems(Iterable<Item> newItems) {
      items.addAll(newItems);
      return this;
    }

    Builder<T> addError(String message) {
      return addError(message, subject);
    }

    Builder<T> addError(String message, Element element) {
      return addItem(message, ERROR, element);
    }

    Builder<T> addError(String message, Element element, AnnotationMirror annotation) {
      return addItem(message, ERROR, element, annotation);
    }

    Builder<T> addError(
        String message,
        Element element,
        AnnotationMirror annotation,
        AnnotationValue annotationValue) {
      return addItem(message, ERROR, element, annotation, annotationValue);
    }

    Builder<T> addWarning(String message) {
      return addWarning(message, subject);
    }

    Builder<T> addWarning(String message, Element element) {
      return addItem(message, WARNING, element);
    }

    Builder<T> addWarning(String message, Element element, AnnotationMirror annotation) {
      return addItem(message, WARNING, element, annotation);
    }

    Builder<T> addWarning(
        String message,
        Element element,
        AnnotationMirror annotation,
        AnnotationValue annotationValue) {
      return addItem(message, WARNING, element, annotation, annotationValue);
    }

    Builder<T> addNote(String message) {
      return addNote(message, subject);
    }

    Builder<T> addNote(String message, Element element) {
      return addItem(message, NOTE, element);
    }

    Builder<T> addNote(String message, Element element, AnnotationMirror annotation) {
      return addItem(message, NOTE, element, annotation);
    }

    Builder<T> addNote(
        String message,
        Element element,
        AnnotationMirror annotation,
        AnnotationValue annotationValue) {
      return addItem(message, NOTE, element, annotation, annotationValue);
    }

    Builder<T> addItem(String message, Kind kind, Element element) {
      return addItem(message, kind, element, Optional.empty(), Optional.empty());
    }

    Builder<T> addItem(String message, Kind kind, Element element, AnnotationMirror annotation) {
      return addItem(message, kind, element, Optional.of(annotation), Optional.empty());
    }

    Builder<T> addItem(
        String message,
        Kind kind,
        Element element,
        AnnotationMirror annotation,
        AnnotationValue annotationValue) {
      return addItem(message, kind, element, Optional.of(annotation), Optional.of(annotationValue));
    }

    private Builder<T> addItem(
        String message,
        Kind kind,
        Element element,
        Optional<AnnotationMirror> annotation,
        Optional<AnnotationValue> annotationValue) {
      items.add(
          new AutoValue_ValidationReport_Item(message, kind, element, annotation, annotationValue));
      return this;
    }

    Builder<T> addSubreport(ValidationReport<?> subreport) {
      subreports.add(subreport);
      return this;
    }

    @CheckReturnValue
    ValidationReport<T> build() {
      return new AutoValue_ValidationReport<>(subject, items.build(), subreports.build());
    }
  }
}

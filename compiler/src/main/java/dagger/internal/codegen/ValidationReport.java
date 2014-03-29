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

import static javax.tools.Diagnostic.Kind.ERROR;

import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;

import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;

/**
 * A collection of items describing contractual issues with the code as presented to an annotation
 * processor.  A "clean" report (i.e. with no issues) is a report with no {@linkplain Item items}.
 * Callers will typically print the results of the report to a {@link Messager} instance using
 * {@link #printMessagesTo}.
 *
 * @author Gregory Kick
 * @since 2.0
 */
@AutoValue
abstract class ValidationReport<T> {
  abstract T subject();
  abstract ImmutableSet<Item> items();

  boolean isClean() {
    return items().isEmpty();
  }

  void printMessagesTo(Messager messager) {
    for (Item item : items()) {
      item.printMessageTo(messager);
    }
  }

  @AutoValue
  static abstract class Item implements PrintableErrorMessage {
    abstract String message();
    abstract Element element();
    abstract Optional<AnnotationMirror> annotation();

    @Override
    public void printMessageTo(Messager messager) {
      if (annotation().isPresent()) {
        messager.printMessage(ERROR, message(), element(), annotation().get());
      } else {
        messager.printMessage(ERROR, message(), element());
      }
    }
  }

  static final class Builder<T> {
    static <T> Builder<T> about(T subject) {
      return new Builder<T>(subject);
    }

    private final T subject;
    private final ImmutableSet.Builder<Item> items = ImmutableSet.builder();

    private Builder(T subject) {
      this.subject = subject;
    }

    Builder<T> addItem(String message, Element element) {
      items.add(new AutoValue_ValidationReport_Item(message, element,
          Optional.<AnnotationMirror>absent()));
      return this;
    }

    Builder<T> addItem(String message, Element element, AnnotationMirror annotation) {
      items.add(new AutoValue_ValidationReport_Item(message, element, Optional.of(annotation)));
      return this;
    }

    ValidationReport<T> build() {
      return new AutoValue_ValidationReport<T>(subject, items.build());
    }
  }
}

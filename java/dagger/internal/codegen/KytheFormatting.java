/*
 * Copyright (C) 2017 The Dagger Authors.
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

import static com.google.auto.common.AnnotationMirrors.getAnnotationValuesWithDefaults;

import com.google.auto.common.MoreTypes;
import com.squareup.javapoet.CodeBlock;
import dagger.model.Key;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.AnnotationValueVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;

final class KytheFormatting {
  /**
   * Produces a {@link String} representation of a {@link Key} in a deterministic fashion. The
   * result is a combination of the key's {@link Key#type() type} and an optional {@link
   * Key#qualifier() qualifier}.
   */
  static String formatKey(Key key) {
    StringBuilder builder = new StringBuilder();
    if (key.qualifier().isPresent()) {
      formatAnnotation(key.qualifier().get(), builder);
      builder.append(' ');
    }
    return builder.append(key.type().toString()).toString();
  }

  /**
   * Produces a string version of {@code annotation} with its attributes and values, including any
   * defaults. Attributes are presented in the order in which they are defined in the {@link
   * AnnotationMirror#getAnnotationType() annotation type}.
   */
  static String formatAnnotation(AnnotationMirror annotation) {
    StringBuilder builder = new StringBuilder();
    formatAnnotation(annotation, builder);
    return builder.toString();
  }

  private static void formatAnnotation(AnnotationMirror annotation, StringBuilder stringBuilder) {
    stringBuilder.append('@').append(MoreTypes.asTypeElement(annotation.getAnnotationType()));
    Map<ExecutableElement, AnnotationValue> annotationValues =
        getAnnotationValuesWithDefaults(annotation);
    if (!annotationValues.isEmpty()) {
      stringBuilder.append('(');
      appendList(
          stringBuilder,
          annotationValues.entrySet(),
          entry -> {
            stringBuilder.append(entry.getKey().getSimpleName()).append('=');
            entry.getValue().accept(ANNOTATION_VALUE_FORMATTER, stringBuilder);
          });
      stringBuilder.append(')');
    }
  }

  private static final AnnotationValueVisitor<Void, StringBuilder> ANNOTATION_VALUE_FORMATTER =
      new SimpleAnnotationValueVisitor8<Void, StringBuilder>() {
        @Override
        public Void visitAnnotation(AnnotationMirror innerAnnotation, StringBuilder stringBuilder) {
          formatAnnotation(innerAnnotation, stringBuilder);
          return null;
        }

        @Override
        public Void visitArray(List<? extends AnnotationValue> list, StringBuilder stringBuilder) {
          stringBuilder.append('{');
          appendList(stringBuilder, list, value -> value.accept(this, stringBuilder));
          stringBuilder.append('}');
          return null;
        }

        @Override
        public Void visitEnumConstant(VariableElement enumConstant, StringBuilder stringBuilder) {
          stringBuilder.append(enumConstant.getSimpleName());
          return null;
        }

        @Override
        public Void visitType(TypeMirror typeMirror, StringBuilder stringBuilder) {
          stringBuilder.append(typeMirror).append(".class");
          return null;
        }

        @Override
        protected Void defaultAction(Object value, StringBuilder stringBuilder) {
          stringBuilder.append(value);
          return null;
        }

        @Override
        public Void visitString(String value, StringBuilder stringBuilder) {
          stringBuilder.append(CodeBlock.of("$S", value));
          return null;
        }
      };

  private static <T> void appendList(
      StringBuilder stringBuilder, Iterable<T> iterable, Consumer<T> consumer) {
    Iterator<T> iterator = iterable.iterator();
    while (iterator.hasNext()) {
      consumer.accept(iterator.next());
      if (iterator.hasNext()) {
        stringBuilder.append(", ");
      }
    }
  }
}

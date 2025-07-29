/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.internal.codegen.xprocessing;

import static androidx.room.compiler.processing.compat.XConverters.getProcessingEnv;
import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static java.util.stream.Collectors.joining;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.processing.JavaPoetExtKt;
import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.common.AnnotationMirrors;
import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import java.util.Arrays;

// TODO(bcorso): Consider moving these methods into XProcessing library.
/** A utility class for {@link XAnnotation} helper methods. */
public final class XAnnotations {

  /** Returns the {@link AnnotationSpec} for the given annotation */
  public static AnnotationSpec getAnnotationSpec(XAnnotation annotation) {
    return JavaPoetExtKt.toAnnotationSpec(annotation, false);
  }

  /** Returns the string representation of the given annotation. */
  public static String toString(XAnnotation annotation) {
    // TODO(b/241293838): Make javac and ksp agree on the string representation.
    return getProcessingEnv(annotation).getBackend() == XProcessingEnv.Backend.JAVAC
        ? AnnotationMirrors.toString(toJavac(annotation))
        : XAnnotations.toStableString(annotation);
  }

  /** Returns the class name of the given annotation */
  public static ClassName getClassName(XAnnotation annotation) {
    return annotation.getType().getTypeElement().getClassName();
  }

  // TODO(b/397745985): Remove this method once the bug is fixed.
  /** Returns the class name of the given annotation */
  public static XClassName asClassName(XAnnotation annotation) {
    return annotation.getType().getTypeElement().asClassName();
  }

  private static final Equivalence<XAnnotation> XANNOTATION_EQUIVALENCE =
      new Equivalence<XAnnotation>() {
        @Override
        protected boolean doEquivalent(XAnnotation left, XAnnotation right) {
          return XTypes.equivalence().equivalent(left.getType(), right.getType())
              && XAnnotationValues.equivalence()
                  .pairwise()
                  .equivalent(left.getAnnotationValues(), right.getAnnotationValues());
        }

        @Override
        protected int doHash(XAnnotation annotation) {
          return Arrays.hashCode(
              new int[] {
                XTypes.equivalence().hash(annotation.getType()),
                XAnnotationValues.equivalence().pairwise().hash(annotation.getAnnotationValues())
              });
        }

        @Override
        public String toString() {
          return "XAnnotation.equivalence()";
        }
      };

  /**
   * Returns an {@link Equivalence} for {@link XAnnotation}.
   *
   * <p>This equivalence takes into account the order of annotation values.
   */
  public static Equivalence<XAnnotation> equivalence() {
    return XANNOTATION_EQUIVALENCE;
  }

  /**
   * Returns a stable string representation of {@link XAnnotation}.
   *
   * <p>The output string will be the same regardless of whether default values were omitted or
   * their attributes were written in different orders, e.g. {@code @A(b = "b", c = "c")} and
   * {@code @A(c = "c", b = "b", attributeWithDefaultValue = "default value")} will both output the
   * same string. This stability can be useful for things like serialization or reporting error
   * messages.
   */
  public static String toStableString(XAnnotation annotation) {
    try {
      // TODO(b/249283155): Due to a bug in XProcessing, calling various methods on an annotation
      // that is an error type may throw an unexpected exception, so we just output the name.
      if (annotation.getType().isError()) {
        return "@" + annotation.getName(); // SUPPRESS_GET_NAME_CHECK
      }
      return annotation.getAnnotationValues().isEmpty()
          // If the annotation doesn't have values then skip the empty parenthesis.
          ? String.format("@%s", getClassName(annotation).canonicalName())
          : String.format(
              "@%s(%s)",
              getClassName(annotation).canonicalName(),
              // The annotation values returned by XProcessing should already be in the order
              // defined in the annotation class and include default values for any missing values.
              annotation.getAnnotationValues().stream()
                  .map(
                      value -> {
                        String name = value.getName(); // SUPPRESS_GET_NAME_CHECK
                        String valueAsString = XAnnotationValues.toStableString(value);
                        // A single value with name "value" can output the value directly.
                        return annotation.getAnnotationValues().size() == 1
                                && name.contentEquals("value")
                            ? valueAsString
                            : String.format("%s=%s", name, valueAsString);
                      })
                  .collect(joining(", ")));
    } catch (TypeNotPresentException e) {
      return e.typeName();
    }
  }

  /** Returns the value of the given [key] as a type element. */
  public static XTypeElement getAsTypeElement(XAnnotation annotation, String key) {
    return annotation.getAsType(key).getTypeElement();
  }

  /** Returns the value of the given [key] as a list of type elements. */
  public static ImmutableList<XTypeElement> getAsTypeElementList(
      XAnnotation annotation, String key) {
    return annotation.getAsTypeList(key).stream()
        .map(XType::getTypeElement)
        .collect(toImmutableList());
  }

  private XAnnotations() {}
}

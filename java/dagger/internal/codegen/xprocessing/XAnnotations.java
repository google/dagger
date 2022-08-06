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

import static androidx.room.compiler.processing.compat.XConverters.toJavac;

import androidx.room.compiler.processing.XAnnotation;
import com.google.auto.common.AnnotationMirrors;
import com.google.common.base.Equivalence;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import java.util.Arrays;

// TODO(bcorso): Consider moving these methods into XProcessing library.
/** A utility class for {@link XAnnotation} helper methods. */
public final class XAnnotations {

  /** Returns the {@link AnnotationSpec} for the given annotation */
  public static AnnotationSpec getAnnotationSpec(XAnnotation annotation) {
    return AnnotationSpec.get(toJavac(annotation));
  }

  /** Returns the string representation of the given annotation. */
  public static String toString(XAnnotation annotation) {
    return AnnotationMirrors.toString(toJavac(annotation));
  }

  /** Returns the class name of the given annotation */
  public static ClassName getClassName(XAnnotation annotation) {
    return annotation.getType().getTypeElement().getClassName();
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

  private XAnnotations() {}
}

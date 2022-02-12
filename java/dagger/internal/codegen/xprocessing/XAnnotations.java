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
import com.squareup.javapoet.ClassName;

// TODO(bcorso): Consider moving these methods into XProcessing library.
/** A utility class for {@link XAnnotation} helper methods. */
public final class XAnnotations {

  /** Returns the string representation of the given annotation. */
  public static String toString(XAnnotation annotation) {
    return AnnotationMirrors.toString(toJavac(annotation));
  }

  /** Returns the class name of the given annotation */
  public static ClassName getClassName(XAnnotation annotation) {
    return annotation.getType().getTypeElement().getClassName();
  }

  private XAnnotations() {}
}

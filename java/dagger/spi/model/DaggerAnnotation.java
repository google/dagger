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

package dagger.spi.model;

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.value.AutoValue;
import com.google.devtools.ksp.symbol.KSAnnotation;
import javax.annotation.Nullable;
import javax.lang.model.element.AnnotationMirror;

/** Wrapper type for an annotation. */
@AutoValue
public abstract class DaggerAnnotation {
  public static DaggerAnnotation fromJavac(
      DaggerTypeElement annotationTypeElement, AnnotationMirror annotation) {
    return new AutoValue_DaggerAnnotation(annotationTypeElement, annotation, null);
  }

  public static DaggerAnnotation fromKsp(
      DaggerTypeElement annotationTypeElement, KSAnnotation ksp) {
    return new AutoValue_DaggerAnnotation(annotationTypeElement, null, ksp);
  }

  public abstract DaggerTypeElement annotationTypeElement();

  /**
   * java representation for the annotation, returns {@code null} if the annotation isn't a java
   * element.
   */
  @Nullable
  public abstract AnnotationMirror java();

  /** KSP declaration for the annotation, returns {@code null} not using KSP. */
  @Nullable
  public abstract KSAnnotation ksp();

  public DaggerProcessingEnv.Backend backend() {
    if (java() != null) {
      return DaggerProcessingEnv.Backend.JAVAC;
    } else if (ksp() != null) {
      return DaggerProcessingEnv.Backend.KSP;
    }
    throw new AssertionError("Unexpected backend");
  }

  @Override
  public String toString() {
    switch (backend()) {
      case JAVAC:
        return AnnotationMirrors.toString(java());
      case KSP:
        return ksp().toString();
    }
    throw new IllegalStateException(String.format("Backend %s not supported yet.", backend()));
  }
}

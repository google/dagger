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

import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static androidx.room.compiler.processing.compat.XConverters.toKS;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XProcessingEnv;
import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.devtools.ksp.symbol.KSAnnotation;
import dagger.internal.codegen.xprocessing.XAnnotations;
import javax.annotation.Nullable;
import javax.lang.model.element.AnnotationMirror;

/** Wrapper type for an annotation. */
@AutoValue
public abstract class DaggerAnnotation {
  public static DaggerAnnotation from(XAnnotation annotation, XProcessingEnv env) {
    Preconditions.checkNotNull(annotation);
    DaggerProcessingEnv.Backend backend =
        DaggerProcessingEnv.Backend.valueOf(env.getBackend().name());
    String representation = XAnnotations.toString(annotation);
    DaggerTypeElement typeElement = DaggerTypeElement.from(annotation.getTypeElement(), env);
    if (backend.equals(DaggerProcessingEnv.Backend.JAVAC)) {
      return builder()
          .annotationTypeElement(typeElement)
          .java(toJavac(annotation))
          .representation(representation)
          .backend(backend)
          .build();
    } else if (backend.equals(DaggerProcessingEnv.Backend.KSP)) {
      return builder()
          .annotationTypeElement(typeElement)
          .ksp(toKS(annotation))
          .representation(representation)
          .backend(backend)
          .build();
    }
    throw new IllegalStateException(String.format("Backend %s is not supported yet.", backend));
  }

  public static Builder builder() {
    return new AutoValue_DaggerAnnotation.Builder();
  }

  /** A builder for {@link DaggerAnnotation}s. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder annotationTypeElement(DaggerTypeElement value);

    @Nullable
    public abstract Builder java(@Nullable AnnotationMirror value);

    @Nullable
    public abstract Builder ksp(@Nullable KSAnnotation value);

    public abstract Builder backend(DaggerProcessingEnv.Backend value);

    public abstract Builder representation(String value);

    public abstract DaggerAnnotation build();
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

  public abstract DaggerProcessingEnv.Backend backend();

  abstract String representation();

  @Override
  public final String toString() {
    return representation();
  }
}

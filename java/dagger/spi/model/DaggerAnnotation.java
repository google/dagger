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

import static dagger.spi.model.CompilerEnvironment.JAVA;
import static dagger.spi.model.CompilerEnvironment.KSP;

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import com.google.common.base.Preconditions;
import com.google.devtools.ksp.symbol.KSAnnotation;
import com.squareup.javapoet.ClassName;
import javax.annotation.Nullable;
import javax.lang.model.element.AnnotationMirror;

/** Wrapper type for an annotation. */
@AutoValue
public abstract class DaggerAnnotation {

  public static DaggerAnnotation fromJava(AnnotationMirror annotationMirror) {
    return new AutoValue_DaggerAnnotation(
        JAVA,
        AnnotationMirrors.equivalence().wrap(Preconditions.checkNotNull(annotationMirror)),
        null);
  }

  public static DaggerAnnotation fromKsp(KSAnnotation ksAnnotation) {
    return new AutoValue_DaggerAnnotation(
        KSP,
        null,
        Preconditions.checkNotNull(ksAnnotation));
  }

  public DaggerTypeElement annotationTypeElement() {
    return DaggerTypeElement.fromJava(
        MoreTypes.asTypeElement(annotationMirror().get().getAnnotationType()));
  }

  public ClassName className() {
    return annotationTypeElement().className();
  }

  public AnnotationMirror java() {
    Preconditions.checkState(compiler() == JAVA);
    return annotationMirror().get();
  }

  public KSAnnotation ksp() {
    Preconditions.checkState(compiler() == KSP);
    return kspInternal();
  }

  public abstract CompilerEnvironment compiler();

  @Nullable
  abstract Equivalence.Wrapper<AnnotationMirror> annotationMirror();

  @Nullable
  abstract KSAnnotation kspInternal();

  @Override
  public final String toString() {
    return (compiler() == JAVA ? java() : ksp()).toString();
  }
}

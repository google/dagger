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

import androidx.room.compiler.processing.XAnnotation;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import com.google.common.base.Preconditions;
import com.squareup.javapoet.ClassName;
import dagger.internal.codegen.xprocessing.XAnnotations;
import javax.lang.model.element.AnnotationMirror;

/** Wrapper type for an annotation. */
@AutoValue
public abstract class DaggerAnnotation {

  public static DaggerAnnotation from(XAnnotation annotation) {
    Preconditions.checkNotNull(annotation);
    return new AutoValue_DaggerAnnotation(XAnnotations.equivalence().wrap(annotation));
  }

  abstract Equivalence.Wrapper<XAnnotation> equivalenceWrapper();

  public DaggerTypeElement annotationTypeElement() {
    return DaggerTypeElement.from(xprocessing().getType().getTypeElement());
  }

  public ClassName className() {
    return annotationTypeElement().className();
  }

  public XAnnotation xprocessing() {
    return equivalenceWrapper().get();
  }

  public AnnotationMirror java() {
    return toJavac(xprocessing());
  }

  @Override
  public final String toString() {
    return XAnnotations.toString(xprocessing());
  }
}

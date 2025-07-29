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

import com.google.devtools.ksp.symbol.KSAnnotation;
import com.google.errorprone.annotations.DoNotMock;
import javax.lang.model.element.AnnotationMirror;

/** Wrapper type for an annotation. */
@DoNotMock("Only use real implementations created by Dagger")
public abstract class DaggerAnnotation {
  public abstract DaggerTypeElement annotationTypeElement();

  /**
   * Returns the Javac representation for the annotation.
   *
   * @throws IllegalStateException if the current backend isn't Javac.
   */
  public abstract AnnotationMirror javac();

  /**
   * Returns the KSP representation for the annotation.
   *
   * @throws IllegalStateException if the current backend isn't KSP.
   */
  public abstract KSAnnotation ksp();

  /** Returns the backend used in this compilation. */
  public abstract DaggerProcessingEnv.Backend backend();
}

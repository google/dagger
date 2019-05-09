/*
 * Copyright (C) 2016 The Dagger Authors.
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

import static com.google.auto.common.AnnotationMirrors.getAnnotationValue;
import static dagger.internal.codegen.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.MoreAnnotationValues.asAnnotationValues;

import com.google.auto.common.AnnotationMirrors;
import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableList;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Name;
import javax.lang.model.type.TypeMirror;

/**
 * A utility class for working with {@link AnnotationMirror} instances, similar to {@link
 * AnnotationMirrors}.
 */
final class MoreAnnotationMirrors {

  private MoreAnnotationMirrors() {}

  /**
   * Wraps an {@link Optional} of a type in an {@code Optional} of a {@link Equivalence.Wrapper} for
   * that type.
   */
  static Optional<Equivalence.Wrapper<AnnotationMirror>> wrapOptionalInEquivalence(
      Optional<AnnotationMirror> optional) {
    return optional.map(AnnotationMirrors.equivalence()::wrap);
  }

  /**
   * Unwraps an {@link Optional} of a {@link Equivalence.Wrapper} into an {@code Optional} of the
   * underlying type.
   */
  static Optional<AnnotationMirror> unwrapOptionalEquivalence(
      Optional<Equivalence.Wrapper<AnnotationMirror>> wrappedOptional) {
    return wrappedOptional.map(Equivalence.Wrapper::get);
  }

  static Name simpleName(AnnotationMirror annotationMirror) {
    return annotationMirror.getAnnotationType().asElement().getSimpleName();
  }

  /**
   * Returns the list of types that is the value named {@code name} from {@code annotationMirror}.
   *
   * @throws IllegalArgumentException unless that member represents an array of types
   */
  static ImmutableList<TypeMirror> getTypeListValue(
      AnnotationMirror annotationMirror, String name) {
    return asAnnotationValues(getAnnotationValue(annotationMirror, name))
        .stream()
        .map(MoreAnnotationValues::asType)
        .collect(toImmutableList());
  }
}

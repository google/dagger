/*
 * Copyright (C) 2014 Google, Inc.
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;

import java.lang.annotation.Annotation;
import java.util.Iterator;
import java.util.List;

import javax.inject.Qualifier;
import javax.inject.Scope;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;

/**
 * Utilities relating to annotations defined in the {@code javax.inject} package.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class InjectionAnnotations {
  static Optional<AnnotationMirror> getScopeAnnotation(Element e) {
    checkNotNull(e);
    return getAnnotatedAnnotation(e, Scope.class);
  }

  static Optional<AnnotationMirror> getQualifier(Element e) {
    checkNotNull(e);
    return getAnnotatedAnnotation(e, Qualifier.class);
  }

  private static Optional<AnnotationMirror> getAnnotatedAnnotation(Element e,
      final Class<? extends Annotation> annotationType) {
    List<? extends AnnotationMirror> annotations = e.getAnnotationMirrors();
    Iterator<? extends AnnotationMirror> qualifiers = FluentIterable.from(annotations)
        .filter(new Predicate<AnnotationMirror>() {
          @Override
          public boolean apply(AnnotationMirror input) {
            return input.getAnnotationType().asElement().getAnnotation(annotationType) != null;
          }
        })
        .iterator();
    if (qualifiers.hasNext()) {
      AnnotationMirror qualifier = qualifiers.next();
      checkState(!qualifiers.hasNext(),
          "More than one " + annotationType.getName() + " was present.");
      return Optional.of(qualifier);
    } else {
      return Optional.absent();
    }
  }

  private InjectionAnnotations() {}
}

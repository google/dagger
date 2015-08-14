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
package dagger2.internal.codegen;

import com.google.auto.common.AnnotationMirrors;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import javax.inject.Qualifier;
import javax.inject.Scope;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Utilities relating to annotations defined in the {@code javax.inject} package.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class InjectionAnnotations {
  static Optional<AnnotationMirror> getScopeAnnotation(Element e) {
    checkNotNull(e);
    ImmutableSet<? extends AnnotationMirror> scopeAnnotations = getScopes(e);
    switch (scopeAnnotations.size()) {
      case 0:
        return Optional.absent();
      case 1:
        return Optional.<AnnotationMirror>of(scopeAnnotations.iterator().next());
      default:
        throw new IllegalArgumentException(
            e + " was annotated with more than one @Scope annotation");
    }
  }

  static Optional<AnnotationMirror> getQualifier(Element e) {
    checkNotNull(e);
    ImmutableSet<? extends AnnotationMirror> qualifierAnnotations = getQualifiers(e);
    switch (qualifierAnnotations.size()) {
      case 0:
        return Optional.absent();
      case 1:
        return Optional.<AnnotationMirror>of(qualifierAnnotations.iterator().next());
      default:
        throw new IllegalArgumentException(
            e + " was annotated with more than one @Qualifier annotation");
    }
  }

  static ImmutableSet<? extends AnnotationMirror> getQualifiers(Element element) {
    return AnnotationMirrors.getAnnotatedAnnotations(element, Qualifier.class);
  }

  static ImmutableSet<? extends AnnotationMirror> getScopes(Element element) {
    return AnnotationMirrors.getAnnotatedAnnotations(element, Scope.class);
  }

  private InjectionAnnotations() {}
}

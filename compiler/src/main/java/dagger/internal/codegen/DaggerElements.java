/*
 * Copyright (C) 2013 The Dagger Authors.
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

import static com.google.auto.common.MoreElements.getLocalAndInheritedMethods;
import static com.google.auto.common.MoreElements.hasModifiers;
import static com.google.common.collect.Lists.asList;
import static dagger.internal.codegen.Util.toImmutableSet;
import static java.util.stream.Collectors.toSet;
import static javax.lang.model.element.Modifier.ABSTRACT;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleElementVisitor6;
import javax.lang.model.util.Types;

/**
 * Utilities for working with {@link Element} objects. Each is a candidate to move to {@link
 * MoreElements}.
 */
final class DaggerElements {

  static ImmutableSet<ExecutableElement> getUnimplementedMethods(
      Elements elements, Types types, TypeElement type) {
    return FluentIterable.from(getLocalAndInheritedMethods(type, types, elements))
        .filter(hasModifiers(ABSTRACT))
        .toSet();
  }

  /**
   * A visitor that returns the input or the closest enclosing element that is a
   * {@link TypeElement}.
   */
  static final ElementVisitor<TypeElement, Void> ENCLOSING_TYPE_ELEMENT =
      new SimpleElementVisitor6<TypeElement, Void>() {
        @Override
        protected TypeElement defaultAction(Element e, Void p) {
          return visit(e.getEnclosingElement());
        }
  
        @Override
        public TypeElement visitType(TypeElement e, Void p) {
          return e;
        }
      };
      
  /**
   * Returns {@code true} iff the given element has an {@link AnnotationMirror} whose
   * {@linkplain AnnotationMirror#getAnnotationType() annotation type} has the same canonical name
   * as any of that of {@code annotationClasses}.
   */
  static boolean isAnyAnnotationPresent(
      Element element, Iterable<? extends Class<? extends Annotation>> annotationClasses) {
    for (Class<? extends Annotation> annotation : annotationClasses) {
      if (MoreElements.isAnnotationPresent(element, annotation)) {
        return true;
      }
    }
    return false;
  }

  @SafeVarargs
  static boolean isAnyAnnotationPresent(
      Element element,
      Class<? extends Annotation> first,
      Class<? extends Annotation>... otherAnnotations) {
    return isAnyAnnotationPresent(element, asList(first, otherAnnotations));
  }

  /**
   * Returns {@code true} iff the given element has an {@link AnnotationMirror} whose {@linkplain
   * AnnotationMirror#getAnnotationType() annotation type} is equivalent to {@code annotationType}.
   */
  static boolean isAnnotationPresent(Element element, TypeMirror annotationType) {
    return element
        .getAnnotationMirrors()
        .stream()
        .map(AnnotationMirror::getAnnotationType)
        .anyMatch(candidate -> MoreTypes.equivalence().equivalent(candidate, annotationType));
  }

  /**
   * Returns the annotation present on {@code element} whose type is {@code first} or within {@code
   * rest}, checking each annotation type in order.
   */
  @SafeVarargs
  static Optional<AnnotationMirror> getAnyAnnotation(
      Element element, Class<? extends Annotation> first, Class<? extends Annotation>... rest) {
    return getAnyAnnotation(element, asList(first, rest));
  }

  /**
   * Returns the annotation present on {@code element} whose type is in {@code annotations},
   * checking each annotation type in order.
   */
  static Optional<AnnotationMirror> getAnyAnnotation(
      Element element, Collection<? extends Class<? extends Annotation>> annotations) {
    return element
        .getAnnotationMirrors()
        .stream()
        .filter(hasAnnotationTypeIn(annotations))
        .map((AnnotationMirror a) -> a) // Avoid returning Optional<? extends AnnotationMirror>.
        .findFirst();
  }

  /** Returns the annotations present on {@code element} of all types. */
  @SafeVarargs
  static ImmutableSet<AnnotationMirror> getAllAnnotations(
      Element element, Class<? extends Annotation> first, Class<? extends Annotation>... rest) {
    return element
        .getAnnotationMirrors()
        .stream()
        .filter(hasAnnotationTypeIn(asList(first, rest)))
        .collect(toImmutableSet());
  }

  /**
   * Returns an {@link AnnotationMirror} for the annotation of type {@code annotationClass} on
   * {@code element}, or {@link Optional#empty()} if no such annotation exists. This method is a
   * safer alternative to calling {@link Element#getAnnotation} as it avoids any interaction with
   * annotation proxies.
   */
  static Optional<AnnotationMirror> getAnnotationMirror(
      Element element, Class<? extends Annotation> annotationClass) {
    return Optional.ofNullable(MoreElements.getAnnotationMirror(element, annotationClass).orNull());
  }

  private static Predicate<AnnotationMirror> hasAnnotationTypeIn(
      Collection<? extends Class<? extends Annotation>> annotations) {
    Set<String> annotationClassNames =
        annotations.stream().map(Class::getCanonicalName).collect(toSet());
    return annotation ->
        annotationClassNames.contains(
            MoreTypes.asTypeElement(annotation.getAnnotationType()).getQualifiedName().toString());
  }
}

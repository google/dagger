/*
 * Copyright (C) 2015 Google, Inc.
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

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.MoreTypes;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import dagger.Reusable;
import dagger.producers.ProductionScope;
import java.lang.annotation.Annotation;
import javax.inject.Singleton;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.ErrorMessages.stripCommonTypePrefixes;
import static dagger.internal.codegen.InjectionAnnotations.getScopes;

/**
 * A representation of the scope (or lack of it) associated with a component, providing method
 * or injection location.
 */
final class Scope {
  /**
   * The underlying {@link AnnotationMirror} that represents the scope annotation.
   */
  private final AnnotationMirror annotationMirror;

  private Scope(AnnotationMirror annotationMirror) {
    this.annotationMirror = checkNotNull(annotationMirror);
  }

  /** Returns all of the associated scoped annotations from the source code element. */
  static ImmutableSet<Scope> scopesOf(Element element) {
    return FluentIterable.from(getScopes(element)).
        transform(new Function<AnnotationMirror, Scope>() {
          @Override public Scope apply(AnnotationMirror annotationMirror) {
            return new Scope(annotationMirror);
          }
        }).toSet();
  }

  /**
   * Returns at most one associated scoped annotation from the source code element, throwing an
   * exception if there are more than one.
   */
  static Optional<Scope> uniqueScopeOf(Element element) {
    ImmutableSet<? extends AnnotationMirror> scopeAnnotations = getScopes(element);
    if (scopeAnnotations.isEmpty()) {
      return Optional.absent();
    }
    return Optional.of(new Scope(Iterables.getOnlyElement(scopeAnnotations)));
  }

  /**
   * Returns a representation for {@link ProductionScope @ProductionScope} scope.
   */
  static Scope productionScope(Elements elements) {
    return scope(elements, ProductionScope.class);
  }

  /**
   * Returns a representation for {@link Singleton @Singleton} scope.
   */
  static Scope singletonScope(Elements elements) {
    return scope(elements, Singleton.class);
  }

  /**
   * Returns a representation for {@link Reusable @Reusable} scope.
   */
  static Scope reusableScope(Elements elements) {
    return scope(elements, Reusable.class);
  }

  private static Scope scope(Elements elements, Class<? extends Annotation> scopeAnnotationClass) {
    return new Scope(
        SimpleAnnotationMirror.of(
            elements.getTypeElement(scopeAnnotationClass.getCanonicalName())));
  }

  /**
   * Returns the readable source representation (name with @ prefix) of the annotation type.
   *
   * <p>It's readable source because it has had common package prefixes removed, e.g.
   * {@code @javax.inject.Singleton} is returned as {@code @Singleton}.
   *
   * <p>Make sure that the scope is actually {@link #isPresent() present} before calling as it will
   * throw an {@link IllegalStateException} otherwise. This does not return any annotation values
   * as according to {@link javax.inject.Scope} scope annotations are not supposed to use them.
   */
  public String getReadableSource() {
    return stripCommonTypePrefixes("@" + getQualifiedName());
  }

  /**
   * Returns the fully qualified name of the annotation type.
   *
   * <p>Make sure that the scope is actually {@link #isPresent() present} before calling as it will
   * throw an {@link IllegalStateException} otherwise. This does not return any annotation values
   * as according to {@link javax.inject.Scope} scope annotations are not supposed to use them.
   */
  public String getQualifiedName() {
    Preconditions.checkState(annotationMirror != null,
        "Cannot create a stripped source representation of no annotation");
    TypeElement typeElement = MoreTypes.asTypeElement(annotationMirror.getAnnotationType());
    return typeElement.getQualifiedName().toString();
  }

  /**
   * Scopes are equal if the underlying {@link AnnotationMirror} are equivalent according to
   * {@link AnnotationMirrors#equivalence()}.
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    } else if (obj instanceof Scope) {
      Scope that = (Scope) obj;
      return AnnotationMirrors.equivalence()
        .equivalent(this.annotationMirror, that.annotationMirror);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return AnnotationMirrors.equivalence().hash(annotationMirror);
  }

  /**
   * Returns a debug representation of the scope.
   */
  @Override
  public String toString() {
    return annotationMirror.toString();
  }
}

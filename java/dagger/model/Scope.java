/*
 * Copyright (C) 2015 The Dagger Authors.
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

package dagger.model;

import static com.google.auto.common.AnnotationMirrors.getAnnotatedAnnotations;
import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.DoNotMock;
import dagger.Reusable;
import dagger.producers.ProductionScope;
import dagger.releasablereferences.CanReleaseReferences;
import java.lang.annotation.Annotation;
import java.util.Optional;
import javax.inject.Singleton;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/** A representation of a {@link javax.inject.Scope}. */
@AutoValue
// TODO(ronshapiro): point to SimpleAnnotationMirror
@DoNotMock("Use Scope.scope(AnnotationMirror) to create an instance")
public abstract class Scope {
  abstract Equivalence.Wrapper<AnnotationMirror> wrappedScopeAnnotation();

  /** The {@link AnnotationMirror} that represents the scope annotation. */
  public final AnnotationMirror scopeAnnotation() {
    return wrappedScopeAnnotation().get();
  }

  /** The scope annotation element. */
  public final TypeElement scopeAnnotationElement() {
    return MoreTypes.asTypeElement(scopeAnnotation().getAnnotationType());
  }

  /**
   * Creates a {@link Scope} object from the {@link javax.inject.Scope}-annotated annotation type.
   */
  public static Scope scope(AnnotationMirror scopeAnnotation) {
    checkArgument(isScope(scopeAnnotation));
    return new AutoValue_Scope(AnnotationMirrors.equivalence().wrap(scopeAnnotation));
  }

  /**
   * Returns {@code true} if {@link #scopeAnnotation()} is a {@link javax.inject.Scope} annotation.
   */
  public static boolean isScope(AnnotationMirror scopeAnnotation) {
    return isScope(MoreElements.asType(scopeAnnotation.getAnnotationType().asElement()));
  }

  /**
   * Returns {@code true} if {@code scopeAnnotationType} is a {@link javax.inject.Scope} annotation.
   */
  public static boolean isScope(TypeElement scopeAnnotationType) {
    return isAnnotationPresent(scopeAnnotationType, javax.inject.Scope.class);
  }

  /** Returns {@code true} if this scope is the {@link Singleton @Singleton} scope. */
  public final boolean isSingleton() {
    return isScope(Singleton.class);
  }

  /** Returns {@code true} if this scope is the {@link Reusable @Reusable} scope. */
  public final boolean isReusable() {
    return isScope(Reusable.class);
  }

  /** Returns {@code true} if this scope is the {@link ProductionScope @ProductionScope} scope. */
  public final boolean isProductionScope() {
    return isScope(ProductionScope.class);
  }

  private boolean isScope(Class<? extends Annotation> annotation) {
    return scopeAnnotationElement().getQualifiedName().contentEquals(annotation.getCanonicalName());
  }

  /**
   * Returns {@code true} for scopes that are annotated with {@link CanReleaseReferences} or some
   * other annotation that is itself annotated with {@link CanReleaseReferences}.
   */
  // Implementation note: you need to check
  // BindingGraph.scopesRequiringReleasableReferenceManagers() to see if a given binding graph
  // actually binds a ReleasableReferenceManager for this scope.
  public final boolean canReleaseReferences() {
    return isAnnotationPresent(scopeAnnotationElement(), CanReleaseReferences.class)
        || !releasableReferencesMetadata().isEmpty();
  }

  /**
   * Returns the set of annotations on the scope that are themselves annotated with {@link
   * CanReleaseReferences}. These annotations are used as metadata for {@link
   * dagger.releasablereferences.TypedReleasableReferenceManager}.
   */
  public final ImmutableSet<AnnotationMirror> releasableReferencesMetadata() {
    return ImmutableSet.copyOf(
        getAnnotatedAnnotations(scopeAnnotationElement(), CanReleaseReferences.class));
  }

  /**
   * Returns the {@linkplain #releasableReferencesMetadata() releasable references metadata}
   * annotation of the given type, if there is one for this scope.
   */
  public final Optional<AnnotationMirror> releasableReferencesMetadata(TypeMirror metadataType) {
    for (AnnotationMirror metadata : releasableReferencesMetadata()) {
      if (MoreTypes.equivalence().equivalent(metadata.getAnnotationType(), metadataType)) {
        return Optional.of(metadata);
      }
    }
    return Optional.empty();
  }

  /** Returns a debug representation of the scope. */
  @Override
  public String toString() {
    return scopeAnnotation().toString();
  }
}

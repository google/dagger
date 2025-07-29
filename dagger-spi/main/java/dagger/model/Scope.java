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

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import com.squareup.javapoet.ClassName;
import dagger.Reusable;
import javax.inject.Singleton;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;

/** A representation of a {@link javax.inject.Scope}. */
@AutoValue
// TODO(ronshapiro): point to SimpleAnnotationMirror
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
    return isAnnotationPresent(scopeAnnotationType, SCOPE.canonicalName())
        || isAnnotationPresent(scopeAnnotationType, SCOPE_JAVAX.canonicalName());
  }

  private static final ClassName PRODUCTION_SCOPE =
      ClassName.get("dagger.producers", "ProductionScope");
  private static final ClassName SINGLETON = ClassName.get("jakarta.inject", "Singleton");
  private static final ClassName SINGLETON_JAVAX = ClassName.get("javax.inject", "Singleton");
  private static final ClassName REUSABLE = ClassName.get("dagger", "Reusable");
  private static final ClassName SCOPE = ClassName.get("jakarta.inject", "Scope");
  private static final ClassName SCOPE_JAVAX = ClassName.get("javax.inject", "Scope");

  /** Returns {@code true} if this scope is the {@link Singleton @Singleton} scope. */
  public final boolean isSingleton() {
    return isScope(SINGLETON) || isScope(SINGLETON_JAVAX);
  }

  /** Returns {@code true} if this scope is the {@link Reusable @Reusable} scope. */
  public final boolean isReusable() {
    return isScope(REUSABLE);
  }

  /** Returns {@code true} if this scope is the {@code @ProductionScope} scope. */
  public final boolean isProductionScope() {
    return isScope(PRODUCTION_SCOPE);
  }

  private boolean isScope(ClassName className) {
    return scopeAnnotationElement().getQualifiedName().contentEquals(className.canonicalName());
  }

  /** Returns a debug representation of the scope. */
  @Override
  public final String toString() {
    return scopeAnnotation().toString();
  }
}

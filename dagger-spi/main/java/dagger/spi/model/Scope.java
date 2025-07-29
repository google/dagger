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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.common.MoreElements;
import com.google.auto.value.AutoValue;

/** A representation of a {@link javax.inject.Scope}. */
@AutoValue
public abstract class Scope {
  /**
   * Creates a {@link Scope} object from the {@link javax.inject.Scope}-annotated annotation type.
   */
  public static Scope scope(DaggerAnnotation scopeAnnotation) {
    checkArgument(isScope(scopeAnnotation));
    return new AutoValue_Scope(scopeAnnotation);
  }

  /**
   * Returns {@code true} if {@link #scopeAnnotation()} is a {@link javax.inject.Scope} annotation.
   */
  public static boolean isScope(DaggerAnnotation scopeAnnotation) {
    return isScope(scopeAnnotation.annotationTypeElement());
  }

  /**
   * Returns {@code true} if {@code scopeAnnotationType} is a {@link javax.inject.Scope} annotation.
   */
  public static boolean isScope(DaggerTypeElement scopeAnnotationType) {
    switch (scopeAnnotationType.backend()) {
      case JAVAC:
        return MoreElements.isAnnotationPresent(scopeAnnotationType.javac(), SCOPE)
            || MoreElements.isAnnotationPresent(scopeAnnotationType.javac(), SCOPE_JAVAX);
      case KSP:
        return KspUtilsKt.hasAnnotation(scopeAnnotationType.ksp(), SCOPE)
            || KspUtilsKt.hasAnnotation(scopeAnnotationType.ksp(), SCOPE_JAVAX);
    }
    throw new IllegalStateException(
        String.format("Backend %s not supported yet.", scopeAnnotationType.backend()));
  }

  private boolean isScope(String annotationName) {
    return scopeAnnotation().toString().equals(annotationName);
  }

  /** The {@link DaggerAnnotation} that represents the scope annotation. */
  public abstract DaggerAnnotation scopeAnnotation();

  private static final String PRODUCTION_SCOPE = "dagger.producers.ProductionScope";
  private static final String SINGLETON = "jakarta.inject.Singleton";
  private static final String SINGLETON_JAVAX = "javax.inject.Singleton";
  private static final String REUSABLE = "dagger.Reusable";
  private static final String SCOPE = "jakarta.inject.Scope";
  private static final String SCOPE_JAVAX = "javax.inject.Scope";

  /** Returns {@code true} if this scope is the {@link javax.inject.Singleton @Singleton} scope. */
  public final boolean isSingleton() {
    return isScope(SINGLETON) || isScope(SINGLETON_JAVAX);
  }

  /** Returns {@code true} if this scope is the {@link dagger.Reusable @Reusable} scope. */
  public final boolean isReusable() {
    return isScope(REUSABLE);
  }

  /**
   * Returns {@code true} if this scope is the {@code @ProductionScope} scope.
   */
  public final boolean isProductionScope() {
    return isScope(PRODUCTION_SCOPE);
  }

  /** Returns a debug representation of the scope. */
  @Override
  public final String toString() {
    return scopeAnnotation().toString();
  }
}

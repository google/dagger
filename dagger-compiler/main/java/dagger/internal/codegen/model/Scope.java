/*
 * Copyright (C) 2023 The Dagger Authors.
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

package dagger.internal.codegen.model;

import static com.google.common.base.Preconditions.checkArgument;

import androidx.room.compiler.codegen.XClassName;
import com.google.auto.value.AutoValue;
import dagger.internal.codegen.xprocessing.XAnnotations;

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
    return scopeAnnotationType.xprocessing().hasAnnotation(SCOPE)
        || scopeAnnotationType.xprocessing().hasAnnotation(SCOPE_JAVAX);
  }

  private static final XClassName PRODUCTION_SCOPE =
      XClassName.get("dagger.producers", "ProductionScope");
  private static final XClassName SINGLETON = XClassName.get("jakarta.inject", "Singleton");
  private static final XClassName SINGLETON_JAVAX = XClassName.get("javax.inject", "Singleton");
  private static final XClassName REUSABLE = XClassName.get("dagger", "Reusable");
  private static final XClassName SCOPE = XClassName.get("jakarta.inject", "Scope");
  private static final XClassName SCOPE_JAVAX = XClassName.get("javax.inject", "Scope");


  /** The {@link DaggerAnnotation} that represents the scope annotation. */
  public abstract DaggerAnnotation scopeAnnotation();

  /** Returns {@code true} if this scope is the {@link javax.inject.Singleton @Singleton} scope. */
  public final boolean isSingleton() {
    return isScope(SINGLETON) || isScope(SINGLETON_JAVAX);
  }

  /** Returns {@code true} if this scope is the {@link dagger.Reusable @Reusable} scope. */
  public final boolean isReusable() {
    return isScope(REUSABLE);
  }

  /**
   * Returns {@code true} if this scope is the {@link
   * dagger.producers.ProductionScope @ProductionScope} scope.
   */
  public final boolean isProductionScope() {
    return isScope(PRODUCTION_SCOPE);
  }

  private boolean isScope(XClassName annotation) {
    return XAnnotations.asClassName(scopeAnnotation().xprocessing()).equals(annotation);
  }

  /** Returns a debug representation of the scope. */
  @Override
  public final String toString() {
    return scopeAnnotation().toString();
  }
}

/*
 * Copyright (C) 2017 The Dagger Authors.
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

import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.DiagnosticFormatting.stripCommonTypePrefixes;

import com.google.auto.common.AnnotationMirrors;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.model.Scope;
import dagger.producers.ProductionScope;
import java.lang.annotation.Annotation;
import java.util.Optional;
import javax.inject.Singleton;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/** Common names and convenience methods for {@link Scope}s. */
final class Scopes {
  /**
   * Creates a {@link Scope} object from the {@link javax.inject.Scope}-annotated annotation type.
   */
  static Scope scope(TypeElement scopeType) {
    return Scope.scope(SimpleAnnotationMirror.of(scopeType));
  }

  /** Returns a representation for {@link ProductionScope @ProductionScope} scope. */
  static Scope productionScope(DaggerElements elements) {
    return scope(elements, ProductionScope.class);
  }

  /** Returns a representation for {@link Singleton @Singleton} scope. */
  static Scope singletonScope(DaggerElements elements) {
    return scope(elements, Singleton.class);
  }

  private static Scope scope(
      DaggerElements elements, Class<? extends Annotation> scopeAnnotationClass) {
    return scope(elements.getTypeElement(scopeAnnotationClass));
  }

  /**
   * Returns at most one associated scoped annotation from the source code element, throwing an
   * exception if there are more than one.
   */
  static Optional<Scope> uniqueScopeOf(Element element) {
    // TODO(ronshapiro): Use MoreCollectors.toOptional() once we can use guava-jre
    return Optional.ofNullable(getOnlyElement(scopesOf(element), null));
  }

  /**
   * Returns the readable source representation (name with @ prefix) of the scope's annotation type.
   *
   * <p>It's readable source because it has had common package prefixes removed, e.g.
   * {@code @javax.inject.Singleton} is returned as {@code @Singleton}.
   */
  static String getReadableSource(Scope scope) {
    return stripCommonTypePrefixes(scope.toString());
  }

  /** Returns all of the associated scopes for a source code element. */
  static ImmutableSet<Scope> scopesOf(Element element) {
    return AnnotationMirrors.getAnnotatedAnnotations(element, javax.inject.Scope.class)
        .stream()
        .map(Scope::scope)
        .collect(toImmutableSet());
  }
}

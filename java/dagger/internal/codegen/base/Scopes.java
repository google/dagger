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

package dagger.internal.codegen.base;

import static dagger.internal.codegen.base.DiagnosticFormatting.stripCommonTypePrefixes;
import static dagger.internal.codegen.extension.DaggerCollectors.toOptional;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import com.google.auto.common.AnnotationMirrors;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.producers.ProductionScope;
import dagger.spi.model.DaggerAnnotation;
import dagger.spi.model.Scope;
import java.util.Optional;
import javax.inject.Singleton;
import javax.lang.model.element.Element;

/** Common names and convenience methods for {@link Scope}s. */
public final class Scopes {

  /** Returns a representation for {@link ProductionScope @ProductionScope} scope. */
  public static Scope productionScope(DaggerElements elements) {
    return scope(elements, TypeNames.PRODUCTION_SCOPE);
  }

  /** Returns a representation for {@link Singleton @Singleton} scope. */
  public static Scope singletonScope(DaggerElements elements) {
    return scope(elements, TypeNames.SINGLETON);
  }

  /**
   * Creates a {@link Scope} object from the {@link javax.inject.Scope}-annotated annotation type.
   */
  private static Scope scope(DaggerElements elements, ClassName scopeAnnotationClassName) {
    return Scope.scope(
        DaggerAnnotation.fromJava(
            SimpleAnnotationMirror.of(
                elements.getTypeElement(scopeAnnotationClassName.canonicalName()))));
  }

  /**
   * Returns at most one associated scoped annotation from the source code element, throwing an
   * exception if there are more than one.
   */
  public static Optional<Scope> uniqueScopeOf(Element element) {
    return scopesOf(element).stream().collect(toOptional());
  }

  /**
   * Returns the readable source representation (name with @ prefix) of the scope's annotation type.
   *
   * <p>It's readable source because it has had common package prefixes removed, e.g.
   * {@code @javax.inject.Singleton} is returned as {@code @Singleton}.
   */
  public static String getReadableSource(Scope scope) {
    return stripCommonTypePrefixes(scope.toString());
  }

  /** Returns all of the associated scopes for a source code element. */
  public static ImmutableSet<Scope> scopesOf(Element element) {
    // TODO(bcorso): Replace Scope class reference with class name once auto-common is updated.
    return AnnotationMirrors.getAnnotatedAnnotations(element, javax.inject.Scope.class)
        .stream()
        .map(DaggerAnnotation::fromJava)
        .map(Scope::scope)
        .collect(toImmutableSet());
  }
}

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
import static dagger.internal.codegen.xprocessing.XElements.getAnnotatedAnnotations;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XProcessingEnv;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.spi.model.DaggerAnnotation;
import dagger.spi.model.Scope;
import java.util.Optional;

/** Common names and convenience methods for {@link Scope}s. */
public final class Scopes {

  /** Returns a representation for {@link dagger.producers.ProductionScope} scope. */
  public static Scope productionScope(XProcessingEnv processingEnv) {
    return Scope.scope(DaggerAnnotation.from(ProducerAnnotations.productionScope(processingEnv)));
  }

  /**
   * Returns at most one associated scoped annotation from the source code element, throwing an
   * exception if there are more than one.
   */
  public static Optional<Scope> uniqueScopeOf(XElement element) {
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
  public static ImmutableSet<Scope> scopesOf(XElement element) {
    return Sets.union(getAnnotatedAnnotations(element, TypeNames.SCOPE),
        getAnnotatedAnnotations(element, TypeNames.SCOPE_JAVAX)).stream()
        .map(DaggerAnnotation::from)
        .map(Scope::scope)
        .collect(toImmutableSet());
  }
}

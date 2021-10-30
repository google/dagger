/*
 * Copyright (C) 2014 The Dagger Authors.
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

package dagger.internal.codegen.binding;

import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.base.ComponentAnnotation.subcomponentAnnotation;
import static dagger.internal.codegen.base.MoreAnnotationMirrors.getTypeListValue;
import static dagger.internal.codegen.binding.ComponentCreatorAnnotation.subcomponentCreatorAnnotations;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.langmodel.DaggerElements.isAnyAnnotationPresent;
import static dagger.internal.codegen.xprocessing.XElements.hasAnyAnnotation;
import static javax.lang.model.util.ElementFilter.typesIn;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.Component;
import dagger.Module;
import java.util.List;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * Utility methods related to dagger configuration annotations (e.g.: {@link Component} and {@link
 * Module}).
 */
public final class ConfigurationAnnotations {

  public static Optional<XTypeElement> getSubcomponentCreator(XTypeElement subcomponent) {
    checkArgument(subcomponentAnnotation(subcomponent).isPresent());
    return subcomponent.getEnclosedTypeElements().stream()
        .filter(ConfigurationAnnotations::isSubcomponentCreator)
        // TODO(bcorso): Consider doing toOptional() instead since there should be at most 1.
        .findFirst();
  }

  public static Optional<TypeElement> getSubcomponentCreator(TypeElement subcomponent) {
    checkArgument(subcomponentAnnotation(subcomponent).isPresent());
    return typesIn(subcomponent.getEnclosedElements()).stream()
        .filter(ConfigurationAnnotations::isSubcomponentCreator)
        // TODO(bcorso): Consider doing toOptional() instead since there should be at most 1.
        .findFirst();
  }

  static boolean isSubcomponentCreator(XElement element) {
    return isSubcomponentCreator(toJavac(element));
  }

  static boolean isSubcomponentCreator(Element element) {
    return isAnyAnnotationPresent(element, subcomponentCreatorAnnotations());
  }

  // Dagger 1 support.
  public static ImmutableList<TypeMirror> getModuleInjects(AnnotationMirror moduleAnnotation) {
    checkNotNull(moduleAnnotation);
    return getTypeListValue(moduleAnnotation, "injects");
  }

  /** Returns the first type that specifies this' nullability, or empty if none. */
  public static Optional<XAnnotation> getNullableAnnotation(XElement element) {
    return element.getAllAnnotations().stream()
        .filter(annotation -> annotation.getName().contentEquals("Nullable"))
        .findFirst();
  }

  public static Optional<XType> getNullableType(XElement element) {
    return getNullableAnnotation(element).map(XAnnotation::getType);
  }

  /** Returns the first type that specifies this' nullability, or empty if none. */
  public static Optional<DeclaredType> getNullableType(Element element) {
    List<? extends AnnotationMirror> mirrors = element.getAnnotationMirrors();
    for (AnnotationMirror mirror : mirrors) {
      if (mirror.getAnnotationType().asElement().getSimpleName().contentEquals("Nullable")) {
        return Optional.of(mirror.getAnnotationType());
      }
    }
    return Optional.empty();
  }

  /** Returns the enclosed types annotated with the given annotation. */
  public static ImmutableSet<XTypeElement> enclosedAnnotatedTypes(
      XTypeElement typeElement, ImmutableSet<ClassName> annotations) {
    return typeElement.getEnclosedTypeElements().stream()
        .filter(enclosedType -> hasAnyAnnotation(enclosedType, annotations))
        .collect(toImmutableSet());
  }

  private ConfigurationAnnotations() {}
}

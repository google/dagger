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

import static com.google.common.base.Preconditions.checkArgument;
import static dagger.internal.codegen.base.ComponentAnnotation.subcomponentAnnotations;
import static dagger.internal.codegen.base.ComponentCreatorAnnotation.subcomponentCreatorAnnotations;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.xprocessing.XAnnotations.getClassName;
import static dagger.internal.codegen.xprocessing.XElements.hasAnyAnnotation;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.Component;
import dagger.Module;
import java.util.List;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.type.DeclaredType;

/**
 * Utility methods related to dagger configuration annotations (e.g.: {@link Component} and {@link
 * Module}).
 */
public final class ConfigurationAnnotations {

  public static Optional<XTypeElement> getSubcomponentCreator(XTypeElement subcomponent) {
    checkArgument(subcomponent.hasAnyAnnotation(subcomponentAnnotations()));
    return subcomponent.getEnclosedTypeElements().stream()
        .filter(ConfigurationAnnotations::isSubcomponentCreator)
        // TODO(bcorso): Consider doing toOptional() instead since there should be at most 1.
        .findFirst();
  }

  static boolean isSubcomponentCreator(XElement element) {
    return hasAnyAnnotation(element, subcomponentCreatorAnnotations());
  }

  /** Returns the first type that specifies this' nullability, or empty if none. */
  public static Optional<XAnnotation> getNullableAnnotation(XElement element) {
    return element.getAllAnnotations().stream()
        .filter(annotation -> getClassName(annotation).simpleName().contentEquals("Nullable"))
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

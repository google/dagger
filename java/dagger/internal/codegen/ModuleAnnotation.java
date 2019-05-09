/*
 * Copyright (C) 2019 The Dagger Authors.
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

import static com.google.auto.common.AnnotationMirrors.getAnnotationValue;
import static com.google.auto.common.MoreTypes.asTypeElement;
import static com.google.common.base.Preconditions.checkArgument;
import static dagger.internal.codegen.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.MoreAnnotationValues.asAnnotationValues;
import static dagger.internal.codegen.langmodel.DaggerElements.getAnyAnnotation;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dagger.Module;
import dagger.producers.ProducerModule;
import java.lang.annotation.Annotation;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.TypeElement;

/** A {@code @Module} or {@code @ProducerModule} annotation. */
@AutoValue
abstract class ModuleAnnotation {
  private static final ImmutableSet<Class<? extends Annotation>> MODULE_ANNOTATIONS =
      ImmutableSet.of(Module.class, ProducerModule.class);

  /** The annotation itself. */
  // This does not use AnnotationMirrors.equivalence() because we want the actual annotation
  // instance.
  abstract AnnotationMirror annotation();

  /** The type of the annotation. */
  @Memoized
  Class<?> annotationClass() {
    try {
      return Class.forName(
          asTypeElement(annotation().getAnnotationType()).getQualifiedName().toString());
    } catch (ClassNotFoundException e) {
      AssertionError assertionError = new AssertionError();
      assertionError.initCause(e);
      throw assertionError;
    }
  }

  /**
   * The types specified in the {@code includes} attribute.
   *
   * @throws IllegalArgumentException if any of the values are error types
   */
  @Memoized
  ImmutableList<TypeElement> includes() {
    return includesAsAnnotationValues().stream()
        .map(MoreAnnotationValues::asType)
        .map(MoreTypes::asTypeElement)
        .collect(toImmutableList());
  }

  /** The values specified in the {@code includes} attribute. */
  @Memoized
  ImmutableList<AnnotationValue> includesAsAnnotationValues() {
    return asAnnotationValues(getAnnotationValue(annotation(), "includes"));
  }

  /**
   * The types specified in the {@code subcomponents} attribute.
   *
   * @throws IllegalArgumentException if any of the values are error types
   */
  @Memoized
  ImmutableList<TypeElement> subcomponents() {
    return subcomponentsAsAnnotationValues().stream()
        .map(MoreAnnotationValues::asType)
        .map(MoreTypes::asTypeElement)
        .collect(toImmutableList());
  }

  /** The values specified in the {@code subcomponents} attribute. */
  @Memoized
  ImmutableList<AnnotationValue> subcomponentsAsAnnotationValues() {
    return asAnnotationValues(getAnnotationValue(annotation(), "subcomponents"));
  }

  /** Returns {@code true} if the argument is a {@code @Module} or {@code @ProducerModule}. */
  static boolean isModuleAnnotation(AnnotationMirror annotation) {
    return MODULE_ANNOTATIONS.stream()
        .map(Class::getCanonicalName)
        .anyMatch(asTypeElement(annotation.getAnnotationType()).getQualifiedName()::contentEquals);
  }

  /** The module annotation types. */
  static ImmutableSet<Class<? extends Annotation>> moduleAnnotations() {
    return MODULE_ANNOTATIONS;
  }

  /**
   * Creates an object that represents a {@code @Module} or {@code @ProducerModule}.
   *
   * @throws IllegalArgumentException if {@link #isModuleAnnotation(AnnotationMirror)} returns
   *     {@code false}
   */
  static ModuleAnnotation moduleAnnotation(AnnotationMirror annotation) {
    checkArgument(
        isModuleAnnotation(annotation),
        "%s is not a Module or ProducerModule annotation",
        annotation);
    return new AutoValue_ModuleAnnotation(annotation);
  }

  /**
   * Returns an object representing the {@code @Module} or {@code @ProducerModule} annotation if one
   * annotates {@code typeElement}.
   */
  static Optional<ModuleAnnotation> moduleAnnotation(TypeElement typeElement) {
    return getAnyAnnotation(typeElement, Module.class, ProducerModule.class)
        .map(ModuleAnnotation::moduleAnnotation);
  }
}

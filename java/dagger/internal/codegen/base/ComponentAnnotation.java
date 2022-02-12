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

package dagger.internal.codegen.base;

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.xprocessing.XAnnotations.getClassName;
import static dagger.internal.codegen.xprocessing.XElements.getAnyAnnotation;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.internal.codegen.javapoet.TypeNames;
import java.util.Collection;
import java.util.Optional;

/**
 * A {@code @Component}, {@code @Subcomponent}, {@code @ProductionComponent}, or
 * {@code @ProductionSubcomponent} annotation, or a {@code @Module} or {@code @ProducerModule}
 * annotation that is being treated as a component annotation when validating full binding graphs
 * for modules.
 */
@AutoValue
public abstract class ComponentAnnotation {
  /** The root component annotation types. */
  private static final ImmutableSet<ClassName> ROOT_COMPONENT_ANNOTATIONS =
      ImmutableSet.of(TypeNames.COMPONENT, TypeNames.PRODUCTION_COMPONENT);

  /** The subcomponent annotation types. */
  private static final ImmutableSet<ClassName> SUBCOMPONENT_ANNOTATIONS =
      ImmutableSet.of(TypeNames.SUBCOMPONENT, TypeNames.PRODUCTION_SUBCOMPONENT);

  /** All component annotation types. */
  private static final ImmutableSet<ClassName> ALL_COMPONENT_ANNOTATIONS =
      ImmutableSet.<ClassName>builder()
          .addAll(ROOT_COMPONENT_ANNOTATIONS)
          .addAll(SUBCOMPONENT_ANNOTATIONS)
          .build();

  /** All component and creator annotation types. */
  private static final ImmutableSet<ClassName> ALL_COMPONENT_AND_CREATOR_ANNOTATIONS =
      ImmutableSet.<ClassName>builder()
          .addAll(ALL_COMPONENT_ANNOTATIONS)
          .addAll(ComponentCreatorAnnotation.allCreatorAnnotations())
          .build();

  /** All production annotation types. */
  private static final ImmutableSet<ClassName> PRODUCTION_ANNOTATIONS =
      ImmutableSet.of(
          TypeNames.PRODUCTION_COMPONENT,
          TypeNames.PRODUCTION_SUBCOMPONENT,
          TypeNames.PRODUCER_MODULE);

  private XAnnotation annotation;

  /** The annotation itself. */
  public final XAnnotation annotation() {
    return annotation;
  }

  /** Returns the {@link ClassName} name of the annotation. */
  public abstract ClassName className();

  /** The simple name of the annotation type. */
  public final String simpleName() {
    return className().simpleName();
  }

  /**
   * Returns {@code true} if the annotation is a {@code @Subcomponent} or
   * {@code @ProductionSubcomponent}.
   */
  public final boolean isSubcomponent() {
    return SUBCOMPONENT_ANNOTATIONS.contains(className());
  }

  /**
   * Returns {@code true} if the annotation is a {@code @ProductionComponent},
   * {@code @ProductionSubcomponent}, or {@code @ProducerModule}.
   */
  public final boolean isProduction() {
    return PRODUCTION_ANNOTATIONS.contains(className());
  }

  /**
   * Returns {@code true} if the annotation is a real component annotation and not a module
   * annotation.
   */
  public final boolean isRealComponent() {
    return ALL_COMPONENT_ANNOTATIONS.contains(className());
  }

  /** The types listed as {@code dependencies}. */
  @Memoized
  public ImmutableList<XType> dependencyTypes() {
    return isRootComponent()
        ? ImmutableList.copyOf(annotation.getAsTypeList("dependencies"))
        : ImmutableList.of();
  }

  /**
   * The types listed as {@code dependencies}.
   *
   * @throws IllegalArgumentException if any of {@link #dependencyTypes()} are error types
   */
  @Memoized
  public ImmutableSet<XTypeElement> dependencies() {
    return dependencyTypes().stream().map(XType::getTypeElement).collect(toImmutableSet());
  }

  /**
   * The types listed as {@code modules}.
   *
   * @throws IllegalArgumentException if any module is an error type.
   */
  @Memoized
  public ImmutableSet<XTypeElement> modules() {
    return annotation.getAsTypeList(isRealComponent() ? "modules" : "includes").stream()
        .map(XType::getTypeElement)
        .collect(toImmutableSet());
  }

  private final boolean isRootComponent() {
    return ROOT_COMPONENT_ANNOTATIONS.contains(className());
  }

  /**
   * Returns an object representing a root component annotation, not a subcomponent annotation, if
   * one is present on {@code typeElement}.
   */
  public static Optional<ComponentAnnotation> rootComponentAnnotation(
      XTypeElement typeElement, DaggerSuperficialValidation superficialValidation) {
    return anyComponentAnnotation(typeElement, ROOT_COMPONENT_ANNOTATIONS, superficialValidation);
  }

  /**
   * Returns an object representing a subcomponent annotation, if one is present on {@code
   * typeElement}.
   */
  public static Optional<ComponentAnnotation> subcomponentAnnotation(
      XTypeElement typeElement, DaggerSuperficialValidation superficialValidation) {
    return anyComponentAnnotation(typeElement, SUBCOMPONENT_ANNOTATIONS, superficialValidation);
  }

  /**
   * Returns an object representing a root component or subcomponent annotation, if one is present
   * on {@code typeElement}.
   */
  public static Optional<ComponentAnnotation> anyComponentAnnotation(
      XElement element, DaggerSuperficialValidation superficialValidation) {
    return anyComponentAnnotation(element, ALL_COMPONENT_ANNOTATIONS, superficialValidation);
  }

  private static Optional<ComponentAnnotation> anyComponentAnnotation(
      XElement element,
      Collection<ClassName> annotations,
      DaggerSuperficialValidation superficialValidation) {
    return getAnyAnnotation(element, annotations)
        .map(
            annotation -> {
              superficialValidation.validateAnnotationOf(element, annotation);
              return create(annotation);
            });
  }

  /** Returns {@code true} if the argument is a component annotation. */
  public static boolean isComponentAnnotation(XAnnotation annotation) {
    return ALL_COMPONENT_ANNOTATIONS.contains(getClassName(annotation));
  }

  /** Creates a fictional component annotation representing a module. */
  public static ComponentAnnotation fromModuleAnnotation(ModuleAnnotation moduleAnnotation) {
    return create(moduleAnnotation.annotation());
  }

  private static ComponentAnnotation create(XAnnotation annotation) {
    ComponentAnnotation componentAnnotation =
        new AutoValue_ComponentAnnotation(getClassName(annotation));
    componentAnnotation.annotation = annotation;
    return componentAnnotation;
  }

  /** The root component annotation types. */
  public static ImmutableSet<ClassName> rootComponentAnnotations() {
    return ROOT_COMPONENT_ANNOTATIONS;
  }

  /** The subcomponent annotation types. */
  public static ImmutableSet<ClassName> subcomponentAnnotations() {
    return SUBCOMPONENT_ANNOTATIONS;
  }

  /** All component annotation types. */
  public static ImmutableSet<ClassName> allComponentAnnotations() {
    return ALL_COMPONENT_ANNOTATIONS;
  }

  /** All component and creator annotation types. */
  public static ImmutableSet<ClassName> allComponentAndCreatorAnnotations() {
    return ALL_COMPONENT_AND_CREATOR_ANNOTATIONS;
  }
}

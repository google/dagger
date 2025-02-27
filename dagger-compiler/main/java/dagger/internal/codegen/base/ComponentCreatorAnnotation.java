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

import static com.google.common.base.Ascii.toUpperCase;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.extension.DaggerStreams.valuesOf;
import static java.util.stream.Collectors.mapping;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.xprocessing.XTypeNames;
import java.util.stream.Collector;
import java.util.stream.Stream;

/** Simple representation of a component creator annotation type. */
public enum ComponentCreatorAnnotation {
  COMPONENT_BUILDER(XTypeNames.COMPONENT_BUILDER),
  COMPONENT_FACTORY(XTypeNames.COMPONENT_FACTORY),
  SUBCOMPONENT_BUILDER(XTypeNames.SUBCOMPONENT_BUILDER),
  SUBCOMPONENT_FACTORY(XTypeNames.SUBCOMPONENT_FACTORY),
  PRODUCTION_COMPONENT_BUILDER(XTypeNames.PRODUCTION_COMPONENT_BUILDER),
  PRODUCTION_COMPONENT_FACTORY(XTypeNames.PRODUCTION_COMPONENT_FACTORY),
  PRODUCTION_SUBCOMPONENT_BUILDER(XTypeNames.PRODUCTION_SUBCOMPONENT_BUILDER),
  PRODUCTION_SUBCOMPONENT_FACTORY(XTypeNames.PRODUCTION_SUBCOMPONENT_FACTORY),
  ;

  @SuppressWarnings("ImmutableEnumChecker")
  private final XClassName annotation;

  private final ComponentCreatorKind creatorKind;

  @SuppressWarnings("ImmutableEnumChecker")
  private final XClassName componentAnnotation;

  ComponentCreatorAnnotation(XClassName annotation) {
    this.annotation = annotation;
    this.creatorKind = ComponentCreatorKind.valueOf(toUpperCase(annotation.getSimpleName()));
    this.componentAnnotation = XTypeNames.enclosingClassName(annotation);
  }

  /** The actual annotation type. */
  public XClassName annotation() {
    return annotation;
  }

  /** The component annotation type that encloses this creator annotation type. */
  public final XClassName componentAnnotation() {
    return componentAnnotation;
  }

  /** Returns {@code true} if the creator annotation is for a subcomponent. */
  public final boolean isSubcomponentCreatorAnnotation() {
    return componentAnnotation().getSimpleName().endsWith("Subcomponent");
  }

  /**
   * Returns {@code true} if the creator annotation is for a production component or subcomponent.
   */
  public final boolean isProductionCreatorAnnotation() {
    return componentAnnotation().getSimpleName().startsWith("Production");
  }

  /** The creator kind the annotation is associated with. */
  // TODO(dpb): Remove ComponentCreatorKind.
  public ComponentCreatorKind creatorKind() {
    return creatorKind;
  }

  @Override
  public final String toString() {
    return annotation().getCanonicalName();
  }

  public final String simpleName() {
    return annotation().getSimpleName();
  }

  /** Returns all component creator annotations. */
  public static ImmutableSet<XClassName> allCreatorAnnotations() {
    return stream().collect(toAnnotationClasses());
  }

  /** Returns all root component creator annotations. */
  public static ImmutableSet<XClassName> rootComponentCreatorAnnotations() {
    return stream()
        .filter(
            componentCreatorAnnotation ->
                !componentCreatorAnnotation.isSubcomponentCreatorAnnotation())
        .collect(toAnnotationClasses());
  }

  /** Returns all subcomponent creator annotations. */
  public static ImmutableSet<XClassName> subcomponentCreatorAnnotations() {
    return stream()
        .filter(
            componentCreatorAnnotation ->
                componentCreatorAnnotation.isSubcomponentCreatorAnnotation())
        .collect(toAnnotationClasses());
  }

  /** Returns all production component creator annotations. */
  public static ImmutableSet<XClassName> productionCreatorAnnotations() {
    return stream()
        .filter(
            componentCreatorAnnotation ->
                componentCreatorAnnotation.isProductionCreatorAnnotation())
        .collect(toAnnotationClasses());
  }

  /** Returns the legal creator annotations for the given {@code componentAnnotation}. */
  public static ImmutableSet<XClassName> creatorAnnotationsFor(
      ComponentAnnotation componentAnnotation) {
    return stream()
        .filter(
            creatorAnnotation ->
                creatorAnnotation.componentAnnotation().getSimpleName()
                    .equals(componentAnnotation.simpleName()))
        .collect(toAnnotationClasses());
  }

  /** Returns all creator annotations present on the given {@code type}. */
  public static ImmutableSet<ComponentCreatorAnnotation> getCreatorAnnotations(XTypeElement type) {
    return stream().filter(cca -> type.hasAnnotation(cca.annotation())).collect(toImmutableSet());
  }

  private static Stream<ComponentCreatorAnnotation> stream() {
    return valuesOf(ComponentCreatorAnnotation.class);
  }

  private static Collector<ComponentCreatorAnnotation, ?, ImmutableSet<XClassName>>
      toAnnotationClasses() {
    return mapping(ComponentCreatorAnnotation::annotation, toImmutableSet());
  }
}

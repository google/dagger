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

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Ascii.toUpperCase;
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.DaggerStreams.valuesOf;
import static java.util.stream.Collectors.mapping;

import com.google.common.collect.ImmutableSet;
import dagger.Component;
import dagger.Subcomponent;
import dagger.producers.ProductionComponent;
import dagger.producers.ProductionSubcomponent;
import java.lang.annotation.Annotation;
import java.util.stream.Collector;
import java.util.stream.Stream;
import javax.lang.model.element.TypeElement;

/** Simple representation of a component creator annotation type. */
enum ComponentCreatorAnnotation {
  COMPONENT_BUILDER(Component.Builder.class),
  COMPONENT_FACTORY(Component.Factory.class),
  SUBCOMPONENT_BUILDER(Subcomponent.Builder.class),
  SUBCOMPONENT_FACTORY(Subcomponent.Factory.class),
  PRODUCTION_COMPONENT_BUILDER(ProductionComponent.Builder.class),
  PRODUCTION_COMPONENT_FACTORY(ProductionComponent.Factory.class),
  PRODUCTION_SUBCOMPONENT_BUILDER(ProductionSubcomponent.Builder.class),
  PRODUCTION_SUBCOMPONENT_FACTORY(ProductionSubcomponent.Factory.class),
  ;

  private final Class<? extends Annotation> annotation;
  private final ComponentCreatorKind creatorKind;
  private final Class<? extends Annotation> componentAnnotation;

  ComponentCreatorAnnotation(Class<? extends Annotation> annotation) {
    this.annotation = annotation;
    this.creatorKind = ComponentCreatorKind.valueOf(toUpperCase(annotation.getSimpleName()));
    this.componentAnnotation = (Class<? extends Annotation>) annotation.getEnclosingClass();
  }

  /** The actual annotation type. */
  Class<? extends Annotation> annotation() {
    return annotation;
  }

  /** The component annotation type that encloses this creator annotation type. */
  final Class<? extends Annotation> componentAnnotation() {
    return componentAnnotation;
  }

  /** Returns {@code true} if the creator annotation is for a subcomponent. */
  final boolean isSubcomponentCreatorAnnotation() {
    return componentAnnotation().getSimpleName().endsWith("Subcomponent");
  }

  /**
   * Returns {@code true} if the creator annotation is for a production component or subcomponent.
   */
  final boolean isProductionCreatorAnnotation() {
    return componentAnnotation().getSimpleName().startsWith("Production");
  }

  /** The creator kind the annotation is associated with. */
  // TODO(dpb): Remove ComponentCreatorKind.
  ComponentCreatorKind creatorKind() {
    return creatorKind;
  }

  @Override
  public final String toString() {
    return annotation().getName();
  }

  /** Returns all component creator annotations. */
  static ImmutableSet<Class<? extends Annotation>> allCreatorAnnotations() {
    return stream().collect(toAnnotationClasses());
  }

  /** Returns all root component creator annotations. */
  static ImmutableSet<Class<? extends Annotation>> rootComponentCreatorAnnotations() {
    return stream()
        .filter(
            componentCreatorAnnotation ->
                !componentCreatorAnnotation.isSubcomponentCreatorAnnotation())
        .collect(toAnnotationClasses());
  }

  /** Returns all subcomponent creator annotations. */
  static ImmutableSet<Class<? extends Annotation>> subcomponentCreatorAnnotations() {
    return stream()
        .filter(
            componentCreatorAnnotation ->
                componentCreatorAnnotation.isSubcomponentCreatorAnnotation())
        .collect(toAnnotationClasses());
  }

  /** Returns all production component creator annotations. */
  static ImmutableSet<Class<? extends Annotation>> productionCreatorAnnotations() {
    return stream()
        .filter(
            componentCreatorAnnotation ->
                componentCreatorAnnotation.isProductionCreatorAnnotation())
        .collect(toAnnotationClasses());
  }

  /** Returns the legal creator annotations for the given {@code componentAnnotation}. */
  static ImmutableSet<Class<? extends Annotation>> creatorAnnotationsFor(
      ComponentAnnotation componentAnnotation) {
    return stream()
        .filter(
            creatorAnnotation ->
                creatorAnnotation
                    .componentAnnotation()
                    .getSimpleName()
                    .equals(componentAnnotation.simpleName()))
        .collect(toAnnotationClasses());
  }

  /** Returns all creator annotations present on the given {@code type}. */
  static ImmutableSet<ComponentCreatorAnnotation> getCreatorAnnotations(TypeElement type) {
    return stream()
        .filter(cca -> isAnnotationPresent(type, cca.annotation()))
        .collect(toImmutableSet());
  }

  private static Stream<ComponentCreatorAnnotation> stream() {
    return valuesOf(ComponentCreatorAnnotation.class);
  }

  private static Collector<ComponentCreatorAnnotation, ?, ImmutableSet<Class<? extends Annotation>>>
      toAnnotationClasses() {
    return mapping(ComponentCreatorAnnotation::annotation, toImmutableSet());
  }
}

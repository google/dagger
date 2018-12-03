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

package dagger.internal.codegen;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.collect.Sets.immutableEnumSet;
import static dagger.internal.codegen.DaggerStreams.presentValues;
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;
import static java.util.Arrays.stream;
import static java.util.EnumSet.allOf;

import com.google.common.collect.ImmutableSet;
import dagger.Component;
import dagger.Module;
import dagger.Subcomponent;
import dagger.producers.ProducerModule;
import dagger.producers.ProductionComponent;
import dagger.producers.ProductionSubcomponent;
import java.lang.annotation.Annotation;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.lang.model.element.TypeElement;

/** Enumeration of the different kinds of components. */
enum ComponentKind {
  /** {@code @Component} */
  COMPONENT(Component.class, Optional.of(Component.Builder.class), true, false),

  /** {@code @Subcomponent} */
  SUBCOMPONENT(Subcomponent.class, Optional.of(Subcomponent.Builder.class), false, false),

  /** {@code @ProductionComponent} */
  PRODUCTION_COMPONENT(
      ProductionComponent.class, Optional.of(ProductionComponent.Builder.class), true, true),

  /** {@code @ProductionSubcomponent} */
  PRODUCTION_SUBCOMPONENT(
      ProductionSubcomponent.class, Optional.of(ProductionSubcomponent.Builder.class), false, true),

  /**
   * Kind for a descriptor that was generated from a {@link Module} instead of a component type in
   * order to validate the module's bindings.
   */
  MODULE(Module.class, Optional.empty(), true, false),

  /**
   * Kind for a descriptor was generated from a {@link ProducerModule} instead of a component type
   * in order to validate the module's bindings.
   */
  PRODUCER_MODULE(ProducerModule.class, Optional.empty(), true, true),
  ;

  private static final ImmutableSet<ComponentKind> TOP_LEVEL_COMPONENT_KINDS =
      stream(values())
          .filter(kind -> !kind.isForModuleValidation())
          .filter(kind -> kind.isTopLevel())
          .collect(toImmutableSet());

  private static final ImmutableSet<ComponentKind> SUBCOMPONENT_KINDS =
      stream(values())
          .filter(kind -> !kind.isForModuleValidation())
          .filter(kind -> !kind.isTopLevel())
          .collect(toImmutableSet());

  /** Returns the set of kinds for top-level components. */
  static ImmutableSet<ComponentKind> topLevelComponentKinds() {
    return TOP_LEVEL_COMPONENT_KINDS;
  }

  /** Returns the set of kinds for subcomponents. */
  static ImmutableSet<ComponentKind> subcomponentKinds() {
    return SUBCOMPONENT_KINDS;
  }

  /** Returns the set of all annotations that mark components and their builders. */
  static ImmutableSet<Class<? extends Annotation>> allComponentAndBuilderAnnotations() {
    return stream(values())
        .filter(kind -> !kind.isForModuleValidation())
        .flatMap(kind -> Stream.of(kind.annotation(), kind.builderAnnotation().get()))
        .collect(toImmutableSet());
  }

  /** Returns the annotations for components of the given kinds. */
  static ImmutableSet<Class<? extends Annotation>> annotationsFor(Set<ComponentKind> kinds) {
    return annotationsFor(kinds, kind -> Optional.of(kind.annotation()));
  }

  private static ImmutableSet<Class<? extends Annotation>> annotationsFor(
      Set<ComponentKind> kinds,
      Function<ComponentKind, Optional<Class<? extends Annotation>>> annotationFunction) {
    return kinds.stream()
        .map(annotationFunction)
        .flatMap(presentValues())
        .collect(toImmutableSet());
  }
  
  /** Returns the annotations for builders for components of the given kinds. */
  static ImmutableSet<Class<? extends Annotation>> builderAnnotationsFor(Set<ComponentKind> kinds) {
    return annotationsFor(kinds, ComponentKind::builderAnnotation);
  }

  /**
   * Returns the kind of an annotated element if it is annotated with one of the {@linkplain
   * #annotation() annotations}.
   *
   * @throws IllegalArgumentException if the element is annotated with more than one of the
   *     annotations
   */
  static Optional<ComponentKind> forAnnotatedElement(TypeElement element) {
    return forAnnotatedElement(element, kind -> Optional.of(kind.annotation()));
  }

  private static Optional<ComponentKind> forAnnotatedElement(
      TypeElement element,
      Function<ComponentKind, Optional<Class<? extends Annotation>>> annotationFunction) {
    Set<ComponentKind> kinds = EnumSet.noneOf(ComponentKind.class);
    for (ComponentKind kind : values()) {
      if (annotationFunction
          .apply(kind)
          .filter(annotation -> isAnnotationPresent(element, annotation))
          .isPresent()) {
        kinds.add(kind);
      }
    }

    if (kinds.size() > 1) {
      throw new IllegalArgumentException(
          element
              + " cannot be annotated with more than one of "
              + annotationsFor(kinds, annotationFunction));
    }
    return kinds.stream().findAny();
  }
  
  /**
   * Returns the kind of an annotated element if it is annotated with one of the {@linkplain
   * #builderAnnotation() builder annotations}.
   *
   * @throws IllegalArgumentException if the element is annotated with more than one of the builder
   *     annotations
   */
  static Optional<ComponentKind> forAnnotatedBuilderElement(TypeElement element) {
    return forAnnotatedElement(element, ComponentKind::builderAnnotation);
  }

  private final Class<? extends Annotation> annotation;
  private final Optional<Class<? extends Annotation>> builderAnnotation;
  private final boolean topLevel;
  private final boolean production;

  ComponentKind(
      Class<? extends Annotation> annotation,
      Optional<Class<? extends Annotation>> builderAnnotation,
      boolean topLevel,
      boolean production) {
    this.annotation = annotation;
    this.builderAnnotation = builderAnnotation;
    this.topLevel = topLevel;
    this.production = production;
  }

  /** Returns the annotation that marks a component of this kind. */
  Class<? extends Annotation> annotation() {
    return annotation;
  }

  /**
   * Returns the {@code @Builder} annotation type for this kind of component, or empty if the
   * descriptor is {@linkplain #isForModuleValidation() for a module} in order to validate its
   * bindings.
   */
  Optional<Class<? extends Annotation>> builderAnnotation() {
    return builderAnnotation;
  }

  /** Returns the kinds of modules that can be used with a component of this kind. */
  ImmutableSet<ModuleKind> legalModuleKinds() {
    return isProducer()
        ? immutableEnumSet(allOf(ModuleKind.class))
        : immutableEnumSet(ModuleKind.MODULE);
  }

  /** Returns the kinds of subcomponents a component of this kind can have. */
  ImmutableSet<ComponentKind> legalSubcomponentKinds() {
    return isProducer()
        ? immutableEnumSet(PRODUCTION_SUBCOMPONENT)
        : immutableEnumSet(SUBCOMPONENT, PRODUCTION_SUBCOMPONENT);
  }

  /**
   * Returns {@code true} if the descriptor is for a top-level (not a child) component or is for
   * {@linkplain #isForModuleValidation() module-validation}.
   */
  boolean isTopLevel() {
    return topLevel;
  }

  /** Returns true if this is a production component. */
  boolean isProducer() {
    return production;
  }

  /** Returns {@code true} if the descriptor is for a module in order to validate its bindings. */
  boolean isForModuleValidation() {
    switch (this) {
      case MODULE:
      case PRODUCER_MODULE:
        return true;
      default:
        // fall through
    }
    return false;
  }
}

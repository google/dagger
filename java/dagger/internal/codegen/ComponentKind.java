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
import static dagger.internal.codegen.DaggerStreams.stream;
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.DaggerStreams.valuesOf;
import static java.util.EnumSet.allOf;

import com.google.common.collect.ImmutableSet;
import dagger.Component;
import dagger.Module;
import dagger.Subcomponent;
import dagger.producers.ProducerModule;
import dagger.producers.ProductionComponent;
import dagger.producers.ProductionSubcomponent;
import java.lang.annotation.Annotation;
import java.util.Optional;
import javax.lang.model.element.TypeElement;

/** Enumeration of the different kinds of components. */
enum ComponentKind {
  /** {@code @Component} */
  COMPONENT(Component.class, true, false),

  /** {@code @Subcomponent} */
  SUBCOMPONENT(Subcomponent.class, false, false),

  /** {@code @ProductionComponent} */
  PRODUCTION_COMPONENT(ProductionComponent.class, true, true),

  /** {@code @ProductionSubcomponent} */
  PRODUCTION_SUBCOMPONENT(ProductionSubcomponent.class, false, true),

  /**
   * Kind for a descriptor that was generated from a {@link Module} instead of a component type in
   * order to validate the module's bindings.
   */
  MODULE(Module.class, true, false),

  /**
   * Kind for a descriptor was generated from a {@link ProducerModule} instead of a component type
   * in order to validate the module's bindings.
   */
  PRODUCER_MODULE(ProducerModule.class, true, true),
  ;

  private static final ImmutableSet<ComponentKind> ROOT_COMPONENT_KINDS =
      valuesOf(ComponentKind.class)
          .filter(kind -> !kind.isForModuleValidation())
          .filter(kind -> kind.isRoot())
          .collect(toImmutableSet());

  private static final ImmutableSet<ComponentKind> SUBCOMPONENT_KINDS =
      valuesOf(ComponentKind.class)
          .filter(kind -> !kind.isForModuleValidation())
          .filter(kind -> !kind.isRoot())
          .collect(toImmutableSet());

  /** Returns the set of kinds for root components. */
  static ImmutableSet<ComponentKind> rootComponentKinds() {
    return ROOT_COMPONENT_KINDS;
  }

  /** Returns the set of kinds for subcomponents. */
  static ImmutableSet<ComponentKind> subcomponentKinds() {
    return SUBCOMPONENT_KINDS;
  }

  /** Returns the annotations for components of the given kinds. */
  static ImmutableSet<Class<? extends Annotation>> annotationsFor(Iterable<ComponentKind> kinds) {
    return stream(kinds).map(ComponentKind::annotation).collect(toImmutableSet());
  }

  /** Returns the set of component kinds the given {@code element} has annotations for. */
  static ImmutableSet<ComponentKind> getComponentKinds(TypeElement element) {
    return valuesOf(ComponentKind.class)
        .filter(kind -> isAnnotationPresent(element, kind.annotation()))
        .collect(toImmutableSet());
  }

  /**
   * Returns the kind of an annotated element if it is annotated with one of the {@linkplain
   * #annotation() annotations}.
   *
   * @throws IllegalArgumentException if the element is annotated with more than one of the
   *     annotations
   */
  static Optional<ComponentKind> forAnnotatedElement(TypeElement element) {
    ImmutableSet<ComponentKind> kinds = getComponentKinds(element);
    if (kinds.size() > 1) {
      throw new IllegalArgumentException(
          element + " cannot be annotated with more than one of " + annotationsFor(kinds));
    }
    return kinds.stream().findAny();
  }

  private final Class<? extends Annotation> annotation;
  private final boolean isRoot;
  private final boolean production;

  ComponentKind(
      Class<? extends Annotation> annotation,
      boolean isRoot,
      boolean production) {
    this.annotation = annotation;
    this.isRoot = isRoot;
    this.production = production;
  }

  /** Returns the annotation that marks a component of this kind. */
  Class<? extends Annotation> annotation() {
    return annotation;
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
   * Returns {@code true} if the descriptor is for a root component (not a subcomponent) or is for
   * {@linkplain #isForModuleValidation() module-validation}.
   */
  boolean isRoot() {
    return isRoot;
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

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
import static dagger.internal.codegen.ComponentCreatorKind.BUILDER;
import static dagger.internal.codegen.ComponentCreatorKind.FACTORY;
import static dagger.internal.codegen.ComponentKind.COMPONENT;
import static dagger.internal.codegen.ComponentKind.PRODUCTION_COMPONENT;
import static dagger.internal.codegen.ComponentKind.PRODUCTION_SUBCOMPONENT;
import static dagger.internal.codegen.ComponentKind.SUBCOMPONENT;
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import dagger.Component;
import dagger.Subcomponent;
import dagger.producers.ProductionComponent;
import dagger.producers.ProductionSubcomponent;
import java.lang.annotation.Annotation;
import java.util.Set;
import javax.lang.model.element.TypeElement;

/**
 * Simple representation of an annotation for a component creator type. Each annotation is for a
 * specific component kind and creator kind.
 */
@AutoValue
abstract class ComponentCreatorAnnotation {

  private static final ImmutableMap<Class<? extends Annotation>, ComponentCreatorAnnotation>
      ANNOTATIONS =
          Maps.uniqueIndex(
              ImmutableList.of(
                  create(Component.Builder.class, COMPONENT, BUILDER),
                  create(Component.Factory.class, COMPONENT, FACTORY),
                  create(Subcomponent.Builder.class, SUBCOMPONENT, BUILDER),
                  create(Subcomponent.Factory.class, SUBCOMPONENT, FACTORY),
                  create(ProductionComponent.Builder.class, PRODUCTION_COMPONENT, BUILDER),
                  create(ProductionComponent.Factory.class, PRODUCTION_COMPONENT, FACTORY),
                  create(ProductionSubcomponent.Builder.class, PRODUCTION_SUBCOMPONENT, BUILDER),
                  create(ProductionSubcomponent.Factory.class, PRODUCTION_SUBCOMPONENT, FACTORY)),
              ComponentCreatorAnnotation::annotation);

  /** Returns the set of all component creator annotations. */
  static ImmutableSet<Class<? extends Annotation>> allCreatorAnnotations() {
    return ANNOTATIONS.keySet();
  }

  /** Returns all creator annotations for the given {@code componentKind}. */
  static ImmutableSet<Class<? extends Annotation>> creatorAnnotationsFor(
      ComponentKind componentKind) {
    return creatorAnnotationsFor(ImmutableSet.of(componentKind));
  }

  /** Returns all creator annotations for any of the given {@code componentKinds}. */
  static ImmutableSet<Class<? extends Annotation>> creatorAnnotationsFor(
      Set<ComponentKind> componentKinds) {
    return ANNOTATIONS.values().stream()
        .filter(annotation -> componentKinds.contains(annotation.componentKind()))
        .map(ComponentCreatorAnnotation::annotation)
        .collect(toImmutableSet());
  }

  /** Returns the legal creator annotations for the given {@code componentAnnotation}. */
  static ImmutableSet<Class<? extends Annotation>> creatorAnnotationsFor(
      ComponentAnnotation componentAnnotation) {
    return ANNOTATIONS.values().stream()
        .filter(
            creatorAnnotation ->
                creatorAnnotation
                    .componentAnnotation()
                    .getSimpleName()
                    .equals(componentAnnotation.simpleName()))
        .map(ComponentCreatorAnnotation::annotation)
        .collect(toImmutableSet());
  }

  /** Returns all creator annotations present on the given {@code type}. */
  static ImmutableSet<ComponentCreatorAnnotation> getCreatorAnnotations(TypeElement type) {
    return ImmutableSet.copyOf(
        Maps.filterKeys(ANNOTATIONS, annotation -> isAnnotationPresent(type, annotation)).values());
  }

  /** The actual annotation. */
  abstract Class<? extends Annotation> annotation();

  /** The component annotation type that encloses this creator annotation type. */
  final Class<? extends Annotation> componentAnnotation() {
    return (Class<? extends Annotation>) annotation().getEnclosingClass();
  }

  /** The component kind the annotation is associated with. */
  abstract ComponentKind componentKind();

  /** The creator kind the annotation is associated with. */
  abstract ComponentCreatorKind creatorKind();

  @Override
  public final String toString() {
    return annotation().getName();
  }

  private static ComponentCreatorAnnotation create(
      Class<? extends Annotation> annotation,
      ComponentKind componentKind,
      ComponentCreatorKind componentCreatorKind) {
    return new AutoValue_ComponentCreatorAnnotation(
        annotation, componentKind, componentCreatorKind);
  }
}

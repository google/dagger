/*
 * Copyright (C) 2015 The Dagger Authors.
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

import static com.google.common.base.Preconditions.checkArgument;
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.langmodel.DaggerElements.getAnnotationMirror;

import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import dagger.Module;
import dagger.producers.ProducerModule;
import java.lang.annotation.Annotation;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;

/** Enumeration of the kinds of modules. */
enum ModuleKind {
  /** {@code @Module} */
  MODULE(Module.class),

  /** {@code @ProducerModule} */
  PRODUCER_MODULE(ProducerModule.class);

  /** Returns the annotations for modules of the given kinds. */
  static ImmutableSet<Class<? extends Annotation>> annotationsFor(Set<ModuleKind> kinds) {
    return kinds.stream().map(ModuleKind::annotation).collect(toImmutableSet());
  }

  /**
   * Returns the kind of an annotated element if it is annotated with one of the module {@linkplain
   * #annotation() annotations}.
   *
   * @throws IllegalArgumentException if the element is annotated with more than one of the module
   *     annotations
   */
  static Optional<ModuleKind> forAnnotatedElement(TypeElement element) {
    Set<ModuleKind> kinds = EnumSet.noneOf(ModuleKind.class);
    for (ModuleKind kind : values()) {
      if (MoreElements.isAnnotationPresent(element, kind.annotation())) {
        kinds.add(kind);
      }
    }

    if (kinds.size() > 1) {
      throw new IllegalArgumentException(
          element + " cannot be annotated with more than one of " + annotationsFor(kinds));
    }
    return kinds.stream().findAny();
  }

  static void checkIsModule(TypeElement moduleElement) {
    checkArgument(forAnnotatedElement(moduleElement).isPresent());
  }

  private final Class<? extends Annotation> moduleAnnotation;

  ModuleKind(Class<? extends Annotation> moduleAnnotation) {
    this.moduleAnnotation = moduleAnnotation;
  }

  /**
   * Returns the annotation mirror for this module kind on the given type.
   *
   * @throws IllegalArgumentException if the annotation is not present on the type
   */
  AnnotationMirror getModuleAnnotation(TypeElement element) {
    Optional<AnnotationMirror> result = getAnnotationMirror(element, moduleAnnotation);
    checkArgument(
        result.isPresent(), "annotation %s is not present on type %s", moduleAnnotation, element);
    return result.get();
  }

  /** Returns the annotation that marks a module of this kind. */
  Class<? extends Annotation> annotation() {
    return moduleAnnotation;
  }

  /** Returns the kinds of modules that a module of this kind is allowed to include. */
  ImmutableSet<ModuleKind> legalIncludedModuleKinds() {
    switch (this) {
      case MODULE:
        return Sets.immutableEnumSet(MODULE);
      case PRODUCER_MODULE:
        return Sets.immutableEnumSet(MODULE, PRODUCER_MODULE);
    }
    throw new AssertionError(this);
  }
}

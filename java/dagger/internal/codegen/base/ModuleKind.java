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

package dagger.internal.codegen.base;

import static com.google.common.base.Preconditions.checkArgument;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.squareup.javapoet.ClassName;
import dagger.internal.codegen.javapoet.TypeNames;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/** Enumeration of the kinds of modules. */
public enum ModuleKind {
  /** {@code @Module} */
  MODULE(TypeNames.MODULE),

  /** {@code @ProducerModule} */
  PRODUCER_MODULE(TypeNames.PRODUCER_MODULE);

  /** Returns the annotations for modules of the given kinds. */
  private static ImmutableSet<ClassName> annotationsFor(Set<ModuleKind> kinds) {
    return kinds.stream().map(ModuleKind::annotation).collect(toImmutableSet());
  }

  /**
   * Returns the kind of an annotated element if it is annotated with one of the module {@linkplain
   * #annotation() annotations}.
   *
   * @throws IllegalArgumentException if the element is annotated with more than one of the module
   *     annotations
   */
  public static Optional<ModuleKind> forAnnotatedElement(XTypeElement element) {
    Set<ModuleKind> kinds = EnumSet.noneOf(ModuleKind.class);
    for (ModuleKind kind : values()) {
      if (element.hasAnnotation(kind.annotation())) {
        kinds.add(kind);
      }
    }

    if (kinds.size() > 1) {
      throw new IllegalArgumentException(
          element + " cannot be annotated with more than one of " + annotationsFor(kinds));
    }
    return kinds.stream().findAny();
  }

  public static void checkIsModule(XTypeElement moduleElement) {
    // If the type element is a Kotlin companion object, then assert it is a module if its enclosing
    // type is a module.
    if (moduleElement.isCompanionObject()) {
      checkArgument(forAnnotatedElement(moduleElement.getEnclosingTypeElement()).isPresent());
    } else {
      checkArgument(forAnnotatedElement(moduleElement).isPresent());
    }
  }

  private final ClassName moduleAnnotation;

  ModuleKind(ClassName moduleAnnotation) {
    this.moduleAnnotation = moduleAnnotation;
  }

  /**
   * Returns the annotation mirror for this module kind on the given type.
   *
   * @throws IllegalArgumentException if the annotation is not present on the type
   */
  public XAnnotation getModuleAnnotation(XTypeElement element) {
    checkArgument(
        element.hasAnnotation(moduleAnnotation),
        "annotation %s is not present on type %s",
        moduleAnnotation,
        element);
    return element.getAnnotation(moduleAnnotation);
  }

  /** Returns the annotation that marks a module of this kind. */
  public ClassName annotation() {
    return moduleAnnotation;
  }

  /** Returns the kinds of modules that a module of this kind is allowed to include. */
  public ImmutableSet<ModuleKind> legalIncludedModuleKinds() {
    switch (this) {
      case MODULE:
        return Sets.immutableEnumSet(MODULE);
      case PRODUCER_MODULE:
        return Sets.immutableEnumSet(MODULE, PRODUCER_MODULE);
    }
    throw new AssertionError(this);
  }
}

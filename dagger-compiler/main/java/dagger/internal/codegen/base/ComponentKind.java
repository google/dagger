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

package dagger.internal.codegen.base;

import static com.google.common.collect.Sets.immutableEnumSet;
import static dagger.internal.codegen.extension.DaggerStreams.stream;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.extension.DaggerStreams.valuesOf;
import static java.util.EnumSet.allOf;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.xprocessing.XTypeNames;
import java.util.Optional;

/** Enumeration of the different kinds of components. */
public enum ComponentKind {
  COMPONENT(XTypeNames.COMPONENT),
  SUBCOMPONENT(XTypeNames.SUBCOMPONENT),
  PRODUCTION_COMPONENT(XTypeNames.PRODUCTION_COMPONENT),
  PRODUCTION_SUBCOMPONENT(XTypeNames.PRODUCTION_SUBCOMPONENT),
  MODULE(XTypeNames.MODULE),
  PRODUCER_MODULE(XTypeNames.PRODUCER_MODULE);

  private static final ImmutableSet<ComponentKind> PRODUCER_KINDS =
      ImmutableSet.of(PRODUCTION_COMPONENT, PRODUCTION_SUBCOMPONENT, PRODUCER_MODULE);

  /** Returns the annotations for components of the given kinds. */
  public static ImmutableSet<XClassName> annotationsFor(Iterable<ComponentKind> kinds) {
    return stream(kinds).map(ComponentKind::annotation).collect(toImmutableSet());
  }

  /** Returns the set of component kinds the given {@code element} has annotations for. */
  public static ImmutableSet<ComponentKind> getComponentKinds(XTypeElement element) {
    return valuesOf(ComponentKind.class)
        .filter(kind -> element.hasAnnotation(kind.annotation()))
        .collect(toImmutableSet());
  }

  /**
   * Returns the kind of an annotated element if it is annotated with one of the {@linkplain
   * #annotation() annotations}.
   *
   * @throws IllegalArgumentException if the element is annotated with more than one of the
   *     annotations
   */
  public static Optional<ComponentKind> forAnnotatedElement(XTypeElement element) {
    ImmutableSet<ComponentKind> kinds = getComponentKinds(element);
    if (kinds.size() > 1) {
      throw new IllegalArgumentException(
          element + " cannot be annotated with more than one of " + annotationsFor(kinds));
    }
    return kinds.stream().findAny();
  }

  @SuppressWarnings("ImmutableEnumChecker")
  private final XClassName annotation;

  ComponentKind(XClassName annotation) {
    this.annotation = annotation;
  }

  /** Returns the annotation that marks a component of this kind. */
  public XClassName annotation() {
    return annotation;
  }

  /** Returns the kinds of modules that can be used with a component of this kind. */
  public ImmutableSet<ModuleKind> legalModuleKinds() {
    return isProducer()
        ? immutableEnumSet(allOf(ModuleKind.class))
        : immutableEnumSet(ModuleKind.MODULE);
  }

  /** Returns the kinds of subcomponents a component of this kind can have. */
  public ImmutableSet<ComponentKind> legalSubcomponentKinds() {
    return isProducer()
        ? immutableEnumSet(PRODUCTION_SUBCOMPONENT)
        : immutableEnumSet(SUBCOMPONENT, PRODUCTION_SUBCOMPONENT);
  }

  /** Returns true if this is a production component. */
  public boolean isProducer() {
    return PRODUCER_KINDS.contains(this);
  }
}

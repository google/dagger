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

package dagger.hilt.processor.internal.definecomponent;

import static com.google.auto.common.AnnotationMirrors.getAnnotationElementAndValue;
import static com.google.auto.common.MoreElements.asType;
import static com.google.auto.common.MoreTypes.asTypeElement;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.AnnotationValues;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.hilt.processor.internal.Processors;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;

/** Metadata for types annotated with {@link dagger.hilt.DefineComponent}. */
@AutoValue
abstract class DefineComponentMetadata {
  private static final Map<Element, DefineComponentMetadata> processed = new HashMap<>();

  /** Returns the metadata for an element annotated with {@link dagger.hilt.DefineComponent}. */
  static DefineComponentMetadata from(Element element) {
    return from(element, new LinkedHashSet<>());
  }

  private static DefineComponentMetadata from(Element element, LinkedHashSet<Element> childPath) {
    if (!processed.containsKey(element)) {
      processed.put(element, fromUncached(element, childPath));
    }
    return processed.get(element);
  }

  private static DefineComponentMetadata fromUncached(
      Element element, LinkedHashSet<Element> childPath) {
    ProcessorErrors.checkState(
        childPath.add(element),
        element,
        "@DefineComponent cycle: %s -> %s",
        childPath.stream().map(Object::toString).collect(Collectors.joining(" -> ")),
        element);

    ProcessorErrors.checkState(
        Processors.hasAnnotation(element, ClassNames.DEFINE_COMPONENT),
        element,
        "%s, expected to be annotated with @DefineComponent. Found: %s",
        element,
        element.getAnnotationMirrors());

    // TODO(user): Allow abstract classes?
    ProcessorErrors.checkState(
        element.getKind().equals(ElementKind.INTERFACE),
        element,
        "@DefineComponent is only allowed on interfaces. Found: %s",
        element);
    TypeElement component = asType(element);

    // TODO(user): Allow extending interfaces?
    ProcessorErrors.checkState(
        component.getInterfaces().isEmpty(),
        component,
        "@DefineComponent %s, cannot extend a super class or interface. Found: %s",
        component,
        component.getInterfaces());

    // TODO(user): Allow type parameters?
    ProcessorErrors.checkState(
        component.getTypeParameters().isEmpty(),
        component,
        "@DefineComponent %s, cannot have type parameters.",
        component.asType());

    // TODO(user): Allow non-static abstract methods (aka EntryPoints)?
    List<ExecutableElement> nonStaticMethods =
        ElementFilter.methodsIn(component.getEnclosedElements()).stream()
            .filter(method -> !method.getModifiers().contains(STATIC))
            .collect(Collectors.toList());
    ProcessorErrors.checkState(
        nonStaticMethods.isEmpty(),
        component,
        "@DefineComponent %s, cannot have non-static methods. Found: %s",
        component,
        nonStaticMethods);

    // No need to check non-static fields since interfaces can't have them.

    ImmutableList<TypeElement> scopes =
        Processors.getScopeAnnotations(component).stream()
            .map(AnnotationMirror::getAnnotationType)
            .map(MoreTypes::asTypeElement)
            .collect(toImmutableList());

    ImmutableList<AnnotationMirror> aliasScopes =
        Processors.getAnnotationsAnnotatedWith(component, ClassNames.ALIAS_OF);
    ProcessorErrors.checkState(
        aliasScopes.isEmpty(),
        component,
        "@DefineComponent %s, references invalid scope(s) annotated with @AliasOf. "
            + "@DefineComponent scopes cannot be aliases of other scopes: %s",
        component,
        aliasScopes);

    AnnotationMirror mirror =
        Processors.getAnnotationMirror(component, ClassNames.DEFINE_COMPONENT);
    AnnotationValue parentValue = getAnnotationElementAndValue(mirror, "parent").getValue();

    ProcessorErrors.checkState(
        // TODO(user): Contribute a check to auto/common AnnotationValues.
        !"<error>".contentEquals(parentValue.getValue().toString()),
        component,
        "@DefineComponent %s, references an invalid type: %s",
        component,
        mirror);

    TypeElement parent = asTypeElement(AnnotationValues.getTypeMirror(parentValue));
    Optional<DefineComponentMetadata> parentComponent =
        ClassName.get(parent).equals(ClassNames.DEFINE_COMPONENT_NO_PARENT)
            ? Optional.empty()
            : Optional.of(DefineComponentMetadata.from(parent, childPath));

    return new AutoValue_DefineComponentMetadata(component, scopes, parentComponent);
  }

  /** Returns the component annotated with {@link dagger.hilt.DefineComponent}. */
  abstract TypeElement component();

  /** Returns the scopes of the component. */
  abstract ImmutableList<TypeElement> scopes();

  /** Returns the parent component, if one exists. */
  abstract Optional<DefineComponentMetadata> parentMetadata();

  boolean isRoot() {
    return !parentMetadata().isPresent();
  }

}

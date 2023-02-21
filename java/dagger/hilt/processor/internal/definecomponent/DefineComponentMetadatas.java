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

import static androidx.room.compiler.processing.XElementKt.isTypeElement;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.xprocessing.XElements.asTypeElement;
import static java.util.stream.Collectors.joining;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XAnnotationValue;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XExecutableElement;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.hilt.processor.internal.Processors;
import dagger.internal.codegen.xprocessing.XAnnotations;
import dagger.internal.codegen.xprocessing.XElements;
import dagger.internal.codegen.xprocessing.XTypes;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;

/** Metadata for types annotated with {@link dagger.hilt.DefineComponent}. */
final class DefineComponentMetadatas {
  static DefineComponentMetadatas create() {
    return new DefineComponentMetadatas();
  }

  private final Map<XElement, DefineComponentMetadata> metadatas = new HashMap<>();

  private DefineComponentMetadatas() {}

  /** Returns the metadata for an element annotated with {@link dagger.hilt.DefineComponent}. */
  DefineComponentMetadata get(XElement element) {
    return get(element, new LinkedHashSet<>());
  }

  private DefineComponentMetadata get(XElement element, LinkedHashSet<XElement> childPath) {
    if (!metadatas.containsKey(element)) {
      metadatas.put(element, getUncached(element, childPath));
    }
    return metadatas.get(element);
  }

  private DefineComponentMetadata getUncached(
      XElement element, LinkedHashSet<XElement> childPath) {
    ProcessorErrors.checkState(
        childPath.add(element),
        element,
        "@DefineComponent cycle: %s -> %s",
        childPath.stream().map(XElements::toStableString).collect(joining(" -> ")),
        XElements.toStableString(element));

    ProcessorErrors.checkState(
        element.hasAnnotation(ClassNames.DEFINE_COMPONENT),
        element,
        "%s, expected to be annotated with @DefineComponent. Found: %s",
        XElements.toStableString(element),
        element.getAllAnnotations().stream()
            .map(XAnnotations::toStableString)
            .collect(toImmutableList()));

    // TODO(bcorso): Allow abstract classes?
    ProcessorErrors.checkState(
        isTypeElement(element) && asTypeElement(element).isInterface(),
        element,
        "@DefineComponent is only allowed on interfaces. Found: %s",
        XElements.toStableString(element));
    XTypeElement component = asTypeElement(element);

    // TODO(bcorso): Allow extending interfaces?
    ProcessorErrors.checkState(
        component.getSuperInterfaces().isEmpty(),
        component,
        "@DefineComponent %s, cannot extend a super class or interface. Found: %s",
        XElements.toStableString(component),
        component.getSuperInterfaces().stream()
            .map(XTypes::toStableString)
            .collect(toImmutableList()));

    // TODO(bcorso): Allow type parameters?
    ProcessorErrors.checkState(
        component.getTypeParameters().isEmpty(),
        component,
        "@DefineComponent %s, cannot have type parameters.",
        XTypes.toStableString(component.getType()));

    // TODO(bcorso): Allow non-static abstract methods (aka EntryPoints)?
    ImmutableList<XExecutableElement> nonStaticMethods =
        component.getDeclaredMethods().stream()
            .filter(method -> !method.isStatic())
            .collect(toImmutableList());

    ProcessorErrors.checkState(
        nonStaticMethods.isEmpty(),
        component,
        "@DefineComponent %s, cannot have non-static methods. Found: %s",
        XElements.toStableString(component),
        nonStaticMethods.stream()
            .map(XElements::toStableString)
            .collect(toImmutableList()));

    // No need to check non-static fields since interfaces can't have them.

    ImmutableList<XTypeElement> scopes =
        Processors.getScopeAnnotations(component).stream()
            .map(XAnnotation::getTypeElement)
            .collect(toImmutableList());

    ImmutableList<XAnnotation> aliasScopes =
        ImmutableList.copyOf(component.getAnnotationsAnnotatedWith(ClassNames.ALIAS_OF));
    ProcessorErrors.checkState(
        aliasScopes.isEmpty(),
        component,
        "@DefineComponent %s, references invalid scope(s) annotated with @AliasOf. "
            + "@DefineComponent scopes cannot be aliases of other scopes: %s",
        XElements.toStableString(component),
        aliasScopes.stream().map(XAnnotations::toStableString).collect(toImmutableList()));

    XAnnotation annotation = component.getAnnotation(ClassNames.DEFINE_COMPONENT);
    XAnnotationValue parentValue = annotation.getAnnotationValue("parent");

    ProcessorErrors.checkState(
        !"<error>".contentEquals(parentValue.getValue().toString()),
        component,
        "@DefineComponent %s, references an invalid parent type: %s",
        XElements.toStableString(component),
        XAnnotations.toStableString(annotation));

    XTypeElement parent = parentValue.asType().getTypeElement();

    ProcessorErrors.checkState(
        parent.getClassName().equals(ClassNames.DEFINE_COMPONENT_NO_PARENT)
            || parent.hasAnnotation(ClassNames.DEFINE_COMPONENT),
        component,
        "@DefineComponent %s, references a type not annotated with @DefineComponent: %s",
        XElements.toStableString(component),
        XElements.toStableString(parent));

    Optional<DefineComponentMetadata> parentComponent =
        parent.getClassName().equals(ClassNames.DEFINE_COMPONENT_NO_PARENT)
            ? Optional.empty()
            : Optional.of(get(parent, childPath));

    ClassName componentClassName = component.getClassName();

    ProcessorErrors.checkState(
        parentComponent.isPresent()
            || componentClassName.equals(ClassNames.SINGLETON_COMPONENT),
        component,
        "@DefineComponent %s is missing a parent declaration.\n"
            + "Please declare the parent, for example: @DefineComponent(parent ="
            + " SingletonComponent.class)",
        XElements.toStableString(component));

    ProcessorErrors.checkState(
        componentClassName.equals(ClassNames.SINGLETON_COMPONENT)
        || !componentClassName.simpleName().equals(ClassNames.SINGLETON_COMPONENT.simpleName()),
        component,
        "Cannot have a component with the same simple name as the reserved %s: %s",
        ClassNames.SINGLETON_COMPONENT.simpleName(),
        componentClassName.canonicalName());

    return new AutoValue_DefineComponentMetadatas_DefineComponentMetadata(
        component, scopes, parentComponent);
  }

  @AutoValue
  abstract static class DefineComponentMetadata {

    /** Returns the component annotated with {@link dagger.hilt.DefineComponent}. */
    abstract XTypeElement component();

    /** Returns the scopes of the component. */
    abstract ImmutableList<XTypeElement> scopes();

    /** Returns the parent component, if one exists. */
    abstract Optional<DefineComponentMetadata> parentMetadata();

    boolean isRoot() {
      return !parentMetadata().isPresent();
    }
  }
}

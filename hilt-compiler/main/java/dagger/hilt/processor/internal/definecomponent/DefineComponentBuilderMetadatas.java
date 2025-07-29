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
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XFieldElement;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.hilt.processor.internal.definecomponent.DefineComponentMetadatas.DefineComponentMetadata;
import dagger.internal.codegen.xprocessing.XAnnotations;
import dagger.internal.codegen.xprocessing.XElements;
import dagger.internal.codegen.xprocessing.XTypes;
import java.util.HashMap;
import java.util.Map;

/** Metadata for types annotated with {@link dagger.hilt.DefineComponent.Builder}. */
public final class DefineComponentBuilderMetadatas {
  static DefineComponentBuilderMetadatas create(DefineComponentMetadatas componentMetadatas) {
    return new DefineComponentBuilderMetadatas(componentMetadatas);
  }

  private final Map<XElement, DefineComponentBuilderMetadata> builderMetadatas = new HashMap<>();
  private final DefineComponentMetadatas componentMetadatas;

  private DefineComponentBuilderMetadatas(DefineComponentMetadatas componentMetadatas) {
    this.componentMetadatas = componentMetadatas;
  }

  DefineComponentBuilderMetadata get(XElement element) {
    if (!builderMetadatas.containsKey(element)) {
      builderMetadatas.put(element, getUncached(element));
    }
    return builderMetadatas.get(element);
  }

  /**
   * Returns the component built by a type annotated by DefineComponent.Builder.
   * This method does not do validation beyond what is necessary to get the component this
   * builder is for. It is assumed that the validation will be done as part of processing
   * the DefineComponent.Builder type itself.
   */
  public static XType getComponentTypeFromBuilder(XTypeElement builder) {
    ProcessorErrors.checkState(
        builder.hasAnnotation(ClassNames.DEFINE_COMPONENT_BUILDER),
        builder,
        "%s, expected to be annotated with @DefineComponent.Builder. Found: %s",
        XElements.toStableString(builder),
        builder.getAllAnnotations().stream()
            .map(XAnnotations::toStableString)
            .collect(toImmutableList()));
    return getComponentType(builder, getBuildMethod(builder));
  }

  private DefineComponentBuilderMetadata getUncached(XElement element) {
    ProcessorErrors.checkState(
        element.hasAnnotation(ClassNames.DEFINE_COMPONENT_BUILDER),
        element,
        "%s, expected to be annotated with @DefineComponent.Builder. Found: %s",
        XElements.toStableString(element),
        element.getAllAnnotations().stream()
            .map(XAnnotations::toStableString)
            .collect(toImmutableList()));

    // TODO(bcorso): Allow abstract classes?
    ProcessorErrors.checkState(
        isTypeElement(element) && asTypeElement(element).isInterface(),
        element,
        "@DefineComponent.Builder is only allowed on interfaces. Found: %s",
        XElements.toStableString(element));
    XTypeElement builder = asTypeElement(element);

    // TODO(bcorso): Allow extending interfaces?
    ProcessorErrors.checkState(
        builder.getSuperInterfaces().isEmpty(),
        builder,
        "@DefineComponent.Builder %s, cannot extend a super class or interface. Found: %s",
        XElements.toStableString(builder),
        builder.getSuperInterfaces().stream()
            .map(XTypes::toStableString)
            .collect(toImmutableList()));

    // TODO(bcorso): Allow type parameters?
    ProcessorErrors.checkState(
        builder.getTypeParameters().isEmpty(),
        builder,
        "@DefineComponent.Builder %s, cannot have type parameters.",
        XTypes.toStableString(builder.getType()));

    ImmutableList<XFieldElement> nonStaticFields =
        builder.getDeclaredFields().stream()
            .filter(field -> !field.isStatic())
            .collect(toImmutableList());

    ProcessorErrors.checkState(
        nonStaticFields.isEmpty(),
        builder,
        "@DefineComponent.Builder %s, cannot have non-static fields. Found: %s",
        XElements.toStableString(builder),
        nonStaticFields.stream()
            .map(XElements::toStableString)
            .collect(toImmutableList()));

    XMethodElement buildMethod = getBuildMethod(builder);
    XType componentType = getComponentType(builder, buildMethod);

    ImmutableList<XMethodElement> nonStaticNonBuilderMethods =
        builder.getDeclaredMethods().stream()
            .filter(method -> !method.isStatic())
            .filter(method -> !method.equals(buildMethod))
            .filter(method -> !method.getReturnType().getTypeName().equals(builder.getClassName()))
            .collect(toImmutableList());

    ProcessorErrors.checkStateX(
        nonStaticNonBuilderMethods.isEmpty(),
        nonStaticNonBuilderMethods,
        "@DefineComponent.Builder %s, all non-static methods must return %s or %s. Found: %s",
        XElements.toStableString(builder),
        XElements.toStableString(builder),
        XTypes.toStableString(componentType),
        nonStaticNonBuilderMethods.stream()
            .map(XElements::toStableString)
            .collect(toImmutableList()));

    return new AutoValue_DefineComponentBuilderMetadatas_DefineComponentBuilderMetadata(
        builder,
        buildMethod,
        componentMetadatas.get(componentType.getTypeElement()));
  }

  private static XMethodElement getBuildMethod(XTypeElement builder) {
    ImmutableList<XMethodElement> buildMethods =
        builder.getDeclaredMethods().stream()
            .filter(method -> !method.isStatic())
            .filter(method -> method.getParameters().isEmpty())
            .collect(toImmutableList());

    ProcessorErrors.checkState(
        buildMethods.size() == 1,
        builder,
        "@DefineComponent.Builder %s, must have exactly 1 build method that takes no parameters. "
            + "Found: %s",
        XElements.toStableString(builder),
        buildMethods.stream()
            .map(XElements::toStableString)
            .collect(toImmutableList()));

    return buildMethods.get(0);
  }

  private static XType getComponentType(XTypeElement builder, XMethodElement buildMethod) {
    XType componentType = buildMethod.getReturnType();
    ProcessorErrors.checkState(
        isDeclared(componentType)
            && componentType.getTypeElement().hasAnnotation(ClassNames.DEFINE_COMPONENT),
        builder,
        "@DefineComponent.Builder method, %s#%s, must return a @DefineComponent type. Found: %s",
        XElements.toStableString(builder),
        XElements.toStableString(buildMethod),
        XTypes.toStableString(componentType));
    return componentType;
  }

  @AutoValue
  abstract static class DefineComponentBuilderMetadata {
    abstract XTypeElement builder();

    abstract XMethodElement buildMethod();

    abstract DefineComponentMetadata componentMetadata();
  }
}

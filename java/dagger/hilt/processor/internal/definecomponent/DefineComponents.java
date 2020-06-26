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
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import com.google.auto.common.MoreElements;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.AnnotationValues;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ComponentDescriptor;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.hilt.processor.internal.Processors;
import dagger.hilt.processor.internal.definecomponent.DefineComponentBuilderMetadatas.DefineComponentBuilderMetadata;
import dagger.hilt.processor.internal.definecomponent.DefineComponentMetadatas.DefineComponentMetadata;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

/**
 * A utility class for getting {@link DefineComponentMetadata} and {@link
 * DefineComponentBuilderMetadata}.
 */
public final class DefineComponents {
  static final String AGGREGATING_PACKAGE =
      DefineComponents.class.getPackage().getName() + ".codegen";

  public static DefineComponents create() {
    return new DefineComponents();
  }

  private final Map<Element, ComponentDescriptor> componentDescriptors = new HashMap<>();
  private final DefineComponentMetadatas componentMetadatas = DefineComponentMetadatas.create();
  private final DefineComponentBuilderMetadatas componentBuilderMetadatas =
      DefineComponentBuilderMetadatas.create(componentMetadatas);

  private DefineComponents() {}

  /** Returns the {@link ComponentDescriptor} for the given component element. */
  // TODO(b/144940889): This descriptor doesn't contain the "creator" or the "installInName".
  public ComponentDescriptor componentDescriptor(Element element) {
    if (!componentDescriptors.containsKey(element)) {
      componentDescriptors.put(element, uncachedComponentDescriptor(element));
    }
    return componentDescriptors.get(element);
  }

  private ComponentDescriptor uncachedComponentDescriptor(Element element) {
    DefineComponentMetadata metadata = componentMetadatas.get(element);
    ComponentDescriptor.Builder builder =
        ComponentDescriptor.builder()
            .component(ClassName.get(metadata.component()))
            .scopes(metadata.scopes().stream().map(ClassName::get).collect(toImmutableSet()));

    metadata.parentMetadata()
        .map(DefineComponentMetadata::component)
        .map(this::componentDescriptor)
        .ifPresent(builder::parent);

    return builder.build();
  }

  /** Returns the list of aggregated {@link ComponentDescriptor}s. */
  public ImmutableList<ComponentDescriptor> componentDescriptors(Elements elements) {
    AggregatedMetadata aggregatedMetadata =
        AggregatedMetadata.from(elements, componentMetadatas, componentBuilderMetadatas);
    ListMultimap<DefineComponentMetadata, DefineComponentBuilderMetadata> builderMultimap =
        ArrayListMultimap.create();
    aggregatedMetadata.builders()
        .forEach(builder -> builderMultimap.put(builder.componentMetadata(), builder));

    // Check that there are not multiple builders per component
    for (DefineComponentMetadata componentMetadata : builderMultimap.keySet()) {
      TypeElement component = componentMetadata.component();
      ProcessorErrors.checkState(
          builderMultimap.get(componentMetadata).size() <= 1,
          component,
          "Multiple @%s declarations are not allowed for @%s type, %s. Found: %s",
          ClassNames.DEFINE_COMPONENT_BUILDER,
          ClassNames.DEFINE_COMPONENT,
          component,
          builderMultimap.get(componentMetadata).stream()
              .map(DefineComponentBuilderMetadata::builder)
              .map(TypeElement::toString)
              .sorted()
              .collect(toImmutableList()));
    }

    // Now that we know there is at most 1 builder per component, convert the Multimap to Map.
    Map<DefineComponentMetadata, DefineComponentBuilderMetadata> builderMap = new LinkedHashMap<>();
    builderMultimap.entries().forEach(e -> builderMap.put(e.getKey(), e.getValue()));

    // Check that there is a builder for every component.
    for (DefineComponentMetadata componentMetadata : aggregatedMetadata.components()) {
      ProcessorErrors.checkState(
          // Note: Root components don't need builders, since they get one by default.
          builderMap.containsKey(componentMetadata) || componentMetadata.isRoot(),
          componentMetadata.component(),
          "Missing @%s declaration for @%s type, %s",
          ClassNames.DEFINE_COMPONENT_BUILDER,
          ClassNames.DEFINE_COMPONENT,
          componentMetadata.component());
    }

    return aggregatedMetadata.components().stream()
        .map(componentMetadata -> toComponentDescriptor(componentMetadata, builderMap))
        .collect(toImmutableList());
  }

  private static ComponentDescriptor toComponentDescriptor(
      DefineComponentMetadata componentMetadata,
      Map<DefineComponentMetadata, DefineComponentBuilderMetadata> builderMap) {
    ComponentDescriptor.Builder builder =
        ComponentDescriptor.builder()
            .component(ClassName.get(componentMetadata.component()))
            .scopes(
                componentMetadata.scopes().stream().map(ClassName::get).collect(toImmutableSet()));

    if (builderMap.containsKey(componentMetadata)) {
      builder.creator(ClassName.get(builderMap.get(componentMetadata).builder()));
    }

    componentMetadata
        .parentMetadata()
        .map(parent -> toComponentDescriptor(parent, builderMap))
        .ifPresent(builder::parent);

    return builder.build();
  }

  @AutoValue
  abstract static class AggregatedMetadata {
    /** Returns the aggregated metadata for {@link DefineComponentClasses#component()}. */
    abstract ImmutableList<DefineComponentMetadata> components();

    /** Returns the aggregated metadata for {@link DefineComponentClasses#builder()}. */
    abstract ImmutableList<DefineComponentBuilderMetadata> builders();

    static AggregatedMetadata from(
        Elements elements,
        DefineComponentMetadatas componentMetadatas,
        DefineComponentBuilderMetadatas componentBuilderMetadatas) {
      PackageElement packageElement = elements.getPackageElement(AGGREGATING_PACKAGE);

      if (packageElement == null) {
        return new AutoValue_DefineComponents_AggregatedMetadata(
            ImmutableList.of(), ImmutableList.of());
      }

      ImmutableList.Builder<DefineComponentMetadata> components = ImmutableList.builder();
      ImmutableList.Builder<DefineComponentBuilderMetadata> builders = ImmutableList.builder();
      for (Element element : packageElement.getEnclosedElements()) {
        ProcessorErrors.checkState(
            MoreElements.isType(element),
            element,
            "Only types may be in package %s. Did you add custom code in the package?",
            packageElement);

        TypeElement typeElement = MoreElements.asType(element);
        ProcessorErrors.checkState(
            Processors.hasAnnotation(typeElement, ClassNames.DEFINE_COMPONENT_CLASSES),
            typeElement,
            "Class, %s, must be annotated with @%s. Found: %s.",
            typeElement,
            ClassNames.DEFINE_COMPONENT_CLASSES.simpleName(),
            typeElement.getAnnotationMirrors());

        Optional<TypeElement> component = defineComponentClass(elements, typeElement, "component");
        Optional<TypeElement> builder = defineComponentClass(elements, typeElement, "builder");
        ProcessorErrors.checkState(
            component.isPresent() || builder.isPresent(),
            typeElement,
            "@DefineComponentClasses missing both `component` and `builder` members.");

        component.map(componentMetadatas::get).ifPresent(components::add);
        builder.map(componentBuilderMetadatas::get).ifPresent(builders::add);
      }

      return new AutoValue_DefineComponents_AggregatedMetadata(
          components.build(), builders.build());
    }

    private static Optional<TypeElement> defineComponentClass(
        Elements elements, Element element, String annotationMember) {
      AnnotationMirror mirror =
          Processors.getAnnotationMirror(element, ClassNames.DEFINE_COMPONENT_CLASSES);
      AnnotationValue value = getAnnotationElementAndValue(mirror, annotationMember).getValue();
      String className = AnnotationValues.getString(value);

      if (className.isEmpty()) { // The default value.
        return Optional.empty();
      }

      TypeElement type = elements.getTypeElement(className);
      ProcessorErrors.checkState(
          type != null,
          element,
          "%s.%s(), has invalid value: `%s`.",
          ClassNames.DEFINE_COMPONENT_CLASSES.simpleName(),
          annotationMember,
          className);

      return Optional.of(type);
    }
  }
}

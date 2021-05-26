/*
 * Copyright (C) 2021 The Dagger Authors.
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

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.AggregatedElements;
import dagger.hilt.processor.internal.AnnotationValues;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.hilt.processor.internal.Processors;
import dagger.hilt.processor.internal.root.ir.DefineComponentClassesIr;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

/**
 * A class that represents the values stored in an {@link
 * dagger.hilt.internal.definecomponent.DefineComponentClasses} annotation.
 */
@AutoValue
public abstract class DefineComponentClassesMetadata {

  /** Returns the aggregating element */
  public abstract TypeElement aggregatingElement();

  /**
   * Returns the element annotated with {@code dagger.hilt.internal.definecomponent.DefineComponent}
   * or {@code dagger.hilt.internal.definecomponent.DefineComponent.Builder}.
   */
  abstract TypeElement element();

  /** Returns {@code true} if this element represents a component. */
  abstract boolean isComponent();

  /** Returns {@code true} if this element represents a component builder. */
  boolean isComponentBuilder() {
    return !isComponent();
  }

  /** Returns metadata for all aggregated elements in the aggregating package. */
  public static ImmutableSet<DefineComponentClassesMetadata> from(Elements elements) {
    return from(
        AggregatedElements.from(
            ClassNames.DEFINE_COMPONENT_CLASSES_PACKAGE,
            ClassNames.DEFINE_COMPONENT_CLASSES,
            elements),
        elements);
  }

  /** Returns metadata for each aggregated element. */
  public static ImmutableSet<DefineComponentClassesMetadata> from(
      ImmutableSet<TypeElement> aggregatedElements, Elements elements) {
    return aggregatedElements.stream()
        .map(aggregatedElement -> create(aggregatedElement, elements))
        .collect(toImmutableSet());
  }

  private static DefineComponentClassesMetadata create(TypeElement element, Elements elements) {
    AnnotationMirror annotationMirror =
        Processors.getAnnotationMirror(element, ClassNames.DEFINE_COMPONENT_CLASSES);

    ImmutableMap<String, AnnotationValue> values =
        Processors.getAnnotationValues(elements, annotationMirror);

    String componentName = AnnotationValues.getString(values.get("component"));
    String builderName = AnnotationValues.getString(values.get("builder"));

    ProcessorErrors.checkState(
        !(componentName.isEmpty() && builderName.isEmpty()),
        element,
        "@DefineComponentClasses missing both `component` and `builder` members.");

    ProcessorErrors.checkState(
        componentName.isEmpty() || builderName.isEmpty(),
        element,
        "@DefineComponentClasses should not include both `component` and `builder` members.");

    boolean isComponent = !componentName.isEmpty();
    String componentOrBuilderName = isComponent ? componentName : builderName;
    TypeElement componentOrBuilderElement = elements.getTypeElement(componentOrBuilderName);
    ProcessorErrors.checkState(
        componentOrBuilderElement != null,
        componentOrBuilderElement,
        "%s.%s(), has invalid value: `%s`.",
        ClassNames.DEFINE_COMPONENT_CLASSES.simpleName(),
        isComponent ? "component" : "builder",
        componentOrBuilderName);
    return new AutoValue_DefineComponentClassesMetadata(
        element, componentOrBuilderElement, isComponent);
  }

  public static DefineComponentClassesIr toIr(DefineComponentClassesMetadata metadata) {
    return new DefineComponentClassesIr(
        ClassName.get(metadata.aggregatingElement()),
        ClassName.get(metadata.element()));
  }
}

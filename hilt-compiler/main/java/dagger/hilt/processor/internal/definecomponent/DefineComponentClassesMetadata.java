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

import static androidx.room.compiler.processing.compat.XConverters.getProcessingEnv;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import dagger.hilt.processor.internal.AggregatedElements;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.hilt.processor.internal.root.ir.DefineComponentClassesIr;

/**
 * A class that represents the values stored in an {@link
 * dagger.hilt.internal.definecomponent.DefineComponentClasses} annotation.
 */
@AutoValue
public abstract class DefineComponentClassesMetadata {

  /** Returns the aggregating element */
  public abstract XTypeElement aggregatingElement();

  /**
   * Returns the element annotated with {@code dagger.hilt.internal.definecomponent.DefineComponent}
   * or {@code dagger.hilt.internal.definecomponent.DefineComponent.Builder}.
   */
  public abstract XTypeElement element();

  /** Returns {@code true} if this element represents a component. */
  abstract boolean isComponent();

  /** Returns {@code true} if this element represents a component builder. */
  boolean isComponentBuilder() {
    return !isComponent();
  }

  /** Returns metadata for all aggregated elements in the aggregating package. */
  public static ImmutableSet<DefineComponentClassesMetadata> from(XProcessingEnv env) {
    return from(
        AggregatedElements.from(
            ClassNames.DEFINE_COMPONENT_CLASSES_PACKAGE, ClassNames.DEFINE_COMPONENT_CLASSES, env));
  }

  /** Returns metadata for each aggregated element. */
  public static ImmutableSet<DefineComponentClassesMetadata> from(
      ImmutableSet<XTypeElement> aggregatedElements) {
    return aggregatedElements.stream()
        .map(aggregatedElement -> create(aggregatedElement))
        .collect(toImmutableSet());
  }

  private static DefineComponentClassesMetadata create(XTypeElement element) {
    XAnnotation annotation = element.getAnnotation(ClassNames.DEFINE_COMPONENT_CLASSES);

    String componentName = annotation.getAsString("component");
    String builderName = annotation.getAsString("builder");

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
    XTypeElement componentOrBuilderElement =
        getProcessingEnv(element).findTypeElement(componentOrBuilderName);
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
        metadata.aggregatingElement().getClassName(),
        metadata.element().getClassName().canonicalName());
  }
}

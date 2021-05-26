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

package dagger.hilt.processor.internal.uninstallmodules;

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.AggregatedElements;
import dagger.hilt.processor.internal.AnnotationValues;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.Processors;
import dagger.hilt.processor.internal.root.ir.AggregatedUninstallModulesIr;
import java.util.stream.Collectors;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

/**
 * A class that represents the values stored in an
 * {@link dagger.hilt.android.internal.uninstallmodules.AggregatedUninstallModules} annotation.
 */
@AutoValue
public abstract class AggregatedUninstallModulesMetadata {

  /** Returns the aggregating element */
  public abstract TypeElement aggregatingElement();

  /** Returns the test annotated with {@link dagger.hilt.android.testing.UninstallModules}. */
  public abstract TypeElement testElement();

  /**
   * Returns the list of uninstall modules in {@link dagger.hilt.android.testing.UninstallModules}.
   */
  public abstract ImmutableList<TypeElement> uninstallModuleElements();

  /** Returns metadata for all aggregated elements in the aggregating package. */
  public static ImmutableSet<AggregatedUninstallModulesMetadata> from(Elements elements) {
    return from(
        AggregatedElements.from(
            ClassNames.AGGREGATED_UNINSTALL_MODULES_PACKAGE,
            ClassNames.AGGREGATED_UNINSTALL_MODULES,
            elements),
        elements);
  }

  /** Returns metadata for each aggregated element. */
  public static ImmutableSet<AggregatedUninstallModulesMetadata> from(
      ImmutableSet<TypeElement> aggregatedElements, Elements elements) {
    return aggregatedElements.stream()
        .map(aggregatedElement -> create(aggregatedElement, elements))
        .collect(toImmutableSet());
  }

  public static AggregatedUninstallModulesIr toIr(AggregatedUninstallModulesMetadata metadata) {
    return new AggregatedUninstallModulesIr(
        ClassName.get(metadata.aggregatingElement()),
        ClassName.get(metadata.testElement()),
        metadata.uninstallModuleElements().stream()
            .map(ClassName::get)
            .collect(Collectors.toList()));
  }

  private static AggregatedUninstallModulesMetadata create(TypeElement element, Elements elements) {
    AnnotationMirror annotationMirror =
        Processors.getAnnotationMirror(element, ClassNames.AGGREGATED_UNINSTALL_MODULES);

    ImmutableMap<String, AnnotationValue> values =
        Processors.getAnnotationValues(elements, annotationMirror);

    return new AutoValue_AggregatedUninstallModulesMetadata(
        element,
        elements.getTypeElement(AnnotationValues.getString(values.get("test"))),
        AnnotationValues.getAnnotationValues(values.get("uninstallModules")).stream()
            .map(AnnotationValues::getString)
            .map(elements::getTypeElement)
            .collect(toImmutableList()));
  }
}

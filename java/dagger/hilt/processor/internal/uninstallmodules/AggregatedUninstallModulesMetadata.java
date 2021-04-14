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
import dagger.hilt.processor.internal.AggregatedElements;
import dagger.hilt.processor.internal.AnnotationValues;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.Processors;
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

  /** Returns the test annotated with {@link dagger.hilt.android.testing.UninstallModules}. */
  public abstract TypeElement testElement();

  /**
   * Returns the list of uninstall modules in {@link dagger.hilt.android.testing.UninstallModules}.
   */
  public abstract ImmutableList<TypeElement> uninstallModuleElements();

  /** Returns all aggregated deps in the aggregating package mapped by the top-level element. */
  public static ImmutableSet<AggregatedUninstallModulesMetadata> from(Elements elements) {
    return AggregatedElements.from(
            ClassNames.AGGREGATED_UNINSTALL_MODULES_PACKAGE,
            ClassNames.AGGREGATED_UNINSTALL_MODULES,
            elements)
        .stream()
        .map(aggregatedElement -> create(aggregatedElement, elements))
        .collect(toImmutableSet());
  }

  private static AggregatedUninstallModulesMetadata create(TypeElement element, Elements elements) {
    AnnotationMirror annotationMirror =
        Processors.getAnnotationMirror(element, ClassNames.AGGREGATED_UNINSTALL_MODULES);

    ImmutableMap<String, AnnotationValue> values =
        Processors.getAnnotationValues(elements, annotationMirror);

    return new AutoValue_AggregatedUninstallModulesMetadata(
        elements.getTypeElement(AnnotationValues.getString(values.get("test"))),
        AnnotationValues.getAnnotationValues(values.get("uninstallModules")).stream()
            .map(AnnotationValues::getString)
            .map(elements::getTypeElement)
            .collect(toImmutableList()));
  }
}

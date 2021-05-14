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

package dagger.hilt.processor.internal.root;

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import com.google.auto.value.AutoValue;
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
 * Represents the values stored in an {@link
 * dagger.hilt.internal.processedrootsentinel.ProcessedRootSentinel}.
 */
@AutoValue
abstract class ProcessedRootSentinelMetadata {

  /** Returns the processed root elements. */
  abstract ImmutableSet<TypeElement> rootElements();

  static ImmutableSet<ProcessedRootSentinelMetadata> from(Elements elements) {
    return AggregatedElements.from(
            ClassNames.PROCESSED_ROOT_SENTINEL_PACKAGE,
            ClassNames.PROCESSED_ROOT_SENTINEL,
            elements)
        .stream()
        .map(aggregatedElement -> create(aggregatedElement, elements))
        .collect(toImmutableSet());
  }

  private static ProcessedRootSentinelMetadata create(TypeElement element, Elements elements) {
    AnnotationMirror annotationMirror =
        Processors.getAnnotationMirror(element, ClassNames.PROCESSED_ROOT_SENTINEL);

    ImmutableMap<String, AnnotationValue> values =
        Processors.getAnnotationValues(elements, annotationMirror);

    return new AutoValue_ProcessedRootSentinelMetadata(
        AnnotationValues.getStrings(values.get("roots")).stream()
            .map(elements::getTypeElement)
            .collect(toImmutableSet()));
  }
}

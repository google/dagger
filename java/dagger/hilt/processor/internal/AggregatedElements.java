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

package dagger.hilt.processor.internal;

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

/** Utility class for aggregating metadata. */
public final class AggregatedElements {

  /** Returns all aggregated elements in the aggregating package after validating them. */
  public static ImmutableSet<TypeElement> from(
      String aggregatingPackage, ClassName aggregatingAnnotation, Elements elements) {
    PackageElement packageElement = elements.getPackageElement(aggregatingPackage);

    if (packageElement == null) {
      return ImmutableSet.of();
    }

    ImmutableSet<TypeElement> aggregatedElements =
        packageElement.getEnclosedElements().stream()
            .map(MoreElements::asType)
            .collect(toImmutableSet());

    ProcessorErrors.checkState(
        !aggregatedElements.isEmpty(),
        packageElement,
        "No dependencies found. Did you remove code in package %s?",
        packageElement);

    for (TypeElement aggregatedElement : aggregatedElements) {
      ProcessorErrors.checkState(
          Processors.hasAnnotation(aggregatedElement, aggregatingAnnotation),
          aggregatedElement,
          "Expected element, %s, to be annotated with @%s, but only found: %s.",
          aggregatedElement.getSimpleName(),
          aggregatingAnnotation,
          aggregatedElement.getAnnotationMirrors());
    }

    return aggregatedElements;
  }

  private AggregatedElements() {}
}

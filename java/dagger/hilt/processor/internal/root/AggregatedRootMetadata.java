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
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.AggregatedElements;
import dagger.hilt.processor.internal.AnnotationValues;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.Processors;
import dagger.hilt.processor.internal.root.ir.AggregatedRootIr;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.TypeElement;

/**
 * Represents the values stored in an {@link dagger.hilt.internal.aggregatedroot.AggregatedRoot}.
 */
@AutoValue
abstract class AggregatedRootMetadata {

  /** Returns the aggregating element */
  public abstract TypeElement aggregatingElement();

  /** Returns the element that was annotated with the root annotation. */
  abstract TypeElement rootElement();

  /**
   * Returns the originating root element. In most cases this will be the same as
   * {@link #rootElement()}.
   */
  abstract TypeElement originatingRootElement();

  /** Returns the root annotation as an element. */
  abstract TypeElement rootAnnotation();

  /** Returns whether this root can use a shared component. */
  abstract boolean allowsSharingComponent();

  @Memoized
  RootType rootType() {
    return RootType.of(rootElement());
  }

  static ImmutableSet<AggregatedRootMetadata> from(ProcessingEnvironment env) {
    return from(
        AggregatedElements.from(
            ClassNames.AGGREGATED_ROOT_PACKAGE, ClassNames.AGGREGATED_ROOT, env.getElementUtils()),
        env);
  }

  /** Returns metadata for each aggregated element. */
  public static ImmutableSet<AggregatedRootMetadata> from(
      ImmutableSet<TypeElement> aggregatedElements, ProcessingEnvironment env) {
    return aggregatedElements.stream()
        .map(aggregatedElement -> create(aggregatedElement, env))
        .collect(toImmutableSet());
  }

  public static AggregatedRootIr toIr(AggregatedRootMetadata metadata) {
    return new AggregatedRootIr(
        ClassName.get(metadata.aggregatingElement()),
        ClassName.get(metadata.rootElement()),
        ClassName.get(metadata.originatingRootElement()),
        ClassName.get(metadata.rootAnnotation()),
        metadata.allowsSharingComponent());
  }

  private static AggregatedRootMetadata create(TypeElement element, ProcessingEnvironment env) {
    AnnotationMirror annotationMirror =
        Processors.getAnnotationMirror(element, ClassNames.AGGREGATED_ROOT);

    ImmutableMap<String, AnnotationValue> values =
        Processors.getAnnotationValues(env.getElementUtils(), annotationMirror);

    TypeElement rootElement =
        env.getElementUtils().getTypeElement(AnnotationValues.getString(values.get("root")));
    boolean allowSharingComponent = true;
    return new AutoValue_AggregatedRootMetadata(
        element,
        rootElement,
        env.getElementUtils()
            .getTypeElement(AnnotationValues.getString(values.get("originatingRoot"))),
        AnnotationValues.getTypeElement(values.get("rootAnnotation")),
        allowSharingComponent);
  }
}

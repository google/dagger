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

package dagger.hilt.processor.internal.aliasof;

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XAnnotationValue;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import dagger.hilt.processor.internal.AggregatedElements;
import dagger.hilt.processor.internal.BadInputException;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.Processors;
import dagger.hilt.processor.internal.root.ir.AliasOfPropagatedDataIr;
import dagger.internal.codegen.xprocessing.XAnnotations;

/**
 * A class that represents the values stored in an {@link
 * dagger.hilt.internal.aliasof.AliasOfPropagatedData} annotation.
 */
@AutoValue
public abstract class AliasOfPropagatedDataMetadata {

  /** Returns the aggregating element */
  public abstract XTypeElement aggregatingElement();

  abstract ImmutableList<XTypeElement> defineComponentScopeElements();

  abstract XTypeElement aliasElement();

  /** Returns metadata for all aggregated elements in the aggregating package. */
  public static ImmutableSet<AliasOfPropagatedDataMetadata> from(XProcessingEnv env) {
    return from(
        AggregatedElements.from(
            ClassNames.ALIAS_OF_PROPAGATED_DATA_PACKAGE, ClassNames.ALIAS_OF_PROPAGATED_DATA, env));
  }

  /** Returns metadata for each aggregated element. */
  public static ImmutableSet<AliasOfPropagatedDataMetadata> from(
      ImmutableSet<XTypeElement> aggregatedElements) {
    return aggregatedElements.stream()
        .map(AliasOfPropagatedDataMetadata::create)
        .collect(toImmutableSet());
  }

  public static AliasOfPropagatedDataIr toIr(AliasOfPropagatedDataMetadata metadata) {
    return new AliasOfPropagatedDataIr(
        metadata.aggregatingElement().getClassName(),
        metadata.defineComponentScopeElements().stream()
            .map(XTypeElement::getClassName)
            .collect(toImmutableList()),
        metadata.aliasElement().getClassName());
  }

  private static AliasOfPropagatedDataMetadata create(XTypeElement element) {
    XAnnotation annotation = element.getAnnotation(ClassNames.ALIAS_OF_PROPAGATED_DATA);

    // TODO(kuanyingchou) We can remove this once we have
    // `XAnnotation.hasAnnotationValue(methodName: String)`.
    ImmutableMap<String, XAnnotationValue> values = Processors.getAnnotationValues(annotation);

    ImmutableList<XTypeElement> defineComponentScopes;

    if (values.containsKey("defineComponentScopes")) {
      defineComponentScopes =
          XAnnotations.getAsTypeElementList(annotation, "defineComponentScopes");
    } else if (values.containsKey("defineComponentScope")) {
      // Older version of AliasOfPropagatedData only passed a single defineComponentScope class
      // value. Fall back on reading the single value if we get old propagated data.
      defineComponentScopes = XAnnotations.getAsTypeElementList(annotation, "defineComponentScope");
    } else {
      throw new BadInputException(
          "AliasOfPropagatedData is missing defineComponentScopes", element);
    }

    return new AutoValue_AliasOfPropagatedDataMetadata(
        element, defineComponentScopes, annotation.getAsType("alias").getTypeElement());
  }
}

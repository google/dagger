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

package dagger.hilt.processor.internal.earlyentrypoint;

import static androidx.room3.compiler.processing.compat.XConverters.getProcessingEnv;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import androidx.room3.compiler.processing.XAnnotation;
import androidx.room3.compiler.processing.XProcessingEnv;
import androidx.room3.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.AggregatedElements;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.root.ir.AggregatedEarlyEntryPointIr;

/**
 * A class that represents the values stored in an {@link
 * dagger.hilt.android.internal.earlyentrypoint.AggregatedEarlyEntryPoint} annotation.
 */
@AutoValue
public abstract class AggregatedEarlyEntryPointMetadata {

  /** Returns the name of the aggregating element. */
  public abstract ClassName name();

  /** Returns the element annotated with {@link dagger.hilt.android.EarlyEntryPoint}. */
  public abstract ClassName earlyEntryPointName();

  public static AggregatedEarlyEntryPointIr toIr(AggregatedEarlyEntryPointMetadata metadata) {
    return new AggregatedEarlyEntryPointIr(
        metadata.name(), metadata.earlyEntryPointName().canonicalName());
  }

  /** Returns metadata for all aggregated elements in the aggregating package. */
  public static ImmutableSet<AggregatedEarlyEntryPointMetadata> from(XProcessingEnv env) {
    return AggregatedElements.from(
            ClassNames.AGGREGATED_EARLY_ENTRY_POINT_PACKAGE,
            ClassNames.AGGREGATED_EARLY_ENTRY_POINT,
            env)
        .stream()
        .map(AggregatedEarlyEntryPointMetadata::from)
        .collect(toImmutableSet());
  }

  /** Returns metadata for the given aggregated element. */
  public static AggregatedEarlyEntryPointMetadata from(XTypeElement element) {
    XProcessingEnv env = getProcessingEnv(element);
    XAnnotation annotation = element.getAnnotation(ClassNames.AGGREGATED_EARLY_ENTRY_POINT);

    return new AutoValue_AggregatedEarlyEntryPointMetadata(
        element.getClassName(),
        env.requireTypeElement(annotation.getAsString("earlyEntryPoint")).getClassName());
  }
}

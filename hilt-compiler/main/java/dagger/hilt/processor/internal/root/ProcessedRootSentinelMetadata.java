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

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.AggregatedElements;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.root.ir.ProcessedRootSentinelIr;
import java.util.stream.Collectors;

/**
 * Represents the values stored in an {@link
 * dagger.hilt.internal.processedrootsentinel.ProcessedRootSentinel}.
 */
@AutoValue
abstract class ProcessedRootSentinelMetadata {

  /** Returns the aggregating element */
  public abstract XTypeElement aggregatingElement();

  /** Returns the processed root elements. */
  abstract ImmutableSet<XTypeElement> rootElements();

  static ImmutableSet<ProcessedRootSentinelMetadata> from(XProcessingEnv env) {
    return AggregatedElements.from(
            ClassNames.PROCESSED_ROOT_SENTINEL_PACKAGE, ClassNames.PROCESSED_ROOT_SENTINEL, env)
        .stream()
        .map(aggregatedElement -> create(aggregatedElement, env))
        .collect(toImmutableSet());
  }

  static ProcessedRootSentinelIr toIr(ProcessedRootSentinelMetadata metadata) {
    return new ProcessedRootSentinelIr(
        metadata.aggregatingElement().getClassName(),
        metadata.rootElements().stream()
            .map(XTypeElement::getClassName)
            .map(ClassName::canonicalName)
            .collect(Collectors.toList()));
  }

  private static ProcessedRootSentinelMetadata create(XTypeElement element, XProcessingEnv env) {
    XAnnotation annotationMirror = element.getAnnotation(ClassNames.PROCESSED_ROOT_SENTINEL);

    return new AutoValue_ProcessedRootSentinelMetadata(
        element,
        annotationMirror.getAsStringList("roots").stream()
            .map(env::requireTypeElement)
            .collect(toImmutableSet()));
  }
}

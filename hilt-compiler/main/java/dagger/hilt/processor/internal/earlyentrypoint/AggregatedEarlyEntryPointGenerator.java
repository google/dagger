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

import androidx.room.compiler.processing.XTypeElement;
import com.squareup.javapoet.AnnotationSpec;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.Processors;

/**
 * Generates an {@link dagger.hilt.android.internal.earlyentrypoint.AggregatedEarlyEntryPoint}
 * annotation.
 */
final class AggregatedEarlyEntryPointGenerator {
  private final XTypeElement earlyEntryPoint;

  AggregatedEarlyEntryPointGenerator(XTypeElement earlyEntryPoint) {
    this.earlyEntryPoint = earlyEntryPoint;
  }

  void generate() {
    Processors.generateAggregatingClass(
        ClassNames.AGGREGATED_EARLY_ENTRY_POINT_PACKAGE,
        AnnotationSpec.builder(ClassNames.AGGREGATED_EARLY_ENTRY_POINT)
            .addMember("earlyEntryPoint", "$S", earlyEntryPoint.getQualifiedName())
            .build(),
        earlyEntryPoint,
        getClass());
  }
}

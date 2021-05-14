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

import com.squareup.javapoet.AnnotationSpec;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.Processors;
import java.io.IOException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;

/**
 * Generates an {@link dagger.hilt.android.internal.earlyentrypoint.AggregatedEarlyEntryPoint}
 * annotation.
 */
final class AggregatedEarlyEntryPointGenerator {

  private final ProcessingEnvironment env;
  private final TypeElement earlyEntryPoint;

  AggregatedEarlyEntryPointGenerator(TypeElement earlyEntryPoint, ProcessingEnvironment env) {
    this.earlyEntryPoint = earlyEntryPoint;
    this.env = env;
  }

  void generate() throws IOException {
    Processors.generateAggregatingClass(
        ClassNames.AGGREGATED_EARLY_ENTRY_POINT_PACKAGE,
        AnnotationSpec.builder(ClassNames.AGGREGATED_EARLY_ENTRY_POINT)
            .addMember("earlyEntryPoint", "$S", earlyEntryPoint.getQualifiedName())
            .build(),
        earlyEntryPoint,
        getClass(),
        env);
  }
}

/*
 * Copyright (C) 2019 The Dagger Authors.
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

import static dagger.hilt.processor.internal.HiltCompilerOptions.useAggregatingRootProcessor;
import static net.ltgt.gradle.incap.IncrementalAnnotationProcessorType.AGGREGATING;
import static net.ltgt.gradle.incap.IncrementalAnnotationProcessorType.DYNAMIC;
import static net.ltgt.gradle.incap.IncrementalAnnotationProcessorType.ISOLATING;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableSet;
import dagger.hilt.processor.internal.BaseProcessingStep;
import dagger.hilt.processor.internal.JavacBaseProcessingStepProcessor;
import javax.annotation.processing.Processor;
import net.ltgt.gradle.incap.IncrementalAnnotationProcessor;

/** Processor that outputs dagger components based on transitive build deps. */
@IncrementalAnnotationProcessor(DYNAMIC)
@AutoService(Processor.class)
public final class RootProcessor extends JavacBaseProcessingStepProcessor {
  @Override
  protected BaseProcessingStep processingStep() {
    return new RootProcessingStep(getXProcessingEnv());
  }

  @Override
  public ImmutableSet<String> additionalProcessingOptions() {
    return useAggregatingRootProcessor(getXProcessingEnv())
        ? ImmutableSet.of(AGGREGATING.getProcessorOption())
        : ImmutableSet.of(ISOLATING.getProcessorOption());
  }
}

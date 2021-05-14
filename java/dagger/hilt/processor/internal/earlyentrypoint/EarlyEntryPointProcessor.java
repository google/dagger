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

import static com.google.auto.common.MoreElements.asType;
import static net.ltgt.gradle.incap.IncrementalAnnotationProcessorType.ISOLATING;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableSet;
import dagger.hilt.processor.internal.BaseProcessor;
import dagger.hilt.processor.internal.ClassNames;
import javax.annotation.processing.Processor;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import net.ltgt.gradle.incap.IncrementalAnnotationProcessor;

/** Validates {@link dagger.hilt.android.EarlyEntryPoint} usages. */
@IncrementalAnnotationProcessor(ISOLATING)
@AutoService(Processor.class)
public final class EarlyEntryPointProcessor extends BaseProcessor {

  @Override
  public ImmutableSet<String> getSupportedAnnotationTypes() {
    return ImmutableSet.of(ClassNames.EARLY_ENTRY_POINT.toString());
  }

  @Override
  public void processEach(TypeElement annotation, Element element) throws Exception {
    new AggregatedEarlyEntryPointGenerator(asType(element), getProcessingEnv()).generate();
  }
}

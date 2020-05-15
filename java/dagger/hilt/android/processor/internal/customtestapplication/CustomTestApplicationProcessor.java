/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.hilt.android.processor.internal.customtestapplication;

import static net.ltgt.gradle.incap.IncrementalAnnotationProcessorType.ISOLATING;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableSet;
import dagger.hilt.processor.internal.BaseProcessor;
import dagger.hilt.processor.internal.ClassNames;
import javax.annotation.processing.Processor;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import net.ltgt.gradle.incap.IncrementalAnnotationProcessor;

/** Processes usages of {@link dagger.hilt.android.testing.CustomTestApplication}. */
@IncrementalAnnotationProcessor(ISOLATING)
@AutoService(Processor.class)
public final class CustomTestApplicationProcessor extends BaseProcessor {

  @Override
  public ImmutableSet<String> getSupportedAnnotationTypes() {
    return ImmutableSet.of(ClassNames.CUSTOM_TEST_APPLICATION.toString());
  }

  @Override
  public void processEach(TypeElement annotation, Element element) throws Exception {
    CustomTestApplicationMetadata metadata =
        CustomTestApplicationMetadata.of(element, getElementUtils());
    new CustomTestApplicationGenerator(getProcessingEnv(), metadata).generate();
  }
}

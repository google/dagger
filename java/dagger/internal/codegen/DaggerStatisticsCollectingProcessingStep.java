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

package dagger.internal.codegen;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.auto.common.BasicAnnotationProcessor.ProcessingStep;
import com.google.common.collect.SetMultimap;
import java.lang.annotation.Annotation;
import java.util.Set;
import javax.lang.model.element.Element;

/**
 * {@link ProcessingStep} that delegates to another {@code ProcessingStep} and collects timing
 * statistics for each processing round for that step.
 */
final class DaggerStatisticsCollectingProcessingStep implements ProcessingStep {

  private final ProcessingStep delegate;
  private final DaggerStatisticsCollector statisticsCollector;

  DaggerStatisticsCollectingProcessingStep(
      ProcessingStep delegate, DaggerStatisticsCollector statisticsCollector) {
    this.delegate = checkNotNull(delegate);
    this.statisticsCollector = checkNotNull(statisticsCollector);
  }

  @Override
  public Set<? extends Class<? extends Annotation>> annotations() {
    return delegate.annotations();
  }

  @Override
  public Set<? extends Element> process(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    statisticsCollector.stepStarted(delegate);
    try {
      return delegate.process(elementsByAnnotation);
    } finally {
      statisticsCollector.stepFinished(delegate);
    }
  }
}

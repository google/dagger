/*
 * Copyright (C) 2015 The Dagger Authors.
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

import com.google.auto.common.BasicAnnotationProcessor.ProcessingStep;
import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import dagger.producers.ProductionComponent;
import dagger.producers.ProductionSubcomponent;
import java.lang.annotation.Annotation;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.lang.model.element.Element;

/**
 * A processing step that is responsible for generating a special module for a
 * {@link ProductionComponent} or {@link ProductionSubcomponent}.
 */
final class MonitoringModuleProcessingStep implements ProcessingStep {
  private final Messager messager;
  private final MonitoringModuleGenerator monitoringModuleGenerator;

  @Inject
  MonitoringModuleProcessingStep(
      Messager messager, MonitoringModuleGenerator monitoringModuleGenerator) {
    this.messager = messager;
    this.monitoringModuleGenerator = monitoringModuleGenerator;
  }

  @Override
  public Set<? extends Class<? extends Annotation>> annotations() {
    return ImmutableSet.of(ProductionComponent.class, ProductionSubcomponent.class);
  }

  @Override
  public Set<Element> process(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    for (Element element : elementsByAnnotation.values()) {
      monitoringModuleGenerator.generate(MoreElements.asType(element), messager);
    }
    return ImmutableSet.of();
  }
}

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

import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableSet;
import dagger.producers.ProductionComponent;
import dagger.producers.ProductionSubcomponent;
import java.lang.annotation.Annotation;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.lang.model.element.TypeElement;

/**
 * A processing step that is responsible for generating a special module for a {@link
 * ProductionComponent} or {@link ProductionSubcomponent}.
 */
final class MonitoringModuleProcessingStep extends TypeCheckingProcessingStep<TypeElement> {
  private final Messager messager;
  private final MonitoringModuleGenerator monitoringModuleGenerator;

  @Inject
  MonitoringModuleProcessingStep(
      Messager messager, MonitoringModuleGenerator monitoringModuleGenerator) {
    super(MoreElements::asType);
    this.messager = messager;
    this.monitoringModuleGenerator = monitoringModuleGenerator;
  }

  @Override
  public Set<? extends Class<? extends Annotation>> annotations() {
    return ImmutableSet.of(ProductionComponent.class, ProductionSubcomponent.class);
  }

  @Override
  protected void process(
      TypeElement element, ImmutableSet<Class<? extends Annotation>> annotations) {
      monitoringModuleGenerator.generate(MoreElements.asType(element), messager);
  }
}

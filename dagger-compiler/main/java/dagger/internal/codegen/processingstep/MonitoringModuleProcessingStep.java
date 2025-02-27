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

package dagger.internal.codegen.processingstep;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.xprocessing.XTypeNames;
import javax.inject.Inject;

/**
 * A processing step that is responsible for generating a special module for a {@link
 * dagger.producers.ProductionComponent} or {@link dagger.producers.ProductionSubcomponent}.
 */
final class MonitoringModuleProcessingStep extends TypeCheckingProcessingStep<XTypeElement> {
  private final MonitoringModuleGenerator monitoringModuleGenerator;

  @Inject
  MonitoringModuleProcessingStep(MonitoringModuleGenerator monitoringModuleGenerator) {
    this.monitoringModuleGenerator = monitoringModuleGenerator;
  }

  @Override
  public ImmutableSet<XClassName> annotationClassNames() {
    return ImmutableSet.of(XTypeNames.PRODUCTION_COMPONENT, XTypeNames.PRODUCTION_SUBCOMPONENT);
  }

  @Override
  protected void process(XTypeElement productionComponent, ImmutableSet<XClassName> annotations) {
    monitoringModuleGenerator.generate(productionComponent, messager);
  }
}

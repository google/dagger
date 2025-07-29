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

import static dagger.internal.codegen.binding.SourceFiles.generatedMonitoringModuleName;
import static dagger.internal.codegen.xprocessing.XFunSpecs.constructorBuilder;
import static dagger.internal.codegen.xprocessing.XFunSpecs.methodBuilder;
import static dagger.internal.codegen.xprocessing.XTypeNames.javaxProviderOf;
import static dagger.internal.codegen.xprocessing.XTypeNames.setOf;
import static dagger.internal.codegen.xprocessing.XTypeSpecs.classBuilder;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XFunSpec;
import androidx.room.compiler.codegen.XTypeSpec;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XFiler;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableList;
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.binding.MonitoringModules;
import dagger.internal.codegen.xprocessing.XTypeNames;
import javax.inject.Inject;

/** Generates a monitoring module for use with production components. */
final class MonitoringModuleGenerator extends SourceFileGenerator<XTypeElement> {
  private final MonitoringModules monitoringModules;

  @Inject
  MonitoringModuleGenerator(
      XFiler filer,
      XProcessingEnv processingEnv,
      MonitoringModules monitoringModules) {
    super(filer, processingEnv);
    this.monitoringModules = monitoringModules;
  }

  @Override
  public XElement originatingElement(XTypeElement componentElement) {
    return componentElement;
  }

  @Override
  public ImmutableList<XTypeSpec> topLevelTypes(XTypeElement componentElement) {
    XClassName name = generatedMonitoringModuleName(componentElement);
    monitoringModules.add(name);
    return ImmutableList.of(
        classBuilder(name)
            .addAnnotation(XTypeNames.MODULE)
            .addModifiers(ABSTRACT)
            .addFunction(privateConstructor())
            .addFunction(setOfFactories())
            .addFunction(monitor(componentElement))
            .build());
  }

  private XFunSpec privateConstructor() {
    return constructorBuilder().addModifiers(PRIVATE).build();
  }

  private XFunSpec setOfFactories() {
    return methodBuilder("setOfFactories")
        .addAnnotation(XTypeNames.MULTIBINDS)
        .addModifiers(ABSTRACT)
        .returns(setOf(XTypeNames.PRODUCTION_COMPONENT_MONITOR_FACTORY))
        .build();
  }

  private XFunSpec monitor(XTypeElement componentElement) {
    return methodBuilder("monitor")
        .returns(XTypeNames.PRODUCTION_COMPONENT_MONITOR)
        .addModifiers(STATIC)
        .addAnnotation(XTypeNames.PROVIDES)
        .addAnnotation(XTypeNames.PRODUCTION_SCOPE)
        .addParameter("component", javaxProviderOf(componentElement.getType().asTypeName()))
        .addParameter(
            "factories", javaxProviderOf(setOf(XTypeNames.PRODUCTION_COMPONENT_MONITOR_FACTORY)))
        .addStatement(
            "return %T.createMonitorForComponent(component, factories)", XTypeNames.MONITORS)
        .build();
  }
}

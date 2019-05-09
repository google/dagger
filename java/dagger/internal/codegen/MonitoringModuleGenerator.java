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

import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static dagger.internal.codegen.javapoet.TypeNames.PRODUCTION_COMPONENT_MONITOR_FACTORY;
import static dagger.internal.codegen.javapoet.TypeNames.providerOf;
import static dagger.internal.codegen.javapoet.TypeNames.setOf;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import dagger.Module;
import dagger.Provides;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.multibindings.Multibinds;
import dagger.producers.ProductionScope;
import dagger.producers.monitoring.ProductionComponentMonitor;
import dagger.producers.monitoring.internal.Monitors;
import java.util.Optional;
import javax.annotation.processing.Filer;
import javax.inject.Inject;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/** Generates a monitoring module for use with production components. */
final class MonitoringModuleGenerator extends SourceFileGenerator<TypeElement> {

  @Inject
  MonitoringModuleGenerator(Filer filer, DaggerElements elements, SourceVersion sourceVersion) {
    super(filer, elements, sourceVersion);
  }

  @Override
  ClassName nameGeneratedType(TypeElement componentElement) {
    return SourceFiles.generatedMonitoringModuleName(componentElement);
  }

  @Override
  Element originatingElement(TypeElement componentElement) {
    return componentElement;
  }

  @Override
  Optional<TypeSpec.Builder> write(ClassName generatedTypeName, TypeElement componentElement) {
    return Optional.of(
        classBuilder(generatedTypeName)
            .addAnnotation(Module.class)
            .addModifiers(ABSTRACT)
            .addMethod(privateConstructor())
            .addMethod(setOfFactories())
            .addMethod(monitor(componentElement)));
  }

  private MethodSpec privateConstructor() {
    return constructorBuilder().addModifiers(PRIVATE).build();
  }

  private MethodSpec setOfFactories() {
    return methodBuilder("setOfFactories")
        .addAnnotation(Multibinds.class)
        .addModifiers(ABSTRACT)
        .returns(setOf(PRODUCTION_COMPONENT_MONITOR_FACTORY))
        .build();
  }

  private MethodSpec monitor(TypeElement componentElement) {
    return methodBuilder("monitor")
        .returns(ProductionComponentMonitor.class)
        .addModifiers(STATIC)
        .addAnnotation(Provides.class)
        .addAnnotation(ProductionScope.class)
        .addParameter(providerOf(ClassName.get(componentElement.asType())), "component")
        .addParameter(
            providerOf(setOf(PRODUCTION_COMPONENT_MONITOR_FACTORY)), "factories")
        .addStatement(
            "return $T.createMonitorForComponent(component, factories)", Monitors.class)
        .build();
  }
}

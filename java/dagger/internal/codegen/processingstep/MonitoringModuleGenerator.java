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

import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static dagger.internal.codegen.javapoet.TypeNames.PRODUCTION_COMPONENT_MONITOR_FACTORY;
import static dagger.internal.codegen.javapoet.TypeNames.providerOf;
import static dagger.internal.codegen.javapoet.TypeNames.setOf;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XFiler;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import dagger.Module;
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.binding.SourceFiles;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.multibindings.Multibinds;
import javax.inject.Inject;

/** Generates a monitoring module for use with production components. */
final class MonitoringModuleGenerator extends SourceFileGenerator<XTypeElement> {

  @Inject
  MonitoringModuleGenerator(XFiler filer, XProcessingEnv processingEnv) {
    super(filer, processingEnv);
  }

  @Override
  public XElement originatingElement(XTypeElement componentElement) {
    return componentElement;
  }

  @Override
  public ImmutableList<TypeSpec.Builder> topLevelTypes(XTypeElement componentElement) {
    return ImmutableList.of(
        classBuilder(SourceFiles.generatedMonitoringModuleName(componentElement))
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

  private MethodSpec monitor(XTypeElement componentElement) {
    return methodBuilder("monitor")
        .returns(TypeNames.PRODUCTION_COMPONENT_MONITOR)
        .addModifiers(STATIC)
        .addAnnotation(TypeNames.PROVIDES)
        .addAnnotation(TypeNames.PRODUCTION_SCOPE)
        .addParameter(providerOf(componentElement.getType().getTypeName()), "component")
        .addParameter(providerOf(setOf(PRODUCTION_COMPONENT_MONITOR_FACTORY)), "factories")
        .addStatement(
            "return $T.createMonitorForComponent(component, factories)", TypeNames.MONITORS)
        .build();
  }
}

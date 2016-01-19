/*
 * Copyright (C) 2015 Google, Inc.
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

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import dagger.Module;
import dagger.Provides;
import dagger.internal.codegen.writer.ClassName;
import dagger.internal.codegen.writer.ClassWriter;
import dagger.internal.codegen.writer.FieldWriter;
import dagger.internal.codegen.writer.JavaWriter;
import dagger.internal.codegen.writer.MethodWriter;
import dagger.internal.codegen.writer.ParameterizedTypeName;
import dagger.internal.codegen.writer.TypeName;
import dagger.producers.monitoring.ProductionComponentMonitor;
import dagger.producers.monitoring.internal.MonitorCache;

import java.util.Set;
import javax.annotation.Generated;
import javax.annotation.processing.Filer;
import javax.inject.Provider;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.element.Modifier.FINAL;

/** Generates a monitoring module for use with production components. */
final class MonitoringModuleGenerator extends JavaWriterSourceFileGenerator<TypeElement> {
  private static final TypeName SET_OF_FACTORIES =
      ParameterizedTypeName.create(
          Set.class, ClassName.fromClass(ProductionComponentMonitor.Factory.class));

  MonitoringModuleGenerator(Filer filer, Elements elements) {
    super(filer, elements);
  }

  @Override
  ClassName nameGeneratedType(TypeElement componentElement) {
    return SourceFiles.generatedMonitoringModuleName(componentElement);
  }

  @Override
  Iterable<? extends Element> getOriginatingElements(TypeElement componentElement) {
    return ImmutableSet.of(componentElement);
  }

  @Override
  Optional<? extends Element> getElementForErrorReporting(TypeElement componentElement) {
    return Optional.of(componentElement);
  }

  @Override
  ImmutableSet<JavaWriter> write(ClassName generatedTypeName, TypeElement componentElement) {
    JavaWriter writer = JavaWriter.inPackage(generatedTypeName.packageName());
    ClassWriter classWriter = writer.addClass(generatedTypeName.simpleName());
    classWriter.annotate(Module.class);
    classWriter.addModifiers(FINAL);

    // TODO(beder): Replace this default set binding with EmptyCollections when it exists.
    MethodWriter emptySetBindingMethod =
        classWriter.addMethod(SET_OF_FACTORIES, "defaultSetOfFactories");
    emptySetBindingMethod.addModifiers(STATIC);
    emptySetBindingMethod.annotate(Provides.class).setMember("type", Provides.Type.SET_VALUES);
    emptySetBindingMethod
        .body()
        .addSnippet("return %s.of();", ClassName.fromClass(ImmutableSet.class));

    FieldWriter providerField = classWriter.addField(MonitorCache.class, "monitorCache");
    providerField.addModifiers(PRIVATE, FINAL);
    providerField.setInitializer("new %s()", ClassName.fromClass(MonitorCache.class));
    MethodWriter monitorMethod = classWriter.addMethod(ProductionComponentMonitor.class, "monitor");
    monitorMethod.annotate(Provides.class);
    monitorMethod.addParameter(
        ParameterizedTypeName.create(Provider.class, ClassName.fromTypeElement(componentElement)),
        "component");
    monitorMethod.addParameter(
        ParameterizedTypeName.create(Provider.class, SET_OF_FACTORIES), "factories");
    monitorMethod.body().addSnippet("return monitorCache.monitor(component, factories);");

    return ImmutableSet.of(writer);
  }
}

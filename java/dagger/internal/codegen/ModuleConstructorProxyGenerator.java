/*
 * Copyright (C) 2018 The Dagger Authors.
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
import static dagger.internal.codegen.ModuleKind.checkIsModule;
import static dagger.internal.codegen.ModuleProxies.nonPublicNullaryConstructor;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.codegen.langmodel.DaggerElements;
import java.util.Optional;
import javax.annotation.processing.Filer;
import javax.inject.Inject;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/**
 * Generates a {@code public static} method that calls {@code new SomeModule()} for modules that
 * don't have {@linkplain ModuleProxies#nonPublicNullaryConstructor(TypeElement,
 * DaggerElements) publicly accessible constructors}.
 */
// TODO(dpb): See if this can become a SourceFileGenerator<ModuleDescriptor> instead. Doing so may
// cause ModuleProcessingStep to defer elements multiple times.
final class ModuleConstructorProxyGenerator extends SourceFileGenerator<TypeElement> {
  private final DaggerElements elements;

  @Inject
  ModuleConstructorProxyGenerator(
      Filer filer, DaggerElements elements, SourceVersion sourceVersion) {
    super(filer, elements, sourceVersion);
    this.elements = elements;
  }

  @Override
  ClassName nameGeneratedType(TypeElement moduleElement) {
    return ModuleProxies.constructorProxyTypeName(moduleElement);
  }

  @Override
  Element originatingElement(TypeElement moduleElement) {
    return moduleElement;
  }

  @Override
  Optional<TypeSpec.Builder> write(ClassName generatedTypeName, TypeElement moduleElement) {
    checkIsModule(moduleElement);
    return nonPublicNullaryConstructor(moduleElement, elements).isPresent()
        ? Optional.of(buildProxy(generatedTypeName, moduleElement))
        : Optional.empty();
  }

  private TypeSpec.Builder buildProxy(ClassName generatedTypeName, TypeElement moduleElement) {
    return classBuilder(generatedTypeName)
        .addModifiers(PUBLIC, FINAL)
        .addMethod(constructorBuilder().addModifiers(PRIVATE).build())
        .addMethod(
            methodBuilder("newInstance")
                .addModifiers(PUBLIC, STATIC)
                .returns(ClassName.get(moduleElement))
                .addStatement("return new $T()", moduleElement)
                .build());
  }
}

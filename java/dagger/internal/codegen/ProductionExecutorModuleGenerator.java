/*
 * Copyright (C) 2016 The Dagger Authors.
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

import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.STATIC;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeSpec;
import dagger.Module;
import dagger.Provides;
import dagger.producers.Production;
import dagger.producers.ProductionScope;
import dagger.producers.internal.ProductionImplementation;
import java.util.Optional;
import java.util.concurrent.Executor;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

/** Generates a producer executor module for use with production components. */
// TODO(beder): Replace this with a single class when the producers client library exists.
final class ProductionExecutorModuleGenerator extends SourceFileGenerator<TypeElement> {

  ProductionExecutorModuleGenerator(Filer filer, Elements elements) {
    super(filer, elements);
  }

  @Override
  ClassName nameGeneratedType(TypeElement componentElement) {
    return SourceFiles.generatedProductionExecutorModuleName(componentElement);
  }

  @Override
  Optional<? extends Element> getElementForErrorReporting(TypeElement componentElement) {
    return Optional.of(componentElement);
  }

  @Override
  Optional<TypeSpec.Builder> write(ClassName generatedTypeName, TypeElement componentElement) {
    return Optional.of(
        classBuilder(generatedTypeName)
            .addAnnotation(Module.class)
            .addModifiers(FINAL)
            .addMethod(
                methodBuilder("executor")
                    .returns(Executor.class)
                    .addModifiers(STATIC)
                    .addAnnotation(Provides.class)
                    .addAnnotation(ProductionScope.class)
                    .addAnnotation(ProductionImplementation.class)
                    .addParameter(
                        ParameterSpec.builder(Executor.class, "executor")
                            .addAnnotation(Production.class)
                            .build())
                    .addStatement("return executor")
                    .build()));
  }
}

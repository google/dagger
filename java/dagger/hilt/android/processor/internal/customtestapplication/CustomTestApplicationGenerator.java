/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.hilt.android.processor.internal.customtestapplication;

import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.VOLATILE;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.Processors;
import java.io.IOException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;

/** Generates an Android Application that holds the Singleton component. */
final class CustomTestApplicationGenerator {
  private static final ParameterSpec COMPONENT_MANAGER =
      ParameterSpec.builder(ClassNames.TEST_APPLICATION_COMPONENT_MANAGER, "componentManager")
          .build();

  private final ProcessingEnvironment processingEnv;
  private final CustomTestApplicationMetadata metadata;

  public CustomTestApplicationGenerator(
      ProcessingEnvironment processingEnv, CustomTestApplicationMetadata metadata) {
    this.processingEnv = processingEnv;
    this.metadata = metadata;
  }

  public void generate() throws IOException {
    TypeSpec.Builder generator =
        TypeSpec.classBuilder(metadata.appName())
            .addOriginatingElement(metadata.element())
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .superclass(metadata.baseAppName())
            .addSuperinterface(
                ParameterizedTypeName.get(ClassNames.GENERATED_COMPONENT_MANAGER, TypeName.OBJECT))
            .addSuperinterface(ClassNames.TEST_APPLICATION_COMPONENT_MANAGER_HOLDER)
            .addField(
                FieldSpec.builder(ClassName.OBJECT, "componentManagerLock", PRIVATE, FINAL)
                    .initializer("new $T()", ClassName.OBJECT)
                    .build())
            .addField(getComponentManagerField())
            .addMethod(getComponentManagerMethod())
            .addMethod(getComponentMethod());

    Processors.addGeneratedAnnotation(
        generator, processingEnv, CustomTestApplicationProcessor.class);

    JavaFile.builder(metadata.appName().packageName(), generator.build())
        .build()
        .writeTo(processingEnv.getFiler());
  }

  // Initialize this in attachBaseContext to not pull it into the main dex.
  /** private TestApplicationComponentManager componentManager; */
  private static FieldSpec getComponentManagerField() {
    return FieldSpec.builder(COMPONENT_MANAGER.type, COMPONENT_MANAGER.name, PRIVATE, VOLATILE)
        .build();
  }
  private static MethodSpec getComponentMethod() {
    return MethodSpec.methodBuilder("generatedComponent")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .returns(TypeName.OBJECT)
        .addStatement("return $N().generatedComponent()", COMPONENT_MANAGER)
        .build();
  }

  private static MethodSpec getComponentManagerMethod() {
    return MethodSpec.methodBuilder("componentManager")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .returns(ParameterizedTypeName.get(ClassNames.GENERATED_COMPONENT_MANAGER, TypeName.OBJECT))
        // This field is initialized lazily to avoid pulling the generated component into the main
        // dex. We could possibly avoid this by class loading TestComponentDataSupplier lazily
        // rather than in the TestApplicationComponentManager constructor.
        .beginControlFlow("if ($N == null)", COMPONENT_MANAGER)
        .beginControlFlow("synchronized (componentManagerLock)")
        .beginControlFlow("if ($N == null)", COMPONENT_MANAGER)
        .addStatement("$N = new $T(this)", COMPONENT_MANAGER, COMPONENT_MANAGER.type)
        .endControlFlow()
        .endControlFlow()
        .endControlFlow()
        .addStatement("return $N", COMPONENT_MANAGER)
        .build();
  }
}

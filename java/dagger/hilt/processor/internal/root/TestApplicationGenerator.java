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

package dagger.hilt.processor.internal.root;

import com.google.common.collect.ImmutableList;
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
import javax.lang.model.element.TypeElement;

/**
 * Generates an Android Application that holds the Singleton component.
 */
public final class TestApplicationGenerator {
  private static final ParameterSpec COMPONENT_MANAGER =
      ParameterSpec.builder(ClassNames.TEST_APPLICATION_COMPONENT_MANAGER, "componentManager")
          .build();

  private final ProcessingEnvironment processingEnv;
  private final TypeElement originatingElement;
  private final ClassName baseName;
  private final ClassName appName;
  private final ImmutableList<RootMetadata> rootMetadatas;

  public TestApplicationGenerator(
      ProcessingEnvironment processingEnv,
      TypeElement originatingElement,
      ClassName baseName,
      ClassName appName,
      ImmutableList<RootMetadata> rootMetadatas) {
    this.processingEnv = processingEnv;
    this.originatingElement = originatingElement;
    this.rootMetadatas = rootMetadatas;
    this.baseName = baseName;
    this.appName = appName;
  }

  public void generate() throws IOException {
    TypeSpec.Builder generator =
        TypeSpec.classBuilder(appName)
            .addOriginatingElement(originatingElement)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .superclass(baseName)
            .addSuperinterface(
                ParameterizedTypeName.get(ClassNames.COMPONENT_MANAGER, TypeName.OBJECT))
            .addSuperinterface(ClassNames.TEST_APPLICATION_COMPONENT_MANAGER_HOLDER)
            .addField(getComponentManagerField())
            .addMethod(getAttachBaseContextMethod())
            .addMethod(getComponentManagerMethod())
            .addMethod(getComponentMethod());

    Processors.addGeneratedAnnotation(
        generator, processingEnv, ClassNames.ROOT_PROCESSOR.toString());

    JavaFile.builder(appName.packageName(), generator.build())
        .build()
        .writeTo(processingEnv.getFiler());
  }

  // Initialize this in attachBaseContext to not pull it into the main dex.
  /** private TestApplicationComponentManager componentManager; */
  private FieldSpec getComponentManagerField() {
    return FieldSpec.builder(COMPONENT_MANAGER.type, COMPONENT_MANAGER.name, Modifier.PRIVATE)
        .build();
  }

  /**
   * Initializes application fields. These fields are initialized in attachBaseContext to avoid
   * potential multidexing issues.
   *
   * <pre><code>
   * {@literal @Override} protected void attachBaseContext(Context base) {
   *   super.attachBaseContext(base);
   *   componentManager = new TestApplicationComponentManager(this);
   * }
   * </code></pre>
   */
  private MethodSpec getAttachBaseContextMethod() {
    return MethodSpec.methodBuilder("attachBaseContext")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PROTECTED, Modifier.FINAL)
        .addParameter(ClassNames.CONTEXT, "base")
        .addStatement("super.attachBaseContext(base)")
        .addStatement("$N = new $T(this)", COMPONENT_MANAGER, COMPONENT_MANAGER.type)
        .build();
  }

  private MethodSpec getComponentMethod() {
    return MethodSpec.methodBuilder("generatedComponent")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .returns(TypeName.OBJECT)
        .addStatement("return $N.generatedComponent()", COMPONENT_MANAGER)
        .build();
  }

  private MethodSpec getComponentManagerMethod() {
    return MethodSpec.methodBuilder("componentManager")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .returns(TypeName.OBJECT)
        .addStatement("return $N", COMPONENT_MANAGER)
        .build();
  }
}

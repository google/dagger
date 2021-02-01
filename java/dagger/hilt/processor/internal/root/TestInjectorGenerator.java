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

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.Processors;
import java.io.IOException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;

/** Generates an entry point for a test. */
public final class TestInjectorGenerator {
  private final ProcessingEnvironment env;
  private final TestRootMetadata metadata;

  TestInjectorGenerator(ProcessingEnvironment env, TestRootMetadata metadata) {
    this.env = env;
    this.metadata = metadata;
  }

  // @GeneratedEntryPoint
  // @InstallIn(SingletonComponent.class)
  // public interface FooTest_GeneratedInjector extends TestInjector<FooTest> {}
  public void generate() throws IOException {
    TypeSpec.Builder builder =
        TypeSpec.interfaceBuilder(metadata.testInjectorName())
            .addOriginatingElement(metadata.testElement())
            .addAnnotation(Processors.getOriginatingElementAnnotation(metadata.testElement()))
            .addAnnotation(ClassNames.GENERATED_ENTRY_POINT)
            .addAnnotation(
                AnnotationSpec.builder(ClassNames.INSTALL_IN)
                    .addMember(
                        "value",
                        "$T.class",
                    ClassNames.SINGLETON_COMPONENT)
                    .build())
            .addModifiers(Modifier.PUBLIC)
            .addSuperinterface(
                ParameterizedTypeName.get(ClassNames.TEST_INJECTOR, metadata.testName()))
            .addMethod(
                MethodSpec.methodBuilder("injectTest")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .addParameter(
                        metadata.testName(),
                        Processors.upperToLowerCamel(metadata.testName().simpleName()))
                    .build());

    Processors.addGeneratedAnnotation(builder, env, getClass());

    JavaFile.builder(metadata.testInjectorName().packageName(), builder.build())
        .build()
        .writeTo(env.getFiler());
  }
}

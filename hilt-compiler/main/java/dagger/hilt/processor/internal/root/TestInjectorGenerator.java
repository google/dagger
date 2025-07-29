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

import androidx.room.compiler.processing.JavaPoetExtKt;
import androidx.room.compiler.processing.XFiler.Mode;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.Processors;
import java.io.IOException;
import javax.lang.model.element.Modifier;

/** Generates an entry point for a test. */
public final class TestInjectorGenerator {
  private final XProcessingEnv env;
  private final TestRootMetadata metadata;

  TestInjectorGenerator(XProcessingEnv env, TestRootMetadata metadata) {
    this.env = env;
    this.metadata = metadata;
  }

  // @GeneratedEntryPoint
  // @InstallIn(SingletonComponent.class)
  // public interface FooTest_GeneratedInjector {
  //   void injectTest(FooTest fooTest);
  // }
  public void generate() throws IOException {
    TypeSpec.Builder builder =
        TypeSpec.interfaceBuilder(metadata.testInjectorName())
            .addAnnotation(Processors.getOriginatingElementAnnotation(metadata.testElement()))
            .addAnnotation(ClassNames.GENERATED_ENTRY_POINT)
            .addAnnotation(
                AnnotationSpec.builder(ClassNames.INSTALL_IN)
                    .addMember("value", "$T.class", installInComponent(metadata.testElement()))
                    .build())
            .addModifiers(Modifier.PUBLIC)
            .addMethod(
                MethodSpec.methodBuilder("injectTest")
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .addParameter(
                        metadata.testName(),
                        Processors.upperToLowerCamel(metadata.testName().simpleName()))
                    .build());
    JavaPoetExtKt.addOriginatingElement(builder, metadata.testElement());

    Processors.addGeneratedAnnotation(builder, env, getClass());

    env.getFiler()
        .write(
            JavaFile.builder(metadata.testInjectorName().packageName(), builder.build()).build(),
            Mode.Isolating);
  }

  private static ClassName installInComponent(XTypeElement testElement) {
    return ClassNames.SINGLETON_COMPONENT;
  }
}

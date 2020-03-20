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

package dagger.hilt.android.processor.internal.testing;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import dagger.hilt.android.processor.internal.AndroidClassNames;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.Processors;
import java.io.IOException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;

/**
 * Generates an entry point that allows for injection of the given test application.
 */
public final class TestApplicationEntryPointGenerator {
  private final ProcessingEnvironment env;
  private final InternalTestRootMetadata metadata;

  public TestApplicationEntryPointGenerator(
      ProcessingEnvironment env, InternalTestRootMetadata metadata) {
    this.env = env;
    this.metadata = metadata;
  }

  // @GeneratedEntryPoint
  // @InstallIn(ApplicationComponent.class)
  // public interface Foo_Application_Injector {
  //   void inject(Foo_Application app);
  // }
  public ClassName generate() throws IOException {
    TypeSpec.Builder builder =
        TypeSpec.interfaceBuilder(metadata.injectorClassName())
            .addOriginatingElement(metadata.testElement())
            .addAnnotation(ClassNames.GENERATED_ENTRY_POINT)
            .addAnnotation(
                AnnotationSpec.builder(ClassNames.INSTALL_IN)
                    .addMember("value", "$T.class", AndroidClassNames.APPLICATION_COMPONENT)
                    .build())
            .addModifiers(Modifier.PUBLIC)
            .addMethod(
                MethodSpec.methodBuilder("inject")
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .addParameter(
                        metadata.appName(),
                        Processors.upperToLowerCamel(metadata.appName().simpleName()))
                    .build());

    Processors.addGeneratedAnnotation(builder, env, getClass());

    JavaFile.builder(metadata.injectorClassName().packageName(), builder.build())
        .build()
        .writeTo(env.getFiler());

    return metadata.injectorClassName();
  }
}

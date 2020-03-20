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

package dagger.hilt.android.processor.internal.testroot;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.Processors;
import java.io.IOException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

/** Generates a {@code InternalTestRoot} annotation with the given parameters. */
public final class InternalTestRootGenerator {

  private final ProcessingEnvironment processingEnv;
  private final ClassName testType;
  private final TypeElement testElement;
  private final ClassName testName;
  private final ClassName baseApplicationName;
  private final ClassName rootName;

  public InternalTestRootGenerator(
      ProcessingEnvironment processingEnv,
      ClassName testType,
      TypeElement testElement,
      ClassName baseApplicationName) {
    this.processingEnv = processingEnv;
    this.testType = testType;
    this.testElement = testElement;
    this.testName = ClassName.get(testElement);
    this.baseApplicationName = baseApplicationName;
    rootName = Processors.append(testName, "_Root");
  }

  // @Generated
  // @InternalTestRoot(
  //   testType = InternalTestRoot.Type.ROBOLECTRIC
  //   testClass = FooTest.class,
  //   applicationBaseClass = BaseApplication.class,
  // )
  // public final class FooTest_Root {}
  public void generate() throws IOException {
    TypeSpec.Builder builder =
        TypeSpec.classBuilder(rootName)
            .addOriginatingElement(testElement)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(
                AnnotationSpec.builder(ClassNames.INTERNAL_TEST_ROOT)
                    .addMember("testType", "$T", testType)
                    .addMember("testClass", "$T.class", testName)
                    .addMember("applicationBaseClass", "$T.class", baseApplicationName)
                    .build());

    Processors.addGeneratedAnnotation(builder, processingEnv, getClass());

    JavaFile.builder(rootName.packageName(), builder.build())
        .build()
        .writeTo(processingEnv.getFiler());
  }
}

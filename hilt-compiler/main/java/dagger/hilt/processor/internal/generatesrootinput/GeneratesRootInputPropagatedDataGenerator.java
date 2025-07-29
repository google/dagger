/*
 * Copyright (C) 2019 The Dagger Authors.
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

package dagger.hilt.processor.internal.generatesrootinput;

import androidx.room.compiler.processing.JavaPoetExtKt;
import androidx.room.compiler.processing.XFiler.Mode;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.Processors;

/** Generates resource files for {@link GeneratesRootInputs}. */
final class GeneratesRootInputPropagatedDataGenerator {
  private final XProcessingEnv processingEnv;
  private final XTypeElement element;

  GeneratesRootInputPropagatedDataGenerator(XProcessingEnv processingEnv, XTypeElement element) {
    this.processingEnv = processingEnv;
    this.element = element;
  }

  void generate() {
    TypeSpec.Builder generator = TypeSpec.classBuilder(Processors.getFullEnclosedName(element));

    JavaPoetExtKt.addOriginatingElement(generator, element)
        .addAnnotation(
            AnnotationSpec.builder(ClassNames.GENERATES_ROOT_INPUT_PROPAGATED_DATA)
                .addMember("value", "$T.class", element.getClassName())
                .build())
        .addJavadoc(
            "Generated class to get the list of annotations that generate input for root.\n");

    Processors.addGeneratedAnnotation(generator, processingEnv, getClass());
    JavaFile javaFile =
        JavaFile.builder(GeneratesRootInputs.AGGREGATING_PACKAGE, generator.build()).build();
    processingEnv.getFiler().write(javaFile, Mode.Isolating);
  }
}

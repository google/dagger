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

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.Processors;
import java.io.IOException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;

/** Generates resource files for {@link GeneratesRootInputs}. */
final class GeneratesRootInputPropagatedDataGenerator {
  private final ProcessingEnvironment processingEnv;
  private final Element element;

  GeneratesRootInputPropagatedDataGenerator(ProcessingEnvironment processingEnv, Element element) {
    this.processingEnv = processingEnv;
    this.element = element;
  }

  void generate() throws IOException {
    TypeSpec.Builder generator =
        TypeSpec.classBuilder(Processors.getFullEnclosedName(element))
            .addOriginatingElement(element)
            .addAnnotation(
                AnnotationSpec.builder(ClassNames.GENERATES_ROOT_INPUT_PROPAGATED_DATA)
                    .addMember("value", "$T.class", element)
                    .build())
            .addJavadoc(
                "Generated class to"
                    + "get the list of annotations that generate input for root.\n");

    Processors.addGeneratedAnnotation(generator, processingEnv, getClass());

    JavaFile.builder(GeneratesRootInputs.AGGREGATING_PACKAGE, generator.build())
        .build()
        .writeTo(processingEnv.getFiler());
  }
}

/*
 * Copyright (C) 2021 The Dagger Authors.
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
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.Processors;
import java.io.IOException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;

/** Generates an {@link dagger.hilt.internal.aggregatedroot.AggregatedRoot}. */
final class AggregatedRootGenerator {
  private final TypeElement rootElement;
  private final TypeElement originatingRootElement;
  private final TypeElement rootAnnotation;
  private final ProcessingEnvironment processingEnv;

  AggregatedRootGenerator(
      TypeElement rootElement,
      TypeElement originatingRootElement,
      TypeElement rootAnnotation,
      ProcessingEnvironment processingEnv) {
    this.rootElement = rootElement;
    this.originatingRootElement = originatingRootElement;
    this.rootAnnotation = rootAnnotation;
    this.processingEnv = processingEnv;
  }

  void generate() throws IOException {
    AnnotationSpec.Builder aggregatedRootAnnotation = AnnotationSpec.builder(
        ClassNames.AGGREGATED_ROOT)
            .addMember("root", "$S", rootElement.getQualifiedName())
            .addMember("rootPackage", "$S", ClassName.get(rootElement).packageName())
            .addMember("originatingRoot", "$S", originatingRootElement.getQualifiedName())
            .addMember("originatingRootPackage", "$S",
                ClassName.get(originatingRootElement).packageName())
            .addMember("rootAnnotation", "$T.class", rootAnnotation);
    ClassName.get(rootElement).simpleNames().forEach(
        name -> aggregatedRootAnnotation.addMember("rootSimpleNames", "$S", name));
    ClassName.get(originatingRootElement).simpleNames().forEach(
        name -> aggregatedRootAnnotation.addMember("originatingRootSimpleNames", "$S", name));
    Processors.generateAggregatingClass(
        ClassNames.AGGREGATED_ROOT_PACKAGE,
        aggregatedRootAnnotation.build(),
        rootElement,
        getClass(),
        processingEnv);
  }
}

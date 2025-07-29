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

import androidx.room.compiler.processing.XTypeElement;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.Processors;

/** Generates an {@link dagger.hilt.internal.aggregatedroot.AggregatedRoot}. */
final class AggregatedRootGenerator {
  private final XTypeElement rootElement;
  private final XTypeElement originatingRootElement;
  private final XTypeElement rootAnnotation;
  private final ClassName rootComponentName;

  AggregatedRootGenerator(
      XTypeElement rootElement,
      XTypeElement originatingRootElement,
      XTypeElement rootAnnotation,
      ClassName rootComponentName) {
    this.rootElement = rootElement;
    this.originatingRootElement = originatingRootElement;
    this.rootAnnotation = rootAnnotation;
    this.rootComponentName = rootComponentName;
  }

  void generate() {
    AnnotationSpec.Builder aggregatedRootAnnotation =
        AnnotationSpec.builder(ClassNames.AGGREGATED_ROOT)
            .addMember("root", "$S", rootElement.getQualifiedName())
            .addMember("rootPackage", "$S", rootElement.getClassName().packageName())
            .addMember("originatingRoot", "$S", originatingRootElement.getQualifiedName())
            .addMember(
                "originatingRootPackage", "$S", originatingRootElement.getClassName().packageName())
            .addMember("rootAnnotation", "$T.class", rootAnnotation.getClassName())
            .addMember("rootComponentPackage", "$S", rootComponentName.packageName());
    rootElement
        .getClassName()
        .simpleNames()
        .forEach(name -> aggregatedRootAnnotation.addMember("rootSimpleNames", "$S", name));
    originatingRootElement
        .getClassName()
        .simpleNames()
        .forEach(
            name -> aggregatedRootAnnotation.addMember("originatingRootSimpleNames", "$S", name));
    rootComponentName
        .simpleNames()
        .forEach(
            name -> aggregatedRootAnnotation.addMember("rootComponentSimpleNames", "$S", name));
    Processors.generateAggregatingClass(
        ClassNames.AGGREGATED_ROOT_PACKAGE,
        aggregatedRootAnnotation.build(),
        rootElement,
        getClass());
  }
}

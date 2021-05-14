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

package dagger.hilt.processor.internal.uninstallmodules;

import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.AnnotationSpec;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.Processors;
import java.io.IOException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;

/**
 * Generates an {@link dagger.hilt.android.internal.uninstallmodules.AggregatedUninstallModules}
 * annotation.
 */
final class AggregatedUninstallModulesGenerator {

  private final ProcessingEnvironment env;
  private final TypeElement testElement;
  private final ImmutableList<TypeElement> uninstallModuleElements;

  AggregatedUninstallModulesGenerator(
      TypeElement testElement,
      ImmutableList<TypeElement> uninstallModuleElements,
      ProcessingEnvironment env) {
    this.testElement = testElement;
    this.uninstallModuleElements = uninstallModuleElements;
    this.env = env;
  }

  void generate() throws IOException {
    Processors.generateAggregatingClass(
        ClassNames.AGGREGATED_UNINSTALL_MODULES_PACKAGE,
        aggregatedUninstallModulesAnnotation(),
        testElement,
        getClass(),
        env);
  }

  private AnnotationSpec aggregatedUninstallModulesAnnotation() {
    AnnotationSpec.Builder builder =
        AnnotationSpec.builder(ClassNames.AGGREGATED_UNINSTALL_MODULES);
    builder.addMember("test", "$S", testElement.getQualifiedName());
    uninstallModuleElements.stream()
        .map(TypeElement::getQualifiedName)
        .forEach(uninstallModule -> builder.addMember("uninstallModules", "$S", uninstallModule));
    return builder.build();
  }
}

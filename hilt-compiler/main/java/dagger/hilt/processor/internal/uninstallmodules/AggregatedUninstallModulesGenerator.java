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

import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.AnnotationSpec;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.Processors;

/**
 * Generates an {@link dagger.hilt.android.internal.uninstallmodules.AggregatedUninstallModules}
 * annotation.
 */
final class AggregatedUninstallModulesGenerator {

  private final XTypeElement testElement;
  private final ImmutableList<XTypeElement> uninstallModuleElements;

  AggregatedUninstallModulesGenerator(
      XTypeElement testElement,
      ImmutableList<XTypeElement> uninstallModuleElements) {
    this.testElement = testElement;
    this.uninstallModuleElements = uninstallModuleElements;
  }

  void generate() {
    Processors.generateAggregatingClass(
        ClassNames.AGGREGATED_UNINSTALL_MODULES_PACKAGE,
        aggregatedUninstallModulesAnnotation(),
        testElement,
        getClass());
  }

  private AnnotationSpec aggregatedUninstallModulesAnnotation() {
    AnnotationSpec.Builder builder =
        AnnotationSpec.builder(ClassNames.AGGREGATED_UNINSTALL_MODULES);
    builder.addMember("test", "$S", testElement.getQualifiedName());
    uninstallModuleElements.stream()
        .map(XTypeElement::getQualifiedName)
        .forEach(uninstallModule -> builder.addMember("uninstallModules", "$S", uninstallModule));
    return builder.build();
  }
}

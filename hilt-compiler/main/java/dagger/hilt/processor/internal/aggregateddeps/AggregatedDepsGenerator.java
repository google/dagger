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

package dagger.hilt.processor.internal.aggregateddeps;

import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.Processors;
import java.util.Optional;

/**
 * Generates the @AggregatedDeps annotated class used to pass information
 * about modules and entry points through multiple javac runs.
 */
final class AggregatedDepsGenerator {
  static final String AGGREGATING_PACKAGE = "hilt_aggregated_deps";
  private static final ClassName AGGREGATED_DEPS =
      ClassName.get("dagger.hilt.processor.internal.aggregateddeps", "AggregatedDeps");

  private final String dependencyType;
  private final XTypeElement dependency;
  private final Optional<ClassName> testName;
  private final ImmutableSet<ClassName> components;
  private final ImmutableSet<ClassName> replacedDependencies;

  AggregatedDepsGenerator(
      String dependencyType,
      XTypeElement dependency,
      Optional<ClassName> testName,
      ImmutableSet<ClassName> components,
      ImmutableSet<ClassName> replacedDependencies) {
    this.dependencyType = dependencyType;
    this.dependency = dependency;
    this.testName = testName;
    this.components = components;
    this.replacedDependencies = replacedDependencies;
  }

  void generate() {
    Processors.generateAggregatingClass(
        AGGREGATING_PACKAGE, aggregatedDepsAnnotation(), dependency, getClass());
  }

  private AnnotationSpec aggregatedDepsAnnotation() {
    AnnotationSpec.Builder annotationBuilder = AnnotationSpec.builder(AGGREGATED_DEPS);
    components.forEach(component -> annotationBuilder.addMember("components", "$S", component));
    replacedDependencies.forEach(dep -> annotationBuilder.addMember("replaces", "$S", dep));
    testName.ifPresent(test -> annotationBuilder.addMember("test", "$S", test));
    annotationBuilder.addMember(dependencyType, "$S", dependency.getQualifiedName());
    return annotationBuilder.build();
  }
}

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

import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import dagger.hilt.processor.internal.Processors;
import java.io.IOException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;

/**
 * Generates the @AggregatedDeps annotated class used to pass information
 * about modules and entry points through multiple javac runs.
 */
final class AggregatedDepsGenerator {
  private static final ClassName AGGREGATED_DEPS =
      ClassName.get("dagger.hilt.processor.internal.aggregateddeps", "AggregatedDeps");

  private final String dependencyType;
  private final TypeElement dependency;
  private final ImmutableSet<ClassName> components;
  private final ProcessingEnvironment processingEnv;

  AggregatedDepsGenerator(
      String dependencyType,
      TypeElement dependency,
      ImmutableSet<ClassName> components,
      ProcessingEnvironment processingEnv) {
    this.dependencyType = dependencyType;
    this.dependency = dependency;
    this.components = components;
    this.processingEnv = processingEnv;
  }

  void generate() throws IOException {
    ClassName name =
        ClassName.get(
            ComponentDependencies.AGGREGATING_PACKAGE,
            Processors.getFullEnclosedName(dependency) + "ModuleDeps");
    TypeSpec.Builder generator =
        TypeSpec.classBuilder(name.simpleName())
            .addOriginatingElement(dependency)
            .addAnnotation(aggregatedDepsAnnotation())
            .addJavadoc("Generated class to pass information through multiple javac runs.\n");

    Processors.addGeneratedAnnotation(generator, processingEnv, getClass());

    JavaFile.builder(name.packageName(), generator.build())
        .build()
        .writeTo(processingEnv.getFiler());
  }

  private AnnotationSpec aggregatedDepsAnnotation() {
    AnnotationSpec.Builder annotationBuilder = AnnotationSpec.builder(AGGREGATED_DEPS);
    components.forEach(component -> annotationBuilder.addMember("components", "$S", component));
    annotationBuilder.addMember(dependencyType, "$S", dependency.getQualifiedName());
    return annotationBuilder.build();
  }
}

/*
 * Copyright (C) 2022 The Dagger Authors.
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

package dagger.internal.codegen.xprocessing;

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;

import androidx.room.compiler.codegen.compat.XConverters;
import androidx.room.compiler.processing.XExecutableParameterElement;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeSpec;

// TODO(bcorso): Consider moving these methods into XProcessing library.
/** A utility class for JavaPoet types to interface with XProcessing types. */
public final class JavaPoetExt {

  /**
   * Configures the given {@link TypeSpec.Builder} so that it fully qualifies all classes nested in
   * the given {@link XTypeElement} and all classes nested within any super type of the given {@link
   * XTypeElement}.
   *
   * @see TypeSpec.Builder#avoidClashesWithNestedClasses(Class)
   */
  public static TypeSpec.Builder avoidClashesWithNestedClasses(
      TypeSpec.Builder builder, XTypeElement typeElement) {
    typeElement
        .getEnclosedTypeElements()
        .forEach(nestedTypeElement -> builder.alwaysQualify(getSimpleName(nestedTypeElement)));

    typeElement.getType().getSuperTypes().stream()
        .filter(XTypes::isDeclared)
        .map(XType::getTypeElement)
        .forEach(superType -> avoidClashesWithNestedClasses(builder, superType));

    return builder;
  }

  public static ParameterSpec toParameterSpec(XExecutableParameterElement param) {
    return ParameterSpec.builder(param.getType().getTypeName(), param.getJvmName()).build();
  }

  public static ParameterSpec toParameterSpec(
      XExecutableParameterElement parameter, XType parameterType) {
    Nullability nullability = Nullability.of(parameter);
    ImmutableSet<AnnotationSpec> typeUseNullableAnnotations =
        nullability.typeUseNullableAnnotations().stream()
            .map(XConverters::toJavaPoet)
            .map(annotation -> AnnotationSpec.builder(annotation).build())
            .collect(toImmutableSet());
    ImmutableSet<AnnotationSpec> nonTypeUseNullableAnnotations =
        nullability.nonTypeUseNullableAnnotations().stream()
            .map(XConverters::toJavaPoet)
            .map(annotation -> AnnotationSpec.builder(annotation).build())
            .collect(toImmutableSet());
    return ParameterSpec.builder(
            parameterType.getTypeName().annotated(typeUseNullableAnnotations.asList()),
            parameter.getJvmName())
        .addAnnotations(nonTypeUseNullableAnnotations)
        .build();
  }

  private JavaPoetExt() {}
}

/*
 * Copyright (C) 2025 The Dagger Authors.
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

import static androidx.room.compiler.codegen.compat.XConverters.toJavaPoet;
import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import androidx.room.compiler.codegen.XAnnotationSpec;
import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XTypeName;
import androidx.room.compiler.codegen.compat.XConverters;
import androidx.room.compiler.processing.XType;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import dagger.internal.codegen.compileroption.CompilerOptions;

/** Utility class for creating {@link XTypeName}s with {@code @Nullable} type-use annotations. */
public final class NullableTypeNames {

  /**
   * Returns a {@link NullableTypeName} with type-use nullable annotations copied from the given
   * {@code nullability}.
   */
  public static XTypeName appendTypeUseNullable(
      XTypeName typeName, XAnnotationSpec nullable, CompilerOptions compilerOptions) {
    checkIsNullable(nullable);
    return typeName;
  }

  private static void checkIsNullable(XAnnotationSpec annotation) {
    TypeName typeName = toJavaPoet(annotation).type;
    checkState(typeName instanceof ClassName, "Type name %s is not a ClassName.", typeName);
    checkState(
        ((ClassName) typeName).simpleName().contentEquals("Nullable"),
        "Type name %s is not a Nullable type.",
        typeName);
  }

  /**
   * Returns a {@link NullableTypeName} with type-use nullable annotations copied from the given
   * {@code nullability}.
   *
   * <p>Note: This method only applies @Nullable to the outer type. It does not apply nullable to
   * any type arguments. Prefer {@link #asNullableTypeName(XType, CompilerOptions)} instead.
   */
  public static XTypeName asNullableTypeName(
      XTypeName typeName, Nullability nullability, CompilerOptions compilerOptions) {
    return typeName;
  }

  /**
   * Returns a {@link NullableTypeName} with type-use nullable annotations copied from the given
   * {@code type}.
   */
  public static XTypeName asNullableTypeName(XType type, CompilerOptions compilerOptions) {
    return type.asTypeName();
  }

  private static TypeName asNullableJavaTypeName(XType type, TypeName typeName) {
    if (typeName instanceof ParameterizedTypeName) {
      ParameterizedTypeName parameterizedTypeName = (ParameterizedTypeName) typeName;
      if (type.getTypeArguments().size() != parameterizedTypeName.typeArguments.size()) {
        throw new IllegalStateException(
            String.format(
                "%s has %s type arguments but %s has %s type arguments",
                type,
                type.getTypeArguments().size(),
                parameterizedTypeName,
                parameterizedTypeName.typeArguments.size()));
      }
      TypeName[] typeArguments = new TypeName[parameterizedTypeName.typeArguments.size()];
      for (int i = 0; i < parameterizedTypeName.typeArguments.size(); i++) {
        typeArguments[i] =
            asNullableJavaTypeName(
                type.getTypeArguments().get(i), parameterizedTypeName.typeArguments.get(i));
      }
      typeName = ParameterizedTypeName.get(parameterizedTypeName.rawType, typeArguments);
    }
    return typeName.annotated(
        getNullableAnnotations(type).stream()
            .map(XConverters::toJavaPoet)
            .map(AnnotationSpec::builder)
            .map(AnnotationSpec.Builder::build)
            .collect(toImmutableList()));
  }

  private static ImmutableSet<XClassName> getNullableAnnotations(XType type) {
    return type.getAllAnnotations().stream()
        .map(XAnnotations::asClassName)
        .filter(annotation -> annotation.getSimpleName().contentEquals("Nullable"))
        .collect(toImmutableSet());
  }

  private NullableTypeNames() {}
}

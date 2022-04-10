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

import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static androidx.room.compiler.processing.compat.XConverters.toXProcessing;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getOnlyElement;

import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;

// TODO(bcorso): Consider moving these methods into XProcessing library.
/** A utility class for {@link XProcessingEnvs} helper methods. */
public final class XProcessingEnvs {

  /** Returns a new unbounded wildcard type argument, i.e. {@code <?>}. */
  public static XType getUnboundedWildcardType(XProcessingEnv processingEnv) {
    return toXProcessing(
        toJavac(processingEnv).getTypeUtils().getWildcardType(null, null), // ALLOW_TYPES_ELEMENTS
        processingEnv);
  }

  /**
   * Returns {@code type}'s single type argument.
   *
   * <p>For example, if {@code type} is {@code List<Number>} this will return {@code Number}.
   *
   * @throws IllegalArgumentException if {@code type} is not a declared type or has zero or more
   *     than one type arguments.
   */
  public static XType unwrapType(XType type) {
    XType unwrapped = unwrapTypeOrDefault(type, null);
    checkArgument(unwrapped != null, "%s is a raw type", type);
    return unwrapped;
  }

  /**
   * Returns {@code type}'s single type argument, if one exists, or {@link Object} if not.
   *
   * <p>For example, if {@code type} is {@code List<Number>} this will return {@code Number}.
   *
   * @throws IllegalArgumentException if {@code type} is not a declared type or has more than one
   *     type argument.
   */
  public static XType unwrapTypeOrObject(XType type, XProcessingEnv processingEnv) {
    return unwrapTypeOrDefault(type, processingEnv.requireType(TypeName.OBJECT));
  }

  private static XType unwrapTypeOrDefault(XType type, XType defaultType) {
    XTypeElement typeElement = type.getTypeElement();
    checkArgument(
        !typeElement.getType().getTypeArguments().isEmpty(),
        "%s does not have a type parameter",
        typeElement.getQualifiedName());
    return getOnlyElement(type.getTypeArguments(), defaultType);
  }

  /**
   * Returns {@code type}'s single type argument wrapped in {@code wrappingClass}.
   *
   * <p>For example, if {@code type} is {@code List<Number>} and {@code wrappingClass} is {@code
   * Set.class}, this will return {@code Set<Number>}.
   *
   * <p>If {@code type} has no type parameters, returns a {@link XType} for {@code wrappingClass} as
   * a raw type.
   *
   * @throws IllegalArgumentException if {@code} has more than one type argument.
   */
  public static XType rewrapType(
      XType type, ClassName wrappingClassName, XProcessingEnv processingEnv) {
    XTypeElement wrappingType = processingEnv.requireTypeElement(wrappingClassName.canonicalName());
    switch (type.getTypeArguments().size()) {
      case 0:
        return processingEnv.getDeclaredType(wrappingType);
      case 1:
        return processingEnv.getDeclaredType(wrappingType, getOnlyElement(type.getTypeArguments()));
      default:
        throw new IllegalArgumentException(type + " has more than 1 type argument");
    }
  }

  /** Returns {@code true} if and only if the {@code type1} is assignable to {@code type2}. */
  public static boolean isAssignable(XType type1, XType type2, XProcessingEnv processingEnv) {
    return toJavac(processingEnv)
        .getTypeUtils() // ALLOW_TYPES_ELEMENTS
        .isAssignable(toJavac(type1), toJavac(type2));
  }

  /** Returns the erasure of the given type. */
  public static XType erasure(XType type, XProcessingEnv processingEnv) {
    return toXProcessing(
        toJavac(processingEnv).getTypeUtils().erasure(toJavac(type)), // ALLOW_TYPES_ELEMENTS
        processingEnv);
  }

  /** Returns {@code true} if {@code type1} is a subtype of {@code type2}. */
  public static boolean isSubtype(XType type1, XType type2, XProcessingEnv processingEnv) {
    return toJavac(processingEnv)
        .getTypeUtils() // ALLOW_TYPES_ELEMENTS
        .isSubtype(toJavac(type1), toJavac(type2));
  }

  /** Returns the type this method is enclosed in. */
  public static XType wrapType(ClassName wrapper, XType type, XProcessingEnv processingEnv) {
    return processingEnv.getDeclaredType(processingEnv.requireTypeElement(wrapper), type);
  }

  private XProcessingEnvs() {}
}

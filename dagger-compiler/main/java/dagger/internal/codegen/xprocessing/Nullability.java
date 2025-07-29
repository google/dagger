/*
 * Copyright (C) 2023 The Dagger Authors.
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

import static androidx.room.compiler.processing.XElementKt.isMethod;
import static androidx.room.compiler.processing.XElementKt.isVariableElement;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.xprocessing.XElements.asMethod;
import static dagger.internal.codegen.xprocessing.XElements.asVariable;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XTypeName;
import androidx.room.compiler.codegen.compat.XConverters;
import androidx.room.compiler.processing.XAnnotated;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XNullability;
import androidx.room.compiler.processing.XType;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import java.util.Optional;

/**
 * Contains information about the nullability of an element or type.
 *
 * <p>Note that an element can be nullable if either:
 *
 * <ul>
 *   <li>The element is annotated with {@code Nullable} or
 *   <li>the associated kotlin type is nullable (i.e. {@code T?} types in Kotlin source).
 * </ul>
 */
@AutoValue
public abstract class Nullability {
  /** A constant that can represent any non-null element. */
  public static final Nullability NOT_NULLABLE =
      new AutoValue_Nullability(ImmutableSet.of(), ImmutableSet.of(), false);

  public static Nullability of(XElement element) {
    ImmutableSet<XClassName> nonTypeUseNullableAnnotations = getNullableAnnotations(element);
    Optional<XType> type = getType(element);
    ImmutableSet<XClassName> typeUseNullableAnnotations =
    ImmutableSet.of();
    boolean isKotlinTypeNullable =
        // Note: Technically, it isn't possible for Java sources to have nullable types like in
        // Kotlin sources, but for some reason KSP treats certain types as nullable if they have a
        // specific @Nullable (TYPE_USE target) annotation. Thus, to avoid inconsistencies with
        // KAPT, just ignore type nullability for elements in java sources.
        !element.getClosestMemberContainer().isFromJava()
            && type.isPresent()
            && type.get().getNullability() == XNullability.NULLABLE;
    return new AutoValue_Nullability(
        nonTypeUseNullableAnnotations,
        // Filter type use annotations that are also found on the element as non-type use
        // annotations. This prevents them from being applied twice in some scenarios and just
        // defaults to using them in the way before Dagger supported type use annotations.
        Sets.difference(typeUseNullableAnnotations, nonTypeUseNullableAnnotations).immutableCopy(),
        isKotlinTypeNullable);
  }

  static XTypeName getTypeNameWithNullableAnnotations(XType type) {
    return type.asTypeName();
  }

  private static TypeName getAnnotatedTypeName(XType type, TypeName typeName) {
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
            getAnnotatedTypeName(
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

  static ImmutableSet<XClassName> getNullableAnnotations(XAnnotated annotated) {
    return annotated.getAllAnnotations().stream()
        .map(XAnnotations::asClassName)
        .filter(annotation -> annotation.getSimpleName().contentEquals("Nullable"))
        .collect(toImmutableSet());
  }

  private static Optional<XType> getType(XElement element) {
    if (isMethod(element)) {
      return Optional.of(asMethod(element).getReturnType());
    } else if (isVariableElement(element)) {
      return Optional.of(asVariable(element).getType());
    }
    return Optional.empty();
  }

  public abstract ImmutableSet<XClassName> nonTypeUseNullableAnnotations();

  public abstract ImmutableSet<XClassName> typeUseNullableAnnotations();

  /**
   * Returns {@code true} if the element's type is a Kotlin nullable type, e.g. {@code Foo?}.
   *
   * <p>Note that this method ignores any {@code @Nullable} type annotations and only looks for
   * explicit {@code ?} usages on kotlin types.
   */
  public abstract boolean isKotlinTypeNullable();

  public ImmutableSet<XClassName> nullableAnnotations() {
    return ImmutableSet.<XClassName>builder()
        .addAll(nonTypeUseNullableAnnotations())
        .addAll(typeUseNullableAnnotations()).build();
  }

  public final boolean isNullable() {
    return isKotlinTypeNullable() || !nullableAnnotations().isEmpty();
  }

  Nullability() {}
}

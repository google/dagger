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

package dagger.internal.codegen.binding;

import static androidx.room.compiler.processing.XElementKt.isMethod;
import static androidx.room.compiler.processing.XElementKt.isVariableElement;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.xprocessing.XElements.asMethod;
import static dagger.internal.codegen.xprocessing.XElements.asVariable;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XNullability;
import androidx.room.compiler.processing.XType;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.internal.codegen.xprocessing.XAnnotations;
import java.util.stream.Stream;

/**
 * Contains information about the nullability of an element.
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
      new AutoValue_Nullability(false, ImmutableSet.of());

  public static Nullability of(XElement element) {
    return new AutoValue_Nullability(
        /* isKotlinTypeNullable= */ isKotlinTypeNullable(element),
        /* nullableAnnotations= */ getNullableAnnotations(element.getAllAnnotations().stream()));
  }

  private static ImmutableSet<ClassName> getNullableAnnotations(Stream<XAnnotation> annotations) {
    return annotations
        .map(XAnnotations::getClassName)
        .filter(annotation -> annotation.simpleName().contentEquals("Nullable"))
        .collect(toImmutableSet());
  }

  /**
   * Returns {@code true} if the element's type is a Kotlin nullable type, e.g. {@code Foo?}.
   *
   * <p>Note that this method ignores any {@code @Nullable} type annotations and only looks for
   * explicit {@code ?} usages on kotlin types.
   */
  private static boolean isKotlinTypeNullable(XElement element) {
    if (element.getClosestMemberContainer().isFromJava()) {
      // Note: Technically, it isn't possible for Java sources to have nullable types like in Kotlin
      // sources, but for some reason KSP treats certain types as nullable if they have a
      // specific @Nullable (TYPE_USE target) annotation. Thus, to avoid inconsistencies with KAPT,
      // just return false if this element is from a java source.
      return false;
    } else if (isMethod(element)) {
      return isKotlinTypeNullable(asMethod(element).getReturnType());
    } else if (isVariableElement(element)) {
      return isKotlinTypeNullable(asVariable(element).getType());
    } else {
      return false;
    }
  }

  private static boolean isKotlinTypeNullable(XType type) {
    return type.getNullability() == XNullability.NULLABLE;
  }

  public abstract boolean isKotlinTypeNullable();

  public abstract ImmutableSet<ClassName> nullableAnnotations();

  public final boolean isNullable() {
    return isKotlinTypeNullable() || !nullableAnnotations().isEmpty();
  }

  Nullability() {}
}

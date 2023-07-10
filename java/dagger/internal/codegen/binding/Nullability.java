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
import static dagger.internal.codegen.xprocessing.XAnnotations.getClassName;
import static dagger.internal.codegen.xprocessing.XElements.asMethod;
import static dagger.internal.codegen.xprocessing.XElements.asVariable;
import static dagger.internal.codegen.xprocessing.XElements.isFromJavaSource;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XNullability;
import androidx.room.compiler.processing.XType;
import com.google.auto.value.AutoValue;
import java.util.Optional;

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
  public static final Nullability NOT_NULLABLE = new AutoValue_Nullability(false, Optional.empty());

  public static Nullability of(XElement element) {
    Optional<XAnnotation> nullableAnnotation = getNullableAnnotation(element);
    boolean isNullable = isKotlinTypeNullable(element) || nullableAnnotation.isPresent();
    return isNullable ? new AutoValue_Nullability(isNullable, nullableAnnotation) : NOT_NULLABLE;
  }

  /**
   * Returns {@code true} if the element's type is a Kotlin nullable type, e.g. {@code Foo?}.
   *
   * <p>Note that this method ignores any {@code @Nullable} type annotations and only looks for
   * explicit {@code ?} usages on kotlin types.
   */
  private static boolean isKotlinTypeNullable(XElement element) {
    if (isFromJavaSource(element)) {
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

  /** Returns the first type that specifies this' nullability, or empty if none. */
  private static Optional<XAnnotation> getNullableAnnotation(XElement element) {
    return element.getAllAnnotations().stream()
        .filter(annotation -> getClassName(annotation).simpleName().contentEquals("Nullable"))
        .findFirst();
  }

  public abstract boolean isNullable();

  public abstract Optional<XAnnotation> nullableAnnotation();

  Nullability() {}
}

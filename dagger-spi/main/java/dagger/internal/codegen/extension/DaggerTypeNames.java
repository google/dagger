/*
 * Copyright (C) 2026 The Dagger Authors.
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

package dagger.internal.codegen.extension;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static java.util.stream.Collectors.joining;

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.WildcardTypeName;

/** Utility for producing stable string representations of {@link TypeName}s. */
public final class DaggerTypeNames {

  /**
   * Returns a string representation of {@link TypeName} that is stable and independent of the
   * annotation processing backend.
   */
  public static String toStableString(TypeName typeName) {
    if (typeName instanceof ClassName) {
      return ((ClassName) typeName).canonicalName();
    } else if (typeName instanceof ArrayTypeName) {
      return String.format("%s[]", toStableString(((ArrayTypeName) typeName).componentType));
    } else if (typeName instanceof ParameterizedTypeName) {
      ParameterizedTypeName parameterizedTypeName = (ParameterizedTypeName) typeName;
      return String.format(
          "%s<%s>",
          parameterizedTypeName.rawType,
          parameterizedTypeName.typeArguments.stream()
              .map(DaggerTypeNames::toStableString)
              .collect(joining(",")));
    } else if (typeName instanceof WildcardTypeName) {
      WildcardTypeName wildcardTypeName = (WildcardTypeName) typeName;
      // Wildcard types have exactly 1 upper bound.
      TypeName upperBound = getOnlyElement(wildcardTypeName.upperBounds);
      if (!upperBound.equals(TypeName.OBJECT)) {
        // Wildcards with non-Object upper bounds can't have lower bounds.
        checkState(wildcardTypeName.lowerBounds.isEmpty());
        return String.format("? extends %s", toStableString(upperBound));
      }
      if (!wildcardTypeName.lowerBounds.isEmpty()) {
        // Wildcard types can have at most 1 lower bound.
        TypeName lowerBound = getOnlyElement(wildcardTypeName.lowerBounds);
        return String.format("? super %s", toStableString(lowerBound));
      }
      // If the upper bound is Object and there is no lower bound then just use "?".
      return "?";
    } else if (typeName instanceof TypeVariableName) {
      return ((TypeVariableName) typeName).name;
    } else {
      // For all other types (e.g. primitive types) just use the TypeName's toString()
      return typeName.toString();
    }
  }

  private DaggerTypeNames() {}
}

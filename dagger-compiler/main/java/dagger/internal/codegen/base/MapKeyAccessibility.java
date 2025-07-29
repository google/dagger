/*
 * Copyright (C) 2017 The Dagger Authors.
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

package dagger.internal.codegen.base;

import static dagger.internal.codegen.xprocessing.Accessibility.isTypeAccessibleFrom;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XAnnotationValue;
import androidx.room.compiler.processing.XType;
import dagger.internal.codegen.xprocessing.Accessibility;
import java.util.List;
import java.util.function.Predicate;

/** Utility class for checking the visibility of an annotation. */
public final class MapKeyAccessibility {

  private MapKeyAccessibility() {}

  private static boolean checkAnnotation(
      XAnnotation annotation, Predicate<XType> accessibilityChecker) {
    return checkValues(annotation.getAnnotationValues(), accessibilityChecker);
  }

  private static boolean checkValues(
      List<XAnnotationValue> values, Predicate<XType> accessibilityChecker) {
    return values.stream().allMatch(value -> checkValue(value, accessibilityChecker));
  }

  private static boolean checkValue(XAnnotationValue value, Predicate<XType> accessibilityChecker) {
    if (value.hasListValue()) {
      return checkValues(value.asAnnotationValueList(), accessibilityChecker);
    } else if (value.hasAnnotationValue()) {
      return checkAnnotation(value.asAnnotation(), accessibilityChecker);
    } else if (value.hasEnumValue()) {
      return accessibilityChecker.test(value.asEnum().getEnclosingElement().getType());
    } else if (value.hasTypeValue()) {
      return accessibilityChecker.test(value.asType());
    } else {
      return true;
    }
  }

  public static boolean isMapKeyAccessibleFrom(XAnnotation annotation, String accessingPackage) {
    return checkAnnotation(annotation, type -> isTypeAccessibleFrom(type, accessingPackage));
  }

  public static boolean isMapKeyPubliclyAccessible(XAnnotation annotation) {
    return checkAnnotation(annotation, Accessibility::isTypePubliclyAccessible);
  }
}

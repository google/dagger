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

package dagger.internal.codegen;

import static dagger.internal.codegen.langmodel.Accessibility.isTypeAccessibleFrom;

import dagger.internal.codegen.langmodel.Accessibility;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;

final class MapKeyAccessibility extends SimpleAnnotationValueVisitor8<Boolean, Void> {
  private final Predicate<TypeMirror> accessibilityChecker;

  private MapKeyAccessibility(Predicate<TypeMirror> accessibilityChecker) {
    this.accessibilityChecker = accessibilityChecker;
  }

  @Override
  public Boolean visitAnnotation(AnnotationMirror annotation, Void aVoid) {
    // The annotation type is not checked, as the generated code will refer to the @AutoAnnotation
    // generated type which is always public
    return visitValues(annotation.getElementValues().values());
  }

  @Override
  public Boolean visitArray(List<? extends AnnotationValue> values, Void aVoid) {
    return visitValues(values);
  }

  private boolean visitValues(Collection<? extends AnnotationValue> values) {
    return values.stream().allMatch(value -> value.accept(this, null));
  }

  @Override
  public Boolean visitEnumConstant(VariableElement enumConstant, Void aVoid) {
    return accessibilityChecker.test(enumConstant.getEnclosingElement().asType());
  }

  @Override
  public Boolean visitType(TypeMirror type, Void aVoid) {
    return accessibilityChecker.test(type);
  }

  @Override
  protected Boolean defaultAction(Object o, Void aVoid) {
    return true;
  }

  static boolean isMapKeyAccessibleFrom(AnnotationMirror annotation, String accessingPackage) {
    return new MapKeyAccessibility(type -> isTypeAccessibleFrom(type, accessingPackage))
        .visitAnnotation(annotation, null);
  }

  static boolean isMapKeyPubliclyAccessible(AnnotationMirror annotation) {
    return new MapKeyAccessibility(Accessibility::isTypePubliclyAccessible)
        .visitAnnotation(annotation, null);
  }
}

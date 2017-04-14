/*
 * Copyright (C) 2016 The Dagger Authors.
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

import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.AnnotationValueVisitor;
import javax.lang.model.type.TypeMirror;

/** An {@link AnnotationValue} that contains a {@link TypeMirror}. */
final class SimpleTypeAnnotationValue implements AnnotationValue {
  private final TypeMirror value;

  SimpleTypeAnnotationValue(TypeMirror value) {
    this.value = value;
  }

  @Override
  public TypeMirror getValue() {
    return value;
  }

  @Override
  public String toString() {
    return value + ".class";
  }

  @Override
  public <R, P> R accept(AnnotationValueVisitor<R, P> visitor, P parameter) {
    return visitor.visitType(getValue(), parameter);
  }
}

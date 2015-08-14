/*
 * Copyright (C) 2014 Google, Inc.
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
package dagger2.internal.codegen;

import dagger2.MapKey;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import static dagger2.internal.codegen.ErrorMessages.MAPKEY_WITHOUT_FIELDS;

/**
 * A {@link Validator} for {@link MapKey} Annotation.
 *
 * @author Chenying Hou
 * @since 2.0
 */
final class MapKeyValidator implements Validator<Element> {
  @Override
  public ValidationReport<Element> validate(Element element) {
    ValidationReport.Builder<Element> builder = ValidationReport.Builder.about(element);
    if (((TypeElement) element).getEnclosedElements().isEmpty()) {
      builder.addItem(MAPKEY_WITHOUT_FIELDS, element);
    }
    return builder.build();
  }
}

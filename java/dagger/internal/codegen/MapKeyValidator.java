/*
 * Copyright (C) 2014 The Dagger Authors.
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

import static dagger.internal.codegen.ErrorMessages.MAPKEY_WITHOUT_MEMBERS;
import static dagger.internal.codegen.ErrorMessages.UNWRAPPED_MAP_KEY_WITH_ARRAY_MEMBER;
import static dagger.internal.codegen.ErrorMessages.UNWRAPPED_MAP_KEY_WITH_TOO_MANY_MEMBERS;
import static javax.lang.model.util.ElementFilter.methodsIn;

import dagger.MapKey;
import java.util.List;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Elements;

/**
 * A validator for {@link MapKey} annotations.
 */
// TODO(dpb,gak): Should unwrapped MapKeys be required to have their single member be named "value"?
final class MapKeyValidator {
  private final Elements elements;

  @Inject
  MapKeyValidator(Elements elements) {
    this.elements = elements;
  }

  ValidationReport<Element> validate(Element element) {
    ValidationReport.Builder<Element> builder = ValidationReport.about(element);
    List<ExecutableElement> members = methodsIn(((TypeElement) element).getEnclosedElements());
    if (members.isEmpty()) {
      builder.addError(MAPKEY_WITHOUT_MEMBERS, element);
    } else if (element.getAnnotation(MapKey.class).unwrapValue()) {
      if (members.size() > 1) {
        builder.addError(UNWRAPPED_MAP_KEY_WITH_TOO_MANY_MEMBERS, element);
      } else if (members.get(0).getReturnType().getKind() == TypeKind.ARRAY) {
        builder.addError(UNWRAPPED_MAP_KEY_WITH_ARRAY_MEMBER, element);
      }
    } else if (autoAnnotationIsMissing()) {
      builder.addError(
          "@AutoAnnotation is a necessary dependency if @MapKey(unwrapValue = false). Add a "
              + "dependency on com.google.auto.value:auto-value:<current version>");
    }
    return builder.build();
  }

  private boolean autoAnnotationIsMissing() {
    return elements.getTypeElement("com.google.auto.value.AutoAnnotation") == null;
  }
}

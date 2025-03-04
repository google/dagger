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

package dagger.internal.codegen.validation;

import androidx.room.compiler.processing.XAnnotationKt;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import androidx.room.compiler.processing.XTypeKt;
import dagger.MapKey;
import dagger.internal.codegen.xprocessing.XTypeNames;
import java.util.List;
import javax.inject.Inject;

/** A validator for {@link MapKey} annotations. */
// TODO(dpb,gak): Should unwrapped MapKeys be required to have their single member be named "value"?
public final class MapKeyValidator {
  private final XProcessingEnv processingEnv;

  @Inject
  MapKeyValidator(XProcessingEnv processingEnv) {
    this.processingEnv = processingEnv;
  }

  public ValidationReport validate(XTypeElement element) {
    ValidationReport.Builder builder = ValidationReport.about(element);
    List<XMethodElement> members = element.getDeclaredMethods();
    if (members.isEmpty()) {
      builder.addError("Map key annotations must have members", element);
    } else if (XAnnotationKt.get(
        element.getAnnotation(XTypeNames.MAP_KEY), "unwrapValue", Boolean.class)) {
      if (members.size() > 1) {
        builder.addError(
            "Map key annotations with unwrapped values must have exactly one member", element);
      } else if (XTypeKt.isArray(members.get(0).getReturnType())) {
        builder.addError("Map key annotations with unwrapped values cannot use arrays", element);
      }
    } else if (autoAnnotationIsMissing()) {
      builder.addError(
          "@AutoAnnotation is a necessary dependency if @MapKey(unwrapValue = false). Add a "
              + "dependency for the annotation, "
              + "\"com.google.auto.value:auto-value-annotations:<current version>\", "
              + "and the annotation processor, "
              + "\"com.google.auto.value:auto-value:<current version>\"");
    }
    return builder.build();
  }

  private boolean autoAnnotationIsMissing() {
    return processingEnv.findTypeElement("com.google.auto.value.AutoAnnotation") == null;
  }
}

/*
 * Copyright (C) 2019 The Dagger Authors.
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

package dagger.internal.codegen.kotlin;

import static dagger.internal.codegen.xprocessing.XElements.closestEnclosingTypeElement;

import androidx.room3.compiler.processing.XElement;
import androidx.room3.compiler.processing.XPropertyElement;
import dagger.internal.codegen.xprocessing.XTypeNames;
import javax.inject.Inject;

/** Utility class for interacting with Kotlin Metadata. */
public final class KotlinMetadataUtil {
  @Inject
  KotlinMetadataUtil() {}

  /**
   * Returns {@code true} if this element has the Kotlin Metadata annotation or if it is enclosed in
   * an element that does.
   */
  public boolean hasMetadata(XElement element) {
    return closestEnclosingTypeElement(element).hasAnnotation(XTypeNames.KOTLIN_METADATA);
  }

  /**
   * Returns {@code true} if the synthetic method for annotations is missing. This can occur when
   * the Kotlin metadata of the property reports that it contains a synthetic method for annotations
   * but such method is not found since it is synthetic and ignored by the processor.
   */
  public boolean isMissingSyntheticAnnotationMethod(XPropertyElement propertyElement) {
    return propertyElement.hasInaccessibleAnnotations();
  }
}

/*
 * Copyright (C) 2022 The Dagger Authors.
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

package dagger.internal.codegen.xprocessing;

import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;

import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.squareup.javapoet.TypeSpec;

// TODO(bcorso): Consider moving these methods into XProcessing library.
/** A utility class for JavaPoet types to interface with XProcessing types. */
public final class JavaPoetExt {

  /**
   * Configures the given {@link TypeSpec.Builder} so that it fully qualifies all classes nested in
   * the given {@link XTypeElement} and all classes nested within any super type of the given {@link
   * XTypeElement}.
   *
   * @see TypeSpec.Builder#avoidClashesWithNestedClasses(Class)
   */
  public static TypeSpec.Builder avoidClashesWithNestedClasses(
      TypeSpec.Builder builder, XTypeElement typeElement) {
    typeElement
        .getEnclosedTypeElements()
        .forEach(nestedTypeElement -> builder.alwaysQualify(getSimpleName(nestedTypeElement)));

    typeElement.getType().getSuperTypes().stream()
        .filter(XTypes::isDeclared)
        .map(XType::getTypeElement)
        .forEach(superType -> avoidClashesWithNestedClasses(builder, superType));

    return builder;
  }

  private JavaPoetExt() {}
}

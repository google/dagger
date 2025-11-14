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

package dagger.internal.codegen.validation;

import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static dagger.internal.codegen.xprocessing.XTypeElements.getAllUnimplementedMethods;
import static javax.lang.model.SourceVersion.isKeyword;

import androidx.room3.compiler.processing.XElement;
import androidx.room3.compiler.processing.XProcessingEnv;
import androidx.room3.compiler.processing.XTypeElement;
import dagger.internal.codegen.base.ValidationReport;
import dagger.internal.codegen.kotlin.KotlinMetadataUtil;
import javax.inject.Inject;

/** Class to validate that an element does not have a name that is a Java keyword. */
final class KeywordValidator {
  private final XProcessingEnv processingEnv;
  private final KotlinMetadataUtil metadataUtil;

  @Inject
  KeywordValidator(XProcessingEnv processingEnv, KotlinMetadataUtil metadataUtil) {
    this.processingEnv = processingEnv;
    this.metadataUtil = metadataUtil;
  }

  /**
   * Adds an error if the given element has a name that is a Java keyword.
   *
   * <p>This is not allowed because Dagger currently generates Java code for KSP.
   */
  static void validateNoJavaKeyword(XElement element, ValidationReport.Builder reportBuilder) {
    String elementName = getSimpleName(element);
    if (isKeyword(elementName)) {
      reportBuilder.addError(javaKeywordErrorMessage(elementName), element);
    }
  }

  /**
   * Adds an error if the given name is a Java keyword.
   *
   * <p>This is not allowed because Dagger currently generates Java code for KSP.
   */
  static void validateNoJavaKeyword(String name, ValidationReport.Builder reportBuilder) {
    if (isKeyword(name)) {
      reportBuilder.addError(javaKeywordErrorMessage(name));
    }
  }

  void validateMethodsName(XTypeElement typeElement, ValidationReport.Builder reportBuilder) {
    switch (processingEnv.getBackend()) {
      case JAVAC:
        if (metadataUtil.hasMetadata(typeElement)) {
      metadataUtil
          .getAllMethodNamesBySignature(typeElement)
          .forEach((signature, name) -> validateNoJavaKeyword(name, reportBuilder));
        }
        break;
      case KSP:
        getAllUnimplementedMethods(typeElement)
            .forEach(method -> validateNoJavaKeyword(method, reportBuilder));
        break;
    }
  }

  private static String javaKeywordErrorMessage(String name) {
    return String.format(
        "The name '%s' cannot be used because it is a Java keyword."
            + " Please use a different name.",
        name);
  }
}

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

package dagger.internal.codegen.base;

import static androidx.room3.compiler.processing.XElementKt.isField;
import static androidx.room3.compiler.processing.XElementKt.isMethod;
import static androidx.room3.compiler.processing.XElementKt.isMethodParameter;
import static androidx.room3.compiler.processing.XElementKt.isTypeElement;
import static dagger.internal.codegen.xprocessing.XElements.asExecutable;
import static dagger.internal.codegen.xprocessing.XElements.asField;
import static dagger.internal.codegen.xprocessing.XElements.asMethod;
import static dagger.internal.codegen.xprocessing.XElements.asMethodParameter;
import static dagger.internal.codegen.xprocessing.XElements.asTypeElement;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static dagger.internal.codegen.xprocessing.XElements.isExecutable;
import static dagger.internal.codegen.xprocessing.XTypes.isPrimitive;
import static javax.lang.model.SourceVersion.isKeyword;

import androidx.room3.compiler.processing.XElement;
import androidx.room3.compiler.processing.XType;
import androidx.room3.compiler.processing.XTypeElement;
import com.google.common.base.Splitter;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;

/** Class to validate that an element does not have a name that is a keyword. */
public final class KeywordValidator {

  @Inject
  KeywordValidator() {}

  /**
   * Validates that the given element does not have a name that is a Java keyword.
   *
   * <p>This is not allowed because Dagger currently generates Java code for KSP.
   *
   * @param element the element to validate
   * @return a validation report containing any errors found
   */
  ValidationReport validateJavaKeyword(XElement element) {
    ValidationReport.Builder report = ValidationReport.about(element);
    if (element == null) {
      return report.build();
    }
    if (isTypeElement(element)) {
      keywordFromName(getSimpleName(element))
          .ifPresent(keyword -> report.addError(javaKeywordErrorMessage(keyword), element));
      validatePackageName(asTypeElement(element), report);
      // For KAPT We need to validate the Kotlin metadata methods name for type elements.
      // TODO(emjich): Re-enable this validation once we figure out how to avoid false positives.
      // As of now, we are seeing false positives for this validation because KAPT stubs do not
      // contain annotation information and we cannot identify which methods to validate for Dagger
      // annotations.
    } else if (isExecutable(element)) {
      if (isMethod(element)) {
        // Validate the method name.
        keywordFromName(getSimpleName(element))
            .ifPresent(keyword -> report.addError(javaKeywordErrorMessage(keyword), element));
        // Validate the method return type.
        validateJavaKeywordType(asMethod(element).getReturnType(), report);
      }
      asExecutable(element)
          .getParameters()
          .forEach(parameter -> validateJavaKeywordType(parameter.getType(), report));
    } else if (isField(element)) {
      keywordFromName(getSimpleName(element))
          .ifPresent(keyword -> report.addError(javaKeywordErrorMessage(keyword), element));
      validateJavaKeywordType(asField(element).getType(), report);
    } else if (isMethodParameter(element)) {
      // Method parameters names do not cause errors, so we only validate the types.
      validateJavaKeywordType(asMethodParameter(element).getType(), report);
    }
    return report.build();
  }

  private void validatePackageName(XTypeElement element, ValidationReport.Builder report) {
    String packageName = element.getPackageName();
    if (packageName == null) {
      return;
    }
    Iterable<String> names = Splitter.on('.').split(packageName);
    for (String name : names) {
      keywordFromName(name)
          .ifPresent(
              keyword -> report.addError(javaKeywordInPackageErrorMessage(keyword), element));
    }
  }

  private void validateJavaKeywordType(@Nullable XType type, ValidationReport.Builder report) {
    if (type == null || type.isError() || isPrimitive(type)) {
      return;
    }
    // Checks the raw types like `List` in `List<Foo>`
    if (type.getTypeElement() != null) {
      keywordFromName(getSimpleName(type.getTypeElement()))
          .ifPresent(
              keyword -> report.addError(javaKeywordErrorMessage(keyword), type.getTypeElement()));
    }
    // Checks the type arguments like `Foo` in `List<Foo>`
    for (XType typeArgument : type.getTypeArguments()) {
      validateJavaKeywordType(typeArgument, report);
    }
    // Checks the wildcard bound types like `Foo` in `? extends Foo`
    if (type.extendsBound() != null) {
      validateJavaKeywordType(type.extendsBound(), report);
    }
  }

  private Optional<String> keywordFromName(String name) {
    return isJavaKeyword(name) ? Optional.of(name) : Optional.empty();
  }

  boolean isJavaKeyword(String name) {
    return isKeyword(name);
  }

  private String javaKeywordErrorMessage(String keyword) {
    return String.format(
        "The name '%s' cannot be used because it is a Java keyword."
            + " Please use a different name.",
        keyword);
  }

  private String javaKeywordInPackageErrorMessage(String keyword) {
    return String.format(
        "The name '%s' cannot be used as a package name because it is a Java keyword."
            + " Please use a different package name.",
        keyword);
  }
}

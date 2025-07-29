/*
 * Copyright (C) 2015 The Dagger Authors.
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

package dagger.internal.codegen.binding;

import static androidx.room.compiler.processing.XElementKt.isMethodParameter;
import static androidx.room.compiler.processing.XElementKt.isTypeElement;
import static dagger.internal.codegen.base.DiagnosticFormatting.stripCommonTypePrefixes;
import static dagger.internal.codegen.base.ElementFormatter.elementToString;
import static dagger.internal.codegen.xprocessing.XElements.asExecutable;
import static dagger.internal.codegen.xprocessing.XElements.asTypeElement;
import static dagger.internal.codegen.xprocessing.XElements.isExecutable;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableList;
import dagger.internal.codegen.base.Formatter;
import dagger.internal.codegen.xprocessing.XTypes;
import javax.inject.Inject;

/**
 * Formats a {@link Declaration} into a {@link String} suitable for use in error messages.
 */
public final class DeclarationFormatter extends Formatter<Declaration> {
  private final MethodSignatureFormatter methodSignatureFormatter;

  @Inject
  DeclarationFormatter(MethodSignatureFormatter methodSignatureFormatter) {
    this.methodSignatureFormatter = methodSignatureFormatter;
  }

  /**
   * Returns {@code true} for declarations that this formatter can format. Specifically bindings
   * from subcomponent declarations or those with {@linkplain Declaration#bindingElement()
   * binding elements} that are methods, constructors, or types.
   */
  public boolean canFormat(Declaration declaration) {
    if (declaration instanceof SubcomponentDeclaration) {
      return true;
    }
    if (declaration.bindingElement().isPresent()) {
      XElement bindingElement = declaration.bindingElement().get();
      return isMethodParameter(bindingElement)
          || isTypeElement(bindingElement)
          || isExecutable(bindingElement);
    }
    // TODO(dpb): validate whether what this is doing is correct
    return false;
  }

  @Override
  public String format(Declaration declaration) {
    if (declaration instanceof SubcomponentDeclaration) {
      return formatSubcomponentDeclaration((SubcomponentDeclaration) declaration);
    }

    if (declaration.bindingElement().isPresent()) {
      XElement bindingElement = declaration.bindingElement().get();
      if (isMethodParameter(bindingElement)) {
        return elementToString(bindingElement);
      } else if (isTypeElement(bindingElement)) {
        return stripCommonTypePrefixes(
            XTypes.toStableString(asTypeElement(bindingElement).getType()));
      } else if (isExecutable(bindingElement)) {
        return methodSignatureFormatter.format(
            asExecutable(bindingElement),
            declaration.contributingModule().map(XTypeElement::getType));
      }
      throw new IllegalArgumentException("Formatting unsupported for element: " + bindingElement);
    }

    return String.format(
        "Dagger-generated binding for %s",
        stripCommonTypePrefixes(declaration.key().toString()));
  }

  private String formatSubcomponentDeclaration(SubcomponentDeclaration subcomponentDeclaration) {
    ImmutableList<XTypeElement> moduleSubcomponents =
        subcomponentDeclaration.moduleAnnotation().subcomponents();
    int index = moduleSubcomponents.indexOf(subcomponentDeclaration.subcomponentType());
    StringBuilder annotationValue = new StringBuilder();
    if (moduleSubcomponents.size() != 1) {
      annotationValue.append("{");
    }
    annotationValue.append(
        formatArgumentInList(
            index,
            moduleSubcomponents.size(),
            subcomponentDeclaration.subcomponentType().getQualifiedName() + ".class"));
    if (moduleSubcomponents.size() != 1) {
      annotationValue.append("}");
    }

    return String.format(
        "@%s(subcomponents = %s) for %s",
        subcomponentDeclaration.moduleAnnotation().simpleName(),
        annotationValue,
        subcomponentDeclaration.contributingModule().get());
  }
}

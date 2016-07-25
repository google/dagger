/*
 * Copyright (C) 2015 Google, Inc.
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

import static dagger.internal.codegen.ErrorMessages.stripCommonTypePrefixes;

/**
 * Formats a {@link BindingDeclaration} into a {@link String} suitable for use in error messages.
 */
final class BindingDeclarationFormatter extends Formatter<BindingDeclaration> {
  private final MethodSignatureFormatter methodSignatureFormatter;

  BindingDeclarationFormatter(MethodSignatureFormatter methodSignatureFormatter) {
    this.methodSignatureFormatter = methodSignatureFormatter;
  }

  @Override
  public String format(BindingDeclaration bindingDeclaration) {
    switch (bindingDeclaration.bindingElement().asType().getKind()) {
      case EXECUTABLE:
        return methodSignatureFormatter.format(
            bindingDeclaration.bindingElementAsExecutable(),
            bindingDeclaration.contributingModuleType());
      case DECLARED:
        return stripCommonTypePrefixes(bindingDeclaration.bindingElement().asType().toString());
      default:
        throw new IllegalArgumentException(
            "Formatting unsupported for element: " + bindingDeclaration.bindingElement());
    }
  }
}

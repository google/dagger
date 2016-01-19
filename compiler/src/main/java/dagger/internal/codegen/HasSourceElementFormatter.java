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

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.base.Optional;
import dagger.internal.codegen.SourceElement.HasSourceElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Formats a {@link HasSourceElement} into a {@link String} suitable for use in error messages.
 */
final class HasSourceElementFormatter extends Formatter<HasSourceElement> {
  private final MethodSignatureFormatter methodSignatureFormatter;

  HasSourceElementFormatter(MethodSignatureFormatter methodSignatureFormatter) {
    this.methodSignatureFormatter = methodSignatureFormatter;
  }

  @Override
  public String format(HasSourceElement hasElement) {
    SourceElement sourceElement = hasElement.sourceElement();
    checkArgument(
        sourceElement.element().asType().getKind().equals(TypeKind.EXECUTABLE),
        "Not yet supporting nonexecutable elements: %s",
        hasElement);

    Optional<TypeElement> contributedBy = sourceElement.contributedBy();
    return methodSignatureFormatter.format(
        MoreElements.asExecutable(sourceElement.element()),
        contributedBy.isPresent()
            ? Optional.of(MoreTypes.asDeclared(contributedBy.get().asType()))
            : Optional.<DeclaredType>absent());
  }
}

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

import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.DiagnosticFormatting.stripCommonTypePrefixes;
import static dagger.internal.codegen.InjectionAnnotations.getQualifier;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

/**
 * Formats the signature of an {@link ExecutableElement} suitable for use in error messages.
 */
final class MethodSignatureFormatter extends Formatter<ExecutableElement> {
  private final Types types;

  @Inject
  MethodSignatureFormatter(Types types) {
    this.types = types;
  }

  @Override public String format(ExecutableElement method) {
    return format(method, Optional.empty());
  }

  /**
   * Formats an ExecutableElement as if it were contained within the container, if the container is
   * present.
   */
  public String format(ExecutableElement method, Optional<DeclaredType> container) {
    StringBuilder builder = new StringBuilder();
    TypeElement type = MoreElements.asType(method.getEnclosingElement());
    ExecutableType executableType = MoreTypes.asExecutable(method.asType());
    if (container.isPresent()) {
      executableType = MoreTypes.asExecutable(types.asMemberOf(container.get(), method));
      type = MoreElements.asType(container.get().asElement());
    }

    // TODO(cgruber): AnnotationMirror formatter.
    List<? extends AnnotationMirror> annotations = method.getAnnotationMirrors();
    if (!annotations.isEmpty()) {
      Iterator<? extends AnnotationMirror> annotationIterator = annotations.iterator();
      for (int i = 0; annotationIterator.hasNext(); i++) {
        if (i > 0) {
          builder.append(' ');
        }
        builder.append(ErrorMessages.format(annotationIterator.next()));
      }
      builder.append(' ');
    }
    builder.append(nameOfType(executableType.getReturnType()));
    builder.append(' ');
    builder.append(type.getQualifiedName());
    builder.append('.');
    builder.append(method.getSimpleName());
    builder.append('(');
    checkState(method.getParameters().size() == executableType.getParameterTypes().size());
    Iterator<? extends VariableElement> parameters = method.getParameters().iterator();
    Iterator<? extends TypeMirror> parameterTypes = executableType.getParameterTypes().iterator();
    for (int i = 0; parameters.hasNext(); i++) {
      if (i > 0) {
        builder.append(", ");
      }
      appendParameter(builder, parameters.next(), parameterTypes.next());
    }
    builder.append(')');
    return builder.toString();
  }

  private static void appendParameter(StringBuilder builder, VariableElement parameter,
      TypeMirror type) {
    getQualifier(parameter)
        .ifPresent(
            qualifier -> {
              builder.append(ErrorMessages.format(qualifier)).append(' ');
            });
    builder.append(nameOfType(type));
  }

  private static String nameOfType(TypeMirror type) {
    return stripCommonTypePrefixes(type.toString());
  }
}

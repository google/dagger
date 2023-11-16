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

package dagger.internal.codegen.binding;

import static androidx.room.compiler.processing.compat.XConverters.getProcessingEnv;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.base.DiagnosticFormatting.stripCommonTypePrefixes;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.xprocessing.XElements.closestEnclosingTypeElement;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XExecutableElement;
import androidx.room.compiler.processing.XExecutableParameterElement;
import androidx.room.compiler.processing.XExecutableType;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XMethodType;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import androidx.room.compiler.processing.XVariableElement;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.squareup.javapoet.ClassName;
import dagger.internal.codegen.base.Formatter;
import dagger.internal.codegen.xprocessing.XAnnotations;
import dagger.internal.codegen.xprocessing.XTypes;
import java.util.Iterator;
import java.util.Optional;
import javax.inject.Inject;

/** Formats the signature of an {@link XExecutableElement} suitable for use in error messages. */
public final class MethodSignatureFormatter extends Formatter<XExecutableElement> {
  private static final ClassName JET_BRAINS_NOT_NULL =
      ClassName.get("org.jetbrains.annotations", "NotNull");
  private static final ClassName JET_BRAINS_NULLABLE =
      ClassName.get("org.jetbrains.annotations", "Nullable");

  private final InjectionAnnotations injectionAnnotations;

  @Inject
  MethodSignatureFormatter(InjectionAnnotations injectionAnnotations) {
    this.injectionAnnotations = injectionAnnotations;
  }

  /**
   * A formatter that uses the type where the method is declared for the annotations and name of the
   * method, but the method's resolved type as a member of {@code type} for the key.
   */
  public Formatter<XMethodElement> typedFormatter(XType type) {
    checkArgument(isDeclared(type));
    return new Formatter<XMethodElement>() {
      @Override
      public String format(XMethodElement method) {
        return MethodSignatureFormatter.this.format(
            method,
            method.asMemberOf(type),
            closestEnclosingTypeElement(method),
            /* includeReturnType= */ true);
      }
    };
  }

  @Override
  public String format(XExecutableElement method) {
    return format(method, Optional.empty());
  }

  /**
   * Formats an ExecutableElement as if it were contained within the container, if the container is
   * present.
   */
  public String format(XExecutableElement method, Optional<XType> container) {
    return format(method, container, /* includeReturnType= */ true);
  }

  private String format(
      XExecutableElement method, Optional<XType> container, boolean includeReturnType) {
    return container.isPresent()
        ? format(
            method,
            method.asMemberOf(container.get()),
            container.get().getTypeElement(),
            includeReturnType)
        : format(
            method,
            method.getExecutableType(),
            closestEnclosingTypeElement(method),
            includeReturnType);
  }

  private String format(
      XExecutableElement method,
      XExecutableType methodType,
      XTypeElement container,
      boolean includeReturnType) {
    StringBuilder builder = new StringBuilder();
    ImmutableList<String> formattedAnnotations = formatedAnnotations(method);
    if (!formattedAnnotations.isEmpty()) {
      builder.append(String.join(" ", formattedAnnotations)).append(" ");
    }
    if (getSimpleName(method).contentEquals("<init>")) {
      builder.append(container.getQualifiedName());
    } else {
      if (includeReturnType) {
        builder.append(nameOfType(((XMethodType) methodType).getReturnType())).append(' ');
      }
      builder.append(container.getQualifiedName()).append('.').append(getSimpleName(method));
    }
    builder.append('(');
    checkState(method.getParameters().size() == methodType.getParameterTypes().size());
    Iterator<XExecutableParameterElement> parameters = method.getParameters().iterator();
    Iterator<XType> parameterTypes = methodType.getParameterTypes().iterator();
    for (int i = 0; parameters.hasNext(); i++) {
      if (i > 0) {
        builder.append(", ");
      }
      appendParameter(builder, parameters.next(), parameterTypes.next());
    }
    builder.append(')');
    return builder.toString();
  }

  public String formatWithoutReturnType(XExecutableElement method) {
    return format(method, Optional.empty(), /* includeReturnType= */ false);
  }

  private void appendParameter(
      StringBuilder builder, XVariableElement parameter, XType parameterType) {
    injectionAnnotations
        .getQualifier(parameter)
        .ifPresent(qualifier -> builder.append(formatAnnotation(qualifier)).append(' '));
    builder.append(nameOfType(parameterType));
  }

  private static String nameOfType(XType type) {
    return stripCommonTypePrefixes(XTypes.toStableString(type));
  }

  private static ImmutableList<String> formatedAnnotations(XExecutableElement executableElement) {
    Nullability nullability = Nullability.of(executableElement);
    ImmutableList<String> formattedAnnotations =
        Streams.concat(
                executableElement.getAllAnnotations().stream()
                    // Filter out @NotNull annotations added by KAPT to make error messages
                    // consistent
                    .filter(annotation -> !annotation.getClassName().equals(JET_BRAINS_NOT_NULL))
                    .map(MethodSignatureFormatter::formatAnnotation),
                nullability.nullableAnnotations().stream()
                    // Filter out @NotNull annotations added by KAPT to make error messages
                    // consistent
                    .filter(annotation -> !annotation.equals(JET_BRAINS_NOT_NULL))
                    .map(annotation -> String.format("@%s", annotation.canonicalName())))
            .distinct()
            .collect(toImmutableList());
    if (nullability.isKotlinTypeNullable()
        && nullability.nullableAnnotations().stream().noneMatch(JET_BRAINS_NULLABLE::equals)
        && getProcessingEnv(executableElement).findTypeElement(JET_BRAINS_NULLABLE) != null) {
      formattedAnnotations =
          ImmutableList.<String>builder()
              .addAll(formattedAnnotations)
              .add(String.format("@%s", JET_BRAINS_NULLABLE.canonicalName()))
              .build();
    }
    return formattedAnnotations;
  }

  private static String formatAnnotation(XAnnotation annotation) {
    return stripCommonTypePrefixes(XAnnotations.toString(annotation));
  }
}

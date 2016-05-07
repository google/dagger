/*
 * Copyright (C) 2016 Google, Inc.
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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Iterables;
import dagger.Binds;
import dagger.Module;
import dagger.producers.ProducerModule;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_NOT_IN_MODULE;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_TYPE_PARAMETER;
import static dagger.internal.codegen.ErrorMessages.BIND_METHOD_NOT_ABSTRACT;
import static dagger.internal.codegen.ErrorMessages.BIND_METHOD_ONE_ASSIGNABLE_PARAMETER;
import static dagger.internal.codegen.Validation.validateMethodQualifiers;
import static dagger.internal.codegen.Validation.validateReturnType;
import static dagger.internal.codegen.Validation.validateUncheckedThrows;
import static javax.lang.model.element.Modifier.ABSTRACT;

/**
 * A {@linkplain ValidationReport validator} for {@link Bind} methods.
 */
final class BindsMethodValidator {
  private final Elements elements;
  private final Types types;
  private final LoadingCache<ExecutableElement, ValidationReport<ExecutableElement>>
      validationCache;

  BindsMethodValidator(Elements elements, Types types) {
    this.elements = checkNotNull(elements);
    this.types = checkNotNull(types);
    this.validationCache = CacheBuilder.newBuilder().build(new ValidationLoader());
  }

  private final class ValidationLoader
      extends CacheLoader<ExecutableElement, ValidationReport<ExecutableElement>> {
    @Override
    public ValidationReport<ExecutableElement> load(ExecutableElement bindsMethodElement) {
      ValidationReport.Builder<ExecutableElement> builder =
          ValidationReport.about(bindsMethodElement);

      checkArgument(isAnnotationPresent(bindsMethodElement, Binds.class));

      Element enclosingElement = bindsMethodElement.getEnclosingElement();
      if (!isAnnotationPresent(enclosingElement, Module.class)
          && !isAnnotationPresent(enclosingElement, ProducerModule.class)) {
        builder.addError(
            formatErrorMessage(
                BINDING_METHOD_NOT_IN_MODULE,
                String.format(
                    // the first @ is in the format string
                    "%s or @%s",
                    Module.class.getSimpleName(),
                    ProducerModule.class.getSimpleName())),
            bindsMethodElement);
      }

      if (!bindsMethodElement.getTypeParameters().isEmpty()) {
        builder.addError(formatErrorMessage(BINDING_METHOD_TYPE_PARAMETER), bindsMethodElement);
      }

      Set<Modifier> modifiers = bindsMethodElement.getModifiers();
      if (!modifiers.contains(ABSTRACT)) {
        builder.addError(formatErrorMessage(BIND_METHOD_NOT_ABSTRACT), bindsMethodElement);
      }
      TypeMirror returnType = bindsMethodElement.getReturnType();
      validateReturnType(Binds.class, builder, returnType);

      List<? extends VariableElement> parameters = bindsMethodElement.getParameters();
      if (parameters.size() == 1) {
        VariableElement parameter = Iterables.getOnlyElement(parameters);
        if (!types.isAssignable(parameter.asType(), returnType)) {
          builder.addError(
              formatErrorMessage(BIND_METHOD_ONE_ASSIGNABLE_PARAMETER), bindsMethodElement);
        }
      } else {
        builder.addError(
            formatErrorMessage(BIND_METHOD_ONE_ASSIGNABLE_PARAMETER), bindsMethodElement);
      }

      validateUncheckedThrows(elements, types, bindsMethodElement, Binds.class, builder);

      validateMethodQualifiers(builder, bindsMethodElement);

      return builder.build();
    }
  }

  ValidationReport<ExecutableElement> validate(ExecutableElement bindsMethodElement) {
    return validationCache.getUnchecked(bindsMethodElement);
  }

  private String formatErrorMessage(String msg) {
    return String.format(msg, Binds.class.getSimpleName());
  }

  private String formatErrorMessage(String msg, String parameter) {
    return String.format(msg, Binds.class.getSimpleName(), parameter);
  }
}

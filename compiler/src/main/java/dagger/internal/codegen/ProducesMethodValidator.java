/*
 * Copyright (C) 2014 Google, Inc.
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

import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_ABSTRACT;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_MUST_NOT_BIND_FRAMEWORK_TYPES;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_MUST_RETURN_A_VALUE;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_NOT_IN_MODULE;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_PRIVATE;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_SET_VALUES_RAW_SET;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_TYPE_PARAMETER;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_WITH_MULTIPLE_MAP_KEY;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_WITH_NO_MAP_KEY;
import static dagger.internal.codegen.ErrorMessages.PRODUCES_METHOD_NULLABLE;
import static dagger.internal.codegen.ErrorMessages.PRODUCES_METHOD_RAW_FUTURE;
import static dagger.internal.codegen.ErrorMessages.PRODUCES_METHOD_RETURN_TYPE;
import static dagger.internal.codegen.ErrorMessages.PRODUCES_METHOD_SET_VALUES_RETURN_SET;
import static dagger.internal.codegen.ErrorMessages.PRODUCES_METHOD_THROWS;
import static dagger.internal.codegen.MapKeys.getMapKeys;
import static dagger.internal.codegen.ProvidesMethodValidator.validateMapKey;
import static dagger.internal.codegen.ProvidesMethodValidator.validateMultibindingSpecifiers;
import static dagger.internal.codegen.Validation.validateMethodQualifiers;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.type.TypeKind.ARRAY;
import static javax.lang.model.type.TypeKind.DECLARED;
import static javax.lang.model.type.TypeKind.VOID;

/**
 * A {@linkplain ValidationReport validator} for {@link Produces} methods.
 *
 * @author Jesse Beder
 * @since 2.0
 */
// TODO(beder): Consider unifying this with the ProvidesMethodValidator after Provides.Type and
// Produces.Type are reconciled.
final class ProducesMethodValidator {
  private final Elements elements;
  private final Types types;

  ProducesMethodValidator(Elements elements, Types types) {
    this.elements = checkNotNull(elements);
    this.types = checkNotNull(types);
  }

  private TypeElement getSetElement() {
    return elements.getTypeElement(Set.class.getCanonicalName());
  }

  ValidationReport<ExecutableElement> validate(ExecutableElement producesMethodElement) {
    ValidationReport.Builder<ExecutableElement> builder =
        ValidationReport.about(producesMethodElement);

    Element enclosingElement = producesMethodElement.getEnclosingElement();
    if (!isAnnotationPresent(enclosingElement, ProducerModule.class)) {
      builder.addError(
          formatModuleErrorMessage(BINDING_METHOD_NOT_IN_MODULE), producesMethodElement);
    }

    if (!producesMethodElement.getTypeParameters().isEmpty()) {
      builder.addError(formatErrorMessage(BINDING_METHOD_TYPE_PARAMETER), producesMethodElement);
    }

    Set<Modifier> modifiers = producesMethodElement.getModifiers();
    if (modifiers.contains(PRIVATE)) {
      builder.addError(formatErrorMessage(BINDING_METHOD_PRIVATE), producesMethodElement);
    }
    if (modifiers.contains(ABSTRACT)) {
      builder.addError(formatErrorMessage(BINDING_METHOD_ABSTRACT), producesMethodElement);
    }

    if (ConfigurationAnnotations.getNullableType(producesMethodElement).isPresent()) {
      // TODO(beder): Make this an error.
      builder.addWarning(PRODUCES_METHOD_NULLABLE, producesMethodElement);
    }

    TypeMirror returnType = producesMethodElement.getReturnType();
    TypeKind returnTypeKind = returnType.getKind();
    if (returnTypeKind.equals(VOID)) {
      builder.addError(
          formatErrorMessage(BINDING_METHOD_MUST_RETURN_A_VALUE), producesMethodElement);
    }

    if (FrameworkTypes.isFrameworkType(returnType)) {
      builder.addError(
          formatErrorMessage(BINDING_METHOD_MUST_NOT_BIND_FRAMEWORK_TYPES), producesMethodElement);
    }

    TypeMirror exceptionType = elements.getTypeElement(Exception.class.getCanonicalName()).asType();
    TypeMirror errorType = elements.getTypeElement(Error.class.getCanonicalName()).asType();
    for (TypeMirror thrownType : producesMethodElement.getThrownTypes()) {
      if (!types.isSubtype(thrownType, exceptionType) && !types.isSubtype(thrownType, errorType)) {
        builder.addError(PRODUCES_METHOD_THROWS, producesMethodElement);
        break;
      }
    }

    ContributionType contributionType = ContributionType.fromBindingMethod(producesMethodElement);
    validateMapKey(builder, producesMethodElement, contributionType, Produces.class);

    validateMultibindingSpecifiers(builder, producesMethodElement, Produces.class);

    validateMethodQualifiers(builder, producesMethodElement);

    switch (contributionType) {
      case UNIQUE:
      case SET:
        validateSingleReturnType(builder, returnType);
        break;
      case MAP:
        validateSingleReturnType(builder, returnType);
        ImmutableSet<? extends AnnotationMirror> mapKeys = getMapKeys(producesMethodElement);
        switch (mapKeys.size()) {
          case 0:
            builder.addError(
                formatErrorMessage(BINDING_METHOD_WITH_NO_MAP_KEY), producesMethodElement);
            break;
          case 1:
            break;
          default:
            builder.addError(
                formatErrorMessage(BINDING_METHOD_WITH_MULTIPLE_MAP_KEY), producesMethodElement);
            break;
        }
        break;
      case SET_VALUES:
        if (returnTypeKind.equals(DECLARED)
            && MoreTypes.isTypeOf(ListenableFuture.class, returnType)) {
          DeclaredType declaredReturnType = MoreTypes.asDeclared(returnType);
          if (!declaredReturnType.getTypeArguments().isEmpty()) {
            validateSetType(builder, Iterables.getOnlyElement(
                declaredReturnType.getTypeArguments()));
          }
        } else {
          validateSetType(builder, returnType);
        }
        break;
      default:
        throw new AssertionError();
    }

    return builder.build();
  }

  private String formatErrorMessage(String msg) {
    return String.format(msg, Produces.class.getSimpleName());
  }

  private String formatModuleErrorMessage(String msg) {
    return String.format(msg, Produces.class.getSimpleName(), ProducerModule.class.getSimpleName());
  }

  private void validateKeyType(ValidationReport.Builder<? extends Element> reportBuilder,
      TypeMirror type) {
    TypeKind kind = type.getKind();
    if (!(kind.isPrimitive() || kind.equals(DECLARED) || kind.equals(ARRAY))) {
      reportBuilder.addError(PRODUCES_METHOD_RETURN_TYPE, reportBuilder.getSubject());
    }
  }

  private void validateSingleReturnType(ValidationReport.Builder<? extends Element> reportBuilder,
      TypeMirror type) {
    if (type.getKind().equals(DECLARED) && MoreTypes.isTypeOf(ListenableFuture.class, type)) {
      DeclaredType declaredType = MoreTypes.asDeclared(type);
      if (declaredType.getTypeArguments().isEmpty()) {
        reportBuilder.addError(PRODUCES_METHOD_RAW_FUTURE, reportBuilder.getSubject());
      } else {
        validateKeyType(reportBuilder, Iterables.getOnlyElement(declaredType.getTypeArguments()));
      }
    } else {
      validateKeyType(reportBuilder, type);
    }
  }

  private void validateSetType(ValidationReport.Builder<? extends Element> reportBuilder,
      TypeMirror type) {
    if (!type.getKind().equals(DECLARED)) {
      reportBuilder.addError(PRODUCES_METHOD_SET_VALUES_RETURN_SET, reportBuilder.getSubject());
      return;
    }

    // TODO(gak): should we allow "covariant return" for set values?
    DeclaredType declaredType = MoreTypes.asDeclared(type);
    if (!declaredType.asElement().equals(getSetElement())) {
      reportBuilder.addError(PRODUCES_METHOD_SET_VALUES_RETURN_SET, reportBuilder.getSubject());
    } else if (declaredType.getTypeArguments().isEmpty()) {
      reportBuilder.addError(
          formatErrorMessage(BINDING_METHOD_SET_VALUES_RAW_SET), reportBuilder.getSubject());
    } else {
      validateSingleReturnType(reportBuilder,
          Iterables.getOnlyElement(declaredType.getTypeArguments()));
    }
  }
}

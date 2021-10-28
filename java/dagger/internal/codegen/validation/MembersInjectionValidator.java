/*
 * Copyright (C) 2018 The Dagger Authors.
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

import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static com.google.auto.common.MoreTypes.asArray;
import static com.google.auto.common.MoreTypes.asDeclared;
import static com.google.auto.common.MoreTypes.asTypeElement;
import static com.google.common.base.Preconditions.checkArgument;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XType;
import com.google.common.collect.ImmutableList;
import dagger.internal.codegen.binding.InjectionAnnotations;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * Validates members injection requests (members injection methods on components and requests for
 * {@code MembersInjector<Foo>}).
 */
final class MembersInjectionValidator {
  private final InjectionAnnotations injectionAnnotations;

  @Inject
  MembersInjectionValidator(InjectionAnnotations injectionAnnotations) {
    this.injectionAnnotations = injectionAnnotations;
  }

  /** Reports errors if a request for a {@code MembersInjector<Foo>}) is invalid. */
  ValidationReport validateMembersInjectionRequest(
      XElement requestElement, XType membersInjectedType) {
    ValidationReport.Builder report = ValidationReport.about(requestElement);
    checkQualifiers(report, requestElement);
    checkMembersInjectedType(report, membersInjectedType);
    return report.build();
  }

  /**
   * Reports errors if a members injection method on a component is invalid.
   *
   * @throws IllegalArgumentException if the method doesn't have exactly one parameter
   */
  ValidationReport validateMembersInjectionMethod(
      XMethodElement method, XType membersInjectedType) {
    return validateMembersInjectionMethod(toJavac(method), toJavac(membersInjectedType));
  }

  /**
   * Reports errors if a members injection method on a component is invalid.
   *
   * @throws IllegalArgumentException if the method doesn't have exactly one parameter
   */
  private ValidationReport validateMembersInjectionMethod(
      ExecutableElement method, TypeMirror membersInjectedType) {
    checkArgument(
        method.getParameters().size() == 1, "expected a method with one parameter: %s", method);

    ValidationReport.Builder report = ValidationReport.about(method);
    checkQualifiers(report, method);
    checkQualifiers(report, method.getParameters().get(0));
    checkMembersInjectedType(report, membersInjectedType);
    return report.build();
  }

  private void checkQualifiers(ValidationReport.Builder report, XElement element) {
    checkQualifiers(report, toJavac(element));
  }

  private void checkQualifiers(ValidationReport.Builder report, Element element) {
    for (AnnotationMirror qualifier : injectionAnnotations.getQualifiers(element)) {
      report.addError("Cannot inject members into qualified types", element, qualifier);
      break; // just report on the first qualifier, in case there is more than one
    }
  }

  private void checkMembersInjectedType(ValidationReport.Builder report, XType type) {
    checkMembersInjectedType(report, toJavac(type));
  }

  private void checkMembersInjectedType(ValidationReport.Builder report, TypeMirror type) {
    // Only declared types can be members-injected.
    if (type.getKind() != TypeKind.DECLARED) {
      report.addError("Cannot inject members into " + type);
      return;
    }

    // If the type is the erasure of a generic type, that means the user referred to
    // Foo<T> as just 'Foo', which we don't allow.  (This is a judgement call; we
    // *could* allow it and instantiate the type bounds, but we don't.)
    ImmutableList<TypeMirror> typeArguments =
        ImmutableList.copyOf(asDeclared(type).getTypeArguments());
    ImmutableList<Element> typeParameters =
        ImmutableList.copyOf(asTypeElement(type).getTypeParameters());
    if (typeArguments.isEmpty() && !typeParameters.isEmpty()) {
      report.addError("Cannot inject members into raw type " + type);
      return;
    }

    // If the type has arguments, validate that each type argument is declared.
    // Otherwise the type argument may be a wildcard (or other type), and we can't
    // resolve that to actual types.  For array type arguments, validate the type of the array.
    if (!typeArguments.stream().allMatch(this::isResolvableTypeArgument)) {
      report.addError("Cannot inject members into types with unbounded type arguments: " + type);
    }
  }

  // TODO(dpb): Can this be inverted so it explicitly rejects wildcards or type variables?
  // This logic is hard to describe.
  private boolean isResolvableTypeArgument(TypeMirror typeArgument) {
    switch (typeArgument.getKind()) {
      case DECLARED:
        return true;
      case ARRAY:
        return isResolvableArrayComponentType(asArray(typeArgument).getComponentType());
      default:
        return false;
    }
  }

  private boolean isResolvableArrayComponentType(TypeMirror componentType) {
    switch (componentType.getKind()) {
      case DECLARED:
        return asDeclared(componentType).getTypeArguments().stream()
            .allMatch(this::isResolvableTypeArgument);
      case ARRAY:
        return isResolvableArrayComponentType(asArray(componentType).getComponentType());
      default:
        return componentType.getKind().isPrimitive();
    }
  }
}

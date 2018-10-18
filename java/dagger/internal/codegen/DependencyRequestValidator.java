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

package dagger.internal.codegen;

import static dagger.internal.codegen.InjectionAnnotations.getQualifiers;
import static dagger.internal.codegen.RequestKinds.extractKeyType;
import static dagger.internal.codegen.RequestKinds.getRequestKind;
import static javax.lang.model.type.TypeKind.WILDCARD;

import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableSet;
import dagger.MembersInjector;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/** Validation for dependency requests. */
final class DependencyRequestValidator {
  private final MembersInjectionValidator membersInjectionValidator;

  @Inject
  DependencyRequestValidator(MembersInjectionValidator membersInjectionValidator) {
    this.membersInjectionValidator = membersInjectionValidator;
  }

  /**
   * Adds an error if the given dependency request has more than one qualifier annotation or is a
   * non-instance request with a wildcard type.
   */
  void validateDependencyRequest(
      ValidationReport.Builder<?> report, Element requestElement, TypeMirror requestType) {
    ImmutableSet<? extends AnnotationMirror> qualifiers = getQualifiers(requestElement);
    if (qualifiers.size() > 1) {
      for (AnnotationMirror qualifier : qualifiers) {
        report.addError(
            "A single dependency request may not use more than one @Qualifier",
            requestElement,
            qualifier);
      }
    }

    TypeMirror keyType = extractKeyType(getRequestKind(requestType), requestType);
    if (keyType.getKind().equals(WILDCARD)) {
      // TODO(ronshapiro): Explore creating this message using RequestKinds.
      report.addError(
          "Dagger does not support injecting Provider<T>, Lazy<T>, Producer<T>, "
              + "or Produced<T> when T is a wildcard type such as "
              + keyType,
          requestElement);
    }
    if (MoreTypes.isType(keyType) && MoreTypes.isTypeOf(MembersInjector.class, keyType)) {
      DeclaredType membersInjectorType = MoreTypes.asDeclared(keyType);
      if (membersInjectorType.getTypeArguments().isEmpty()) {
        report.addError("Cannot inject a raw MembersInjector", requestElement);
      } else {
        report.addSubreport(
            membersInjectionValidator.validateMembersInjectionRequest(
                requestElement, membersInjectorType.getTypeArguments().get(0)));
      }
    }
  }

  /**
   * Adds an error if the given dependency request is for a {@link dagger.producers.Producer} or
   * {@link dagger.producers.Produced}.
   *
   * <p>Only call this when processing a provision binding.
   */
  // TODO(dpb): Should we disallow Producer entry points in non-production components?
  void checkNotProducer(ValidationReport.Builder<?> report, VariableElement requestElement) {
    TypeMirror requestType = requestElement.asType();
    if (FrameworkTypes.isProducerType(requestType)) {
      report.addError(
          String.format(
              "%s may only be injected in @Produces methods",
              MoreTypes.asTypeElement(requestType).getSimpleName()),
          requestElement);
    }
  }
}

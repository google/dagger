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

import static androidx.room.compiler.processing.XElementKt.isField;
import static androidx.room.compiler.processing.XElementKt.isTypeElement;
import static dagger.internal.codegen.base.FrameworkTypes.isDisallowedType;
import static dagger.internal.codegen.base.FrameworkTypes.isFrameworkType;
import static dagger.internal.codegen.base.FrameworkTypes.isMapValueFrameworkType;
import static dagger.internal.codegen.base.RequestKinds.extractKeyType;
import static dagger.internal.codegen.binding.AssistedInjectionAnnotations.isAssistedFactoryType;
import static dagger.internal.codegen.binding.AssistedInjectionAnnotations.isAssistedInjectionType;
import static dagger.internal.codegen.binding.SourceFiles.membersInjectorNameForType;
import static dagger.internal.codegen.xprocessing.XElements.asField;
import static dagger.internal.codegen.xprocessing.XElements.asTypeElement;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static dagger.internal.codegen.xprocessing.XTypes.isRawParameterizedType;
import static dagger.internal.codegen.xprocessing.XTypes.isTypeOf;
import static dagger.internal.codegen.xprocessing.XTypes.isWildcard;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XFieldElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import androidx.room.compiler.processing.XVariableElement;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.base.FrameworkTypes;
import dagger.internal.codegen.base.MapType;
import dagger.internal.codegen.base.RequestKinds;
import dagger.internal.codegen.binding.InjectionAnnotations;
import dagger.internal.codegen.kotlin.KotlinMetadataUtil;
import dagger.internal.codegen.model.RequestKind;
import dagger.internal.codegen.xprocessing.XTypeNames;
import dagger.internal.codegen.xprocessing.XTypes;
import java.util.Optional;
import javax.inject.Inject;

/** Validation for dependency requests. */
final class DependencyRequestValidator {
  private final XProcessingEnv processingEnv;
  private final MembersInjectionValidator membersInjectionValidator;
  private final InjectionAnnotations injectionAnnotations;
  private final KotlinMetadataUtil metadataUtil;

  @Inject
  DependencyRequestValidator(
      XProcessingEnv processingEnv,
      MembersInjectionValidator membersInjectionValidator,
      InjectionAnnotations injectionAnnotations,
      KotlinMetadataUtil metadataUtil) {
    this.processingEnv = processingEnv;
    this.membersInjectionValidator = membersInjectionValidator;
    this.injectionAnnotations = injectionAnnotations;
    this.metadataUtil = metadataUtil;
  }

  /**
   * Adds an error if the given dependency request has more than one qualifier annotation or is a
   * non-instance request with a wildcard type.
   */
  void validateDependencyRequest(
      ValidationReport.Builder report, XElement requestElement, XType requestType) {
    if (requestElement.hasAnnotation(XTypeNames.ASSISTED)) {
      // Don't validate assisted parameters. These are not dependency requests.
      return;
    }
    if (missingQualifierMetadata(requestElement)) {
      report.addError(
          "Unable to read annotations on an injected Kotlin property. "
          + "The Dagger compiler must also be applied to any project containing @Inject "
          + "properties.",
          requestElement);

      // Skip any further validation if we don't have valid metadata for a type that needs it.
      return;
    }

    new Validator(report, requestElement, requestType).validate();
  }

  /**
   * Returns {@code true} if a kotlin inject field is missing metadata about its qualifiers.
   *
   * <p>See https://youtrack.jetbrains.com/issue/KT-34684.
   */
  private boolean missingQualifierMetadata(XElement requestElement) {
    if (isField(requestElement)) {
      XFieldElement fieldElement = asField(requestElement);
      // static/top-level injected fields are not supported,
      // so no need to get qualifier from kotlin metadata
      if (!fieldElement.isStatic()
          && isTypeElement(fieldElement.getEnclosingElement())
          && metadataUtil.hasMetadata(fieldElement)
          && metadataUtil.isMissingSyntheticPropertyForAnnotations(fieldElement)) {
        Optional<XTypeElement> membersInjector =
            Optional.ofNullable(
                processingEnv.findTypeElement(
                    membersInjectorNameForType(asTypeElement(fieldElement.getEnclosingElement()))));
        return !membersInjector.isPresent();
      }
    }
    return false;
  }

  private final class Validator {
    private final ValidationReport.Builder report;
    private final XElement requestElement;
    private final XType requestType;
    private final ImmutableSet<XAnnotation> qualifiers;

    Validator(ValidationReport.Builder report, XElement requestElement, XType requestType) {
      this.report = report;
      this.requestElement = requestElement;
      this.requestType = requestType;
      this.qualifiers = injectionAnnotations.getQualifiers(requestElement);
    }

    void validate() {
      checkQualifiers();
      checkType();
    }

    private void checkQualifiers() {
      if (qualifiers.size() > 1) {
        for (XAnnotation qualifier : qualifiers) {
          report.addError(
              "A single dependency request may not use more than one @Qualifier",
              requestElement,
              qualifier);
        }
      }
    }

    private void checkType() {
      if (isFrameworkType(requestType) && isRawParameterizedType(requestType)) {
        report.addError(
            "Dagger does not support injecting raw type: " + XTypes.toStableString(requestType),
            requestElement);
        // If the requested type is a raw framework type then skip the remaining checks as they
        // will just be noise.
        return;
      }
      if (isDisallowedType(requestType)) {
        report.addError(
            "Dagger disallows injecting the type: " + XTypes.toStableString(requestType),
            requestElement);
        // If the requested type is a disallowed type then skip the remaining checks as they
        // will just be noise.
        return;
      }
      XType keyType = extractKeyType(requestType);
      if (qualifiers.isEmpty() && isDeclared(keyType)) {
        XTypeElement typeElement = keyType.getTypeElement();
        if (isAssistedInjectionType(typeElement)) {
          report.addError(
              "Dagger does not support injecting @AssistedInject type, "
                  + XTypes.toStableString(requestType)
                  + ". Did you mean to inject its assisted factory type instead?",
              requestElement);
        }
        RequestKind requestKind = RequestKinds.getRequestKind(requestType);
        if (!(requestKind == RequestKind.INSTANCE || requestKind == RequestKind.PROVIDER)
            && isAssistedFactoryType(typeElement)) {
          report.addError(
              "Dagger does not support injecting Lazy<T>, Producer<T>, "
                  + "or Produced<T> when T is an @AssistedFactory-annotated type such as "
                  + XTypes.toStableString(keyType),
              requestElement);
        }
      }
      if (isWildcard(keyType)) {
        // TODO(ronshapiro): Explore creating this message using RequestKinds.
        report.addError(
            "Dagger does not support injecting Provider<T>, Lazy<T>, Producer<T>, "
                + "or Produced<T> when T is a wildcard type such as "
                + XTypes.toStableString(keyType),
            requestElement);
      }
      if (isTypeOf(keyType, XTypeNames.MEMBERS_INJECTOR)) {
        if (keyType.getTypeArguments().isEmpty()) {
          report.addError("Cannot inject a raw MembersInjector", requestElement);
        } else {
          report.addSubreport(
              membersInjectionValidator.validateMembersInjectionRequest(
                  requestElement, keyType.getTypeArguments().get(0)));
        }
      }
      if (MapType.isMap(keyType)) {
        MapType mapType = MapType.from(keyType);
        if (!mapType.isRawType()) {
          XType valueType = mapType.valueType();
          if (isMapValueFrameworkType(valueType) && isRawParameterizedType(valueType)) {
            report.addError(
                "Dagger does not support injecting maps of raw framework types: "
                + XTypes.toStableString(requestType),
                requestElement);
          }
          if (isDisallowedType(valueType)) {
            report.addError(
                "Dagger does not support injecting maps of disallowed types: "
                + XTypes.toStableString(requestType),
                requestElement);
          }
        }
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
  void checkNotProducer(ValidationReport.Builder report, XVariableElement requestElement) {
    XType requestType = requestElement.getType();
    if (FrameworkTypes.isProducerType(requestType)) {
      report.addError(
          String.format(
              "%s may only be injected in @Produces methods",
              getSimpleName(requestType.getTypeElement())),
          requestElement);
    }
  }
}

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

import com.google.auto.value.AutoValue;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableSet;
import dagger.producers.Produces;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

/** A collection of options that dictate how the compiler will run. */
@AutoValue
abstract class CompilerOptions {
  abstract boolean usesProducers();
  abstract boolean writeProducerNameInToken();
  abstract Diagnostic.Kind nullableValidationKind();
  abstract Diagnostic.Kind privateMemberValidationKind();
  abstract Diagnostic.Kind staticMemberValidationKind();
  abstract ValidationType scopeCycleValidationType();

  static Builder builder() {
    return new AutoValue_CompilerOptions.Builder();
  }

  static CompilerOptions create(ProcessingEnvironment processingEnv, Elements elements) {
    return builder()
        .usesProducers(elements.getTypeElement(Produces.class.getCanonicalName()) != null)
        .writeProducerNameInToken(
            writeProducerNameInToken(processingEnv).equals(FeatureStatus.ENABLED))
        .nullableValidationKind(nullableValidationType(processingEnv).diagnosticKind().get())
        .privateMemberValidationKind(
            privateMemberValidationType(processingEnv).diagnosticKind().get())
        .staticMemberValidationKind(
            staticMemberValidationType(processingEnv).diagnosticKind().get())
        .scopeCycleValidationType(scopeValidationType(processingEnv))
        .build();
  }

  @AutoValue.Builder
  interface Builder {
    Builder usesProducers(boolean usesProduces);
    Builder writeProducerNameInToken(boolean writeProducerNameInToken);
    Builder nullableValidationKind(Diagnostic.Kind kind);
    Builder privateMemberValidationKind(Diagnostic.Kind kind);
    Builder staticMemberValidationKind(Diagnostic.Kind kind);
    Builder scopeCycleValidationType(ValidationType type);
    CompilerOptions build();
  }

  static final String WRITE_PRODUCER_NAME_IN_TOKEN_KEY = "dagger.writeProducerNameInToken";

  static final String DISABLE_INTER_COMPONENT_SCOPE_VALIDATION_KEY =
      "dagger.disableInterComponentScopeValidation";

  static final String NULLABLE_VALIDATION_KEY = "dagger.nullableValidation";

  static final String PRIVATE_MEMBER_VALIDATION_TYPE_KEY = "dagger.privateMemberValidation";

  static final String STATIC_MEMBER_VALIDATION_TYPE_KEY = "dagger.staticMemberValidation";

  static final ImmutableSet<String> SUPPORTED_OPTIONS = ImmutableSet.of(
        WRITE_PRODUCER_NAME_IN_TOKEN_KEY,
        DISABLE_INTER_COMPONENT_SCOPE_VALIDATION_KEY,
        NULLABLE_VALIDATION_KEY,
        PRIVATE_MEMBER_VALIDATION_TYPE_KEY,
        STATIC_MEMBER_VALIDATION_TYPE_KEY);

  private static FeatureStatus writeProducerNameInToken(ProcessingEnvironment processingEnv) {
    return valueOf(
        processingEnv,
        WRITE_PRODUCER_NAME_IN_TOKEN_KEY,
        FeatureStatus.DISABLED,
        EnumSet.allOf(FeatureStatus.class));
  }

  private static ValidationType scopeValidationType(ProcessingEnvironment processingEnv) {
    return valueOf(
        processingEnv,
        DISABLE_INTER_COMPONENT_SCOPE_VALIDATION_KEY,
        ValidationType.ERROR,
        EnumSet.allOf(ValidationType.class));
  }

  private static ValidationType nullableValidationType(ProcessingEnvironment processingEnv) {
    return valueOf(
        processingEnv,
        NULLABLE_VALIDATION_KEY,
        ValidationType.ERROR,
        EnumSet.of(ValidationType.ERROR, ValidationType.WARNING));
  }

  private static ValidationType privateMemberValidationType(ProcessingEnvironment processingEnv) {
    return valueOf(
        processingEnv,
        PRIVATE_MEMBER_VALIDATION_TYPE_KEY,
        ValidationType.ERROR,
        EnumSet.of(ValidationType.ERROR, ValidationType.WARNING));
  }

  private static ValidationType staticMemberValidationType(ProcessingEnvironment processingEnv) {
    return valueOf(
        processingEnv,
        STATIC_MEMBER_VALIDATION_TYPE_KEY,
        ValidationType.ERROR,
        EnumSet.of(ValidationType.ERROR, ValidationType.WARNING));
  }

  private static <T extends Enum<T>> T valueOf(
      ProcessingEnvironment processingEnv, String key, T defaultValue, Set<T> validValues) {
    Map<String, String> options = processingEnv.getOptions();
    if (options.containsKey(key)) {
      try {
        T type =
            Enum.valueOf(defaultValue.getDeclaringClass(), Ascii.toUpperCase(options.get(key)));
        if (!validValues.contains(type)) {
          throw new IllegalArgumentException(); // let handler below print out good msg.
        }
        return type;
      } catch (IllegalArgumentException e) {
        processingEnv
            .getMessager()
            .printMessage(
                Diagnostic.Kind.ERROR,
                "Processor option -A"
                    + key
                    + " may only have the values "
                    + validValues
                    + " (case insensitive), found: "
                    + options.get(key));
      }
    }
    return defaultValue;
  }
}

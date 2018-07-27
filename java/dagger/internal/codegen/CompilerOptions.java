/*
 * Copyright (C) 2016 The Dagger Authors.
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

import com.google.auto.value.AutoValue;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableSet;
import dagger.producers.Produces;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;

/** A collection of options that dictate how the compiler will run. */
@AutoValue
abstract class CompilerOptions {
  abstract boolean usesProducers();

  /**
   * Returns true if the fast initialization flag, {@code fastInit}, is enabled.
   *
   * <p>If enabled, the generated code will attempt to optimize for fast component initialization.
   * This is done by reducing the number of factory classes loaded during initialization and the
   * number of eagerly initialized fields at the cost of potential memory leaks and higher
   * per-provision instantiation time.
   */
  abstract boolean fastInit();

  /**
   * Returns true if the experimental Android mode 2 is enabled.
   *
   * <p><b>Warning: Do Not use! This flag is for internal, experimental use only!</b>
   *
   * <p>Issues related to this flag will not be supported. This flag could break your build,
   * or cause other unknown issues at runtime.
   *
   * <p>If enabled, the generated code will try to reduce class loading due to providers by using
   * a single {@code Provider} class to replace all factory classes.
   */
  abstract boolean experimentalAndroidMode2();

  abstract boolean writeProducerNameInToken();

  abstract Diagnostic.Kind nullableValidationKind();

  boolean doCheckForNulls() {
    return nullableValidationKind().equals(Kind.ERROR);
  }

  abstract Diagnostic.Kind privateMemberValidationKind();

  abstract Diagnostic.Kind staticMemberValidationKind();

  abstract boolean ignorePrivateAndStaticInjectionForComponent();

  abstract ValidationType scopeCycleValidationType();

  abstract boolean warnIfInjectionFactoryNotGeneratedUpstream();

  abstract boolean headerCompilation();

  abstract boolean aheadOfTimeSubcomponents();

  /** See b/79859714 */
  abstract boolean floatingBindsMethods();

  static Builder builder() {
    return new AutoValue_CompilerOptions.Builder().headerCompilation(false);
  }

  static CompilerOptions create(ProcessingEnvironment processingEnv, DaggerElements elements) {
    checkState(
        !(fastInitEnabled(processingEnv)
            && experimentalAndroidMode2FeatureStatus(processingEnv).equals(FeatureStatus.ENABLED)),
        "fastInit/experimentalAndroidMode and experimentalAndroidMode2 cannot be used together.");

    return builder()
        .usesProducers(elements.getTypeElement(Produces.class) != null)
        .headerCompilation(processingEnv.getOptions().containsKey(HEADER_COMPILATION))
        .fastInit(fastInitEnabled(processingEnv))
        .experimentalAndroidMode2(
            experimentalAndroidMode2FeatureStatus(processingEnv).equals(FeatureStatus.ENABLED))
        .writeProducerNameInToken(
            writeProducerNameInTokenFeatureStatus(processingEnv).equals(FeatureStatus.ENABLED))
        .nullableValidationKind(nullableValidationType(processingEnv).diagnosticKind().get())
        .privateMemberValidationKind(
            privateMemberValidationType(processingEnv).diagnosticKind().get())
        .staticMemberValidationKind(
            staticMemberValidationType(processingEnv).diagnosticKind().get())
        .ignorePrivateAndStaticInjectionForComponent(
            ignorePrivateAndStaticInjectionForComponentFeatureStatus(processingEnv)
                .equals(FeatureStatus.DISABLED))
        .scopeCycleValidationType(scopeValidationType(processingEnv))
        .warnIfInjectionFactoryNotGeneratedUpstream(
            warnIfInjectionFactoryNotGeneratedUpstreamFeatureStatus(processingEnv)
                .equals(FeatureStatus.ENABLED))
        .aheadOfTimeSubcomponents(
            aheadOfTimeSubcomponentsFeatureStatus(processingEnv).equals(FeatureStatus.ENABLED))
        .floatingBindsMethods(
            floatingBindsMethodsFeatureStatus(processingEnv).equals(FeatureStatus.ENABLED))
        .build();
  }

  @AutoValue.Builder
  interface Builder {
    Builder usesProducers(boolean usesProduces);

    Builder headerCompilation(boolean headerCompilation);

    Builder fastInit(boolean fastInit);

    Builder experimentalAndroidMode2(boolean experimentalAndroidMode2);

    Builder writeProducerNameInToken(boolean writeProducerNameInToken);

    Builder nullableValidationKind(Diagnostic.Kind kind);

    Builder privateMemberValidationKind(Diagnostic.Kind kind);

    Builder staticMemberValidationKind(Diagnostic.Kind kind);

    Builder ignorePrivateAndStaticInjectionForComponent(
        boolean ignorePrivateAndStaticInjectionForComponent);

    Builder scopeCycleValidationType(ValidationType type);

    Builder warnIfInjectionFactoryNotGeneratedUpstream(
        boolean warnIfInjectionFactoryNotGeneratedUpstream);

    Builder aheadOfTimeSubcomponents(boolean aheadOfTimeSubcomponents);

    Builder floatingBindsMethods(boolean enabled);

    CompilerOptions build();
  }

  private static final String HEADER_COMPILATION = "experimental_turbine_hjar";

  static final String FAST_INIT = "dagger.fastInit";

  // TODO(user): Remove once all usages are migrated to FAST_INIT.
  static final String EXPERIMENTAL_ANDROID_MODE = "dagger.experimentalAndroidMode";

  static final String EXPERIMENTAL_ANDROID_MODE2 = "dagger.experimentalAndroidMode2";

  static final String WRITE_PRODUCER_NAME_IN_TOKEN_KEY = "dagger.writeProducerNameInToken";

  static final String DISABLE_INTER_COMPONENT_SCOPE_VALIDATION_KEY =
      "dagger.disableInterComponentScopeValidation";

  static final String NULLABLE_VALIDATION_KEY = "dagger.nullableValidation";

  static final String PRIVATE_MEMBER_VALIDATION_TYPE_KEY = "dagger.privateMemberValidation";

  static final String STATIC_MEMBER_VALIDATION_TYPE_KEY = "dagger.staticMemberValidation";

  static final String WARN_IF_INJECTION_FACTORY_NOT_GENERATED_UPSTREAM_KEY =
      "dagger.warnIfInjectionFactoryNotGeneratedUpstream";

  /**
   * If true, Dagger will generate factories and components even if some members-injected types have
   * private or static {@code @Inject}-annotated members.
   *
   * <p>This defaults to false, and should only ever be enabled by the TCK tests. Disabling this
   * validation could lead to generating code that does not compile.
   */
  static final String IGNORE_PRIVATE_AND_STATIC_INJECTION_FOR_COMPONENT =
      "dagger.ignorePrivateAndStaticInjectionForComponent";

  static final String AHEAD_OF_TIME_COMPONENTS_KEY = "dagger.experimentalAheadOfTimeSubcomponents";

  static final String FLOATING_BINDS_METHODS_KEY = "dagger.floatingBindsMethods";

  static final ImmutableSet<String> SUPPORTED_OPTIONS =
      ImmutableSet.of(
          FAST_INIT,
          EXPERIMENTAL_ANDROID_MODE,
          HEADER_COMPILATION,
          WRITE_PRODUCER_NAME_IN_TOKEN_KEY,
          DISABLE_INTER_COMPONENT_SCOPE_VALIDATION_KEY,
          NULLABLE_VALIDATION_KEY,
          PRIVATE_MEMBER_VALIDATION_TYPE_KEY,
          STATIC_MEMBER_VALIDATION_TYPE_KEY,
          WARN_IF_INJECTION_FACTORY_NOT_GENERATED_UPSTREAM_KEY,
          IGNORE_PRIVATE_AND_STATIC_INJECTION_FOR_COMPONENT,
          AHEAD_OF_TIME_COMPONENTS_KEY,
          FLOATING_BINDS_METHODS_KEY);

  private static boolean fastInitEnabled(ProcessingEnvironment processingEnv) {
    return valueOf(
            processingEnv,
            FAST_INIT,
            FeatureStatus.DISABLED,
            EnumSet.allOf(FeatureStatus.class))
        .equals(FeatureStatus.ENABLED)
      || valueOf(
            processingEnv,
            EXPERIMENTAL_ANDROID_MODE,
            FeatureStatus.DISABLED,
            EnumSet.allOf(FeatureStatus.class))
        .equals(FeatureStatus.ENABLED);
  }

  private static FeatureStatus experimentalAndroidMode2FeatureStatus(
      ProcessingEnvironment processingEnv) {
    return valueOf(
        processingEnv,
        EXPERIMENTAL_ANDROID_MODE2,
        FeatureStatus.DISABLED,
        EnumSet.allOf(FeatureStatus.class));
  }

  private static FeatureStatus writeProducerNameInTokenFeatureStatus(
      ProcessingEnvironment processingEnv) {
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

  private static FeatureStatus ignorePrivateAndStaticInjectionForComponentFeatureStatus(
      ProcessingEnvironment processingEnv) {
    return valueOf(
        processingEnv,
        IGNORE_PRIVATE_AND_STATIC_INJECTION_FOR_COMPONENT,
        FeatureStatus.DISABLED,
        EnumSet.allOf(FeatureStatus.class));
  }

  private static FeatureStatus warnIfInjectionFactoryNotGeneratedUpstreamFeatureStatus(
      ProcessingEnvironment processingEnv) {
    return valueOf(
        processingEnv,
        WARN_IF_INJECTION_FACTORY_NOT_GENERATED_UPSTREAM_KEY,
        FeatureStatus.DISABLED,
        EnumSet.allOf(FeatureStatus.class));
  }

  private static FeatureStatus aheadOfTimeSubcomponentsFeatureStatus(
      ProcessingEnvironment processingEnv) {
    return valueOf(
        processingEnv,
        AHEAD_OF_TIME_COMPONENTS_KEY,
        FeatureStatus.DISABLED,
        EnumSet.allOf(FeatureStatus.class));
  }

  private static FeatureStatus floatingBindsMethodsFeatureStatus(
      ProcessingEnvironment processingEnv) {
    return valueOf(
        processingEnv,
        FLOATING_BINDS_METHODS_KEY,
        FeatureStatus.DISABLED,
        EnumSet.allOf(FeatureStatus.class));
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

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

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Sets.immutableEnumSet;
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.FeatureStatus.DISABLED;
import static dagger.internal.codegen.FeatureStatus.ENABLED;
import static dagger.internal.codegen.ValidationType.ERROR;
import static dagger.internal.codegen.ValidationType.NONE;
import static dagger.internal.codegen.ValidationType.WARNING;
import static java.util.EnumSet.allOf;

import com.google.auto.value.AutoValue;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import dagger.producers.Produces;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;

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

  abstract boolean formatGeneratedSource();

  abstract boolean writeProducerNameInToken();

  abstract Diagnostic.Kind nullableValidationKind();

  boolean doCheckForNulls() {
    return nullableValidationKind().equals(Diagnostic.Kind.ERROR);
  }

  abstract Diagnostic.Kind privateMemberValidationKind();

  abstract Diagnostic.Kind staticMemberValidationKind();

  /**
   * If {@code true}, Dagger will generate factories and components even if some members-injected
   * types have {@code private} or {@code static} {@code @Inject}-annotated members.
   *
   * <p>This should only ever be enabled by the TCK tests. Disabling this validation could lead to
   * generating code that does not compile.
   */
  abstract boolean ignorePrivateAndStaticInjectionForComponent();

  abstract ValidationType scopeCycleValidationType();

  abstract boolean warnIfInjectionFactoryNotGeneratedUpstream();

  abstract boolean headerCompilation();

  abstract boolean aheadOfTimeSubcomponents();

  abstract boolean useGradleIncrementalProcessing();

  abstract ValidationType moduleBindingValidationType();

  abstract Diagnostic.Kind moduleHasDifferentScopesDiagnosticKind();

  static Builder builder() {
    return new AutoValue_CompilerOptions.Builder()
        .headerCompilation(false)
        .useGradleIncrementalProcessing(false);
  }

  static CompilerOptions create(ProcessingEnvironment processingEnv) {
    Builder builder = new AutoValue_CompilerOptions.Builder();
    for (Option option : concat(allOf(Feature.class), allOf(Validation.class))) {
      option.set(builder, processingEnv);
    }
    return builder.build().validate();
  }

  CompilerOptions validate() {
    checkState(
        !(fastInit() && experimentalAndroidMode2()),
        "fastInit/experimentalAndroidMode and experimentalAndroidMode2 cannot be used together.");
    return this;
  }

  @AutoValue.Builder
  @CanIgnoreReturnValue
  interface Builder {
    Builder usesProducers(boolean usesProduces);

    Builder headerCompilation(boolean headerCompilation);

    Builder fastInit(boolean fastInit);

    Builder experimentalAndroidMode2(boolean experimentalAndroidMode2);

    Builder formatGeneratedSource(boolean formatGeneratedSource);

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

    Builder useGradleIncrementalProcessing(boolean enabled);

    Builder moduleBindingValidationType(ValidationType validationType);

    Builder moduleHasDifferentScopesDiagnosticKind(Diagnostic.Kind kind);

    @CheckReturnValue
    CompilerOptions build();
  }

  /** An option that can be set into {@link CompilerOptions}. */
  private interface Option {

    /** Sets the appropriate property on a {@link CompilerOptions.Builder}. */
    void set(Builder builder, ProcessingEnvironment processingEnvironment);

    /**
     * {@code true} if {@link #toString()} represents a {@linkplain Processor#getSupportedOptions()
     * supported command line option}.
     */
    default boolean useCommandLineOption() {
      return true;
    }
  }

  /** A feature that can be enabled or disabled. */
  private enum Feature implements Option {
    HEADER_COMPILATION(Builder::headerCompilation) {
      @Override
      boolean isEnabled(ProcessingEnvironment processingEnvironment) {
        return processingEnvironment.getOptions().containsKey(toString());
      }

      @Override
      public String toString() {
        return "experimental_turbine_hjar";
      }
    },

    FAST_INIT(Builder::fastInit) {
      @Override
      boolean isEnabled(ProcessingEnvironment processingEnvironment) {
        return super.isEnabled(processingEnvironment)
            || EXPERIMENTAL_ANDROID_MODE.isEnabled(processingEnvironment);
      }
    },

    // TODO(user): Remove once all usages are migrated to FAST_INIT.
    EXPERIMENTAL_ANDROID_MODE((builder, enabled) -> {}),

    EXPERIMENTAL_ANDROID_MODE2(Builder::experimentalAndroidMode2),

    FORMAT_GENERATED_SOURCE(Builder::formatGeneratedSource, ENABLED),

    WRITE_PRODUCER_NAME_IN_TOKEN(Builder::writeProducerNameInToken),

    WARN_IF_INJECTION_FACTORY_NOT_GENERATED_UPSTREAM(
        Builder::warnIfInjectionFactoryNotGeneratedUpstream),

    IGNORE_PRIVATE_AND_STATIC_INJECTION_FOR_COMPONENT(
        Builder::ignorePrivateAndStaticInjectionForComponent),

    EXPERIMENTAL_AHEAD_OF_TIME_SUBCOMPONENTS(Builder::aheadOfTimeSubcomponents),

    FLOATING_BINDS_METHODS((builder, ignoredValue) -> {}) {
     @Override
      public void set(Builder builder, ProcessingEnvironment processingEnvironment) {
        if (processingEnvironment.getOptions().containsKey(toString())) {
          processingEnvironment
            .getMessager()
            .printMessage(
                Diagnostic.Kind.WARNING,
                toString() + " is no longer a recognized option by Dagger");
        }
      }
    },

    USE_GRADLE_INCREMENTAL_PROCESSING(Builder::useGradleIncrementalProcessing) {
      @Override
      boolean isEnabled(ProcessingEnvironment processingEnvironment) {
        return processingEnvironment.getOptions().containsKey(toString());
      }

      @Override
      public String toString() {
        return "dagger.gradle.incremental";
      }
    },

    USES_PRODUCERS(Builder::usesProducers) {
      @Override
      boolean isEnabled(ProcessingEnvironment processingEnvironment) {
        return processingEnvironment
                .getElementUtils()
                .getTypeElement(Produces.class.getCanonicalName())
            != null;
      }

      @Override
      public boolean useCommandLineOption() {
        return false;
      }
    },
    ;

    final FeatureStatus defaultValue;
    final BiConsumer<Builder, Boolean> setter;

    Feature(BiConsumer<Builder, Boolean> setter) {
      this(setter, DISABLED);
    }

    Feature(BiConsumer<Builder, Boolean> setter, FeatureStatus defaultValue) {
      this.setter = setter;
      this.defaultValue = defaultValue;
    }

    @Override
    public void set(Builder builder, ProcessingEnvironment processingEnvironment) {
      setter.accept(builder, isEnabled(processingEnvironment));
    }

    boolean isEnabled(ProcessingEnvironment processingEnvironment) {
      return CompilerOptions.valueOf(
              processingEnvironment, toString(), defaultValue, allOf(FeatureStatus.class))
          .equals(ENABLED);
    }

    @Override
    public String toString() {
      return optionName(name());
    }
  }

  /** The diagnostic kind or validation type for a kind of validation. */
  private enum Validation implements Option {
    DISABLE_INTER_COMPONENT_SCOPE_VALIDATION(Builder::scopeCycleValidationType),

    NULLABLE_VALIDATION(kindSetter(Builder::nullableValidationKind), ERROR, WARNING) {
    },

    PRIVATE_MEMBER_VALIDATION(kindSetter(Builder::privateMemberValidationKind), ERROR, WARNING),

    STATIC_MEMBER_VALIDATION(kindSetter(Builder::staticMemberValidationKind), ERROR, WARNING),

    /** Whether to validate partial binding graphs associated with modules. */
    MODULE_BINDING_VALIDATION(Builder::moduleBindingValidationType, NONE, ERROR, WARNING),

    /**
     * How to report conflicting scoped bindings when validating partial binding graphs associated
     * with modules.
     */
    MODULE_HAS_DIFFERENT_SCOPES_VALIDATION(
        kindSetter(Builder::moduleHasDifferentScopesDiagnosticKind), ERROR, WARNING),
    ;

    static BiConsumer<Builder, ValidationType> kindSetter(
        BiConsumer<Builder, Diagnostic.Kind> setter) {
      return (builder, validationType) ->
          setter.accept(builder, validationType.diagnosticKind().get());
    }

    final ValidationType defaultType;
    final ImmutableSet<ValidationType> validTypes;
    final BiConsumer<Builder, ValidationType> setter;

    Validation(BiConsumer<Builder, ValidationType> setter) {
      this(setter, ERROR, WARNING, NONE);
    }

    Validation(
        BiConsumer<Builder, ValidationType> setter,
        ValidationType defaultType,
        ValidationType... moreValidTypes) {
      this.setter = setter;
      this.defaultType = defaultType;
      this.validTypes = immutableEnumSet(defaultType, moreValidTypes);
    }

    @Override
    public void set(Builder builder, ProcessingEnvironment processingEnvironment) {
      setter.accept(builder, validationType(processingEnvironment));
    }

    ValidationType validationType(ProcessingEnvironment processingEnvironment) {
      return CompilerOptions.valueOf(processingEnvironment, toString(), defaultType, validTypes);
    }

    @Override
    public String toString() {
      return optionName(name());
    }
  }

  static final ImmutableSet<String> SUPPORTED_OPTIONS =
      Stream.<Option>concat(Arrays.stream(Feature.values()), Arrays.stream(Validation.values()))
          .filter(Option::useCommandLineOption)
          .map(Object::toString)
          .collect(toImmutableSet());

  private static String optionName(String enumName) {
    return "dagger." + UPPER_UNDERSCORE.to(LOWER_CAMEL, enumName);
  }

  private static <T extends Enum<T>> T valueOf(
      ProcessingEnvironment processingEnv, String key, T defaultValue, Set<T> validValues) {
    Map<String, String> options = processingEnv.getOptions();
    if (options.containsKey(key)) {
      String optionValue = options.get(key);
      if (optionValue == null) {
        processingEnv
            .getMessager()
            .printMessage(Diagnostic.Kind.ERROR, "Processor option -A" + key + " needs a value");
      } else {
        try {
          T type = Enum.valueOf(defaultValue.getDeclaringClass(), Ascii.toUpperCase(optionValue));
          if (!validValues.contains(type)) {
            throw new IllegalArgumentException(); // let handler below print out good msg.
          }
          return type;
        } catch (IllegalArgumentException e) {
          processingEnv
              .getMessager()
              .printMessage(
                  Diagnostic.Kind.ERROR,
                  String.format(
                      "Processor option -A%s may only have the values %s "
                          + "(case insensitive), found: %s",
                      key, validValues, options.get(key)));
        }
      }
    }
    return defaultValue;
  }
}

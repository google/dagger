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
import static com.google.common.base.Preconditions.checkNotNull;
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
import com.squareup.javapoet.AnnotationSpec;
import dagger.internal.GenerationOptions;
import dagger.producers.Produces;
import java.util.Arrays;
import java.util.EnumSet;
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

  abstract ValidationType explicitBindingConflictsWithInjectValidationType();

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
    return builder.build();
  }

  abstract Builder toBuilder();

  /**
   * Creates a new {@link CompilerOptions} from the serialized {@link GenerationOptions} of a base
   * component implementation.
   */
  CompilerOptions withGenerationOptions(GenerationOptions generationOptions) {
    return toBuilder().fastInit(generationOptions.fastInit()).build();
  }

  /**
   * Returns an {@link GenerationOptions} annotation that serializes any options for this
   * compilation that should be reused in future compilations.
   */
  AnnotationSpec toGenerationOptionsAnnotation() {
    return AnnotationSpec.builder(GenerationOptions.class)
        .addMember("fastInit", "$L", fastInit())
        .build();
  }

  @AutoValue.Builder
  @CanIgnoreReturnValue
  interface Builder {
    Builder usesProducers(boolean usesProduces);

    Builder headerCompilation(boolean headerCompilation);

    Builder fastInit(boolean fastInit);

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

    Builder explicitBindingConflictsWithInjectValidationType(ValidationType validationType);

    @CheckReturnValue
    CompilerOptions build();
  }

  /** An option that can be set into {@link CompilerOptions}. */
  private interface Option<T extends Enum<T>> {

    /** Sets the appropriate property on a {@link CompilerOptions.Builder}. */
    void set(Builder builder, ProcessingEnvironment processingEnvironment);

    /**
     * {@code true} if {@link #toString()} represents a {@linkplain Processor#getSupportedOptions()
     * supported command line option}.
     */
    default boolean useCommandLineOption() {
      return true;
    }

    /** The default value for this option. */
    T defaultValue();

    /** The valid values for this option. */
    Set<T> validValues();
  }

  /** A feature that can be enabled or disabled. */
  private enum Feature implements Option<FeatureStatus> {
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

    FAST_INIT(Builder::fastInit),

    EXPERIMENTAL_ANDROID_MODE((builder, ignoredValue) -> {}) {
      @Override
      public void set(Builder builder, ProcessingEnvironment processingEnvironment) {
        noLongerRecognizedWarning(processingEnvironment);
      }
    },

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
        noLongerRecognizedWarning(processingEnvironment);
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

    final OptionParser<FeatureStatus> parser = new OptionParser<>(this);
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
    public FeatureStatus defaultValue() {
      return defaultValue;
    }

    @Override
    public Set<FeatureStatus> validValues() {
      return EnumSet.allOf(FeatureStatus.class);
    }

    @Override
    public void set(Builder builder, ProcessingEnvironment processingEnvironment) {
      setter.accept(builder, isEnabled(processingEnvironment));
    }

    boolean isEnabled(ProcessingEnvironment processingEnvironment) {
      return parser.parse(processingEnvironment).equals(ENABLED);
    }

    @Override
    public String toString() {
      return optionName(name());
    }

    void noLongerRecognizedWarning(ProcessingEnvironment processingEnvironment) {
      if (processingEnvironment.getOptions().containsKey(toString())) {
          processingEnvironment
            .getMessager()
            .printMessage(
                Diagnostic.Kind.WARNING,
                toString() + " is no longer a recognized option by Dagger");
      }
    }

  }

  /** The diagnostic kind or validation type for a kind of validation. */
  private enum Validation implements Option<ValidationType> {
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

    /**
     * How to report that an explicit binding in a subcomponent conflicts with an {@code @Inject}
     * constructor used in an ancestor component.
     */
    EXPLICIT_BINDING_CONFLICTS_WITH_INJECT(
        Builder::explicitBindingConflictsWithInjectValidationType, WARNING, ERROR, NONE),
    ;

    final OptionParser<ValidationType> parser = new OptionParser<>(this);

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
    public ValidationType defaultValue() {
      return defaultType;
    }

    @Override
    public Set<ValidationType> validValues() {
      return validTypes;
    }

    @Override
    public void set(Builder builder, ProcessingEnvironment processingEnvironment) {
      setter.accept(builder, parser.parse(processingEnvironment));
    }

    @Override
    public String toString() {
      return optionName(name());
    }
  }

  private static String optionName(String enumName) {
    return "dagger." + UPPER_UNDERSCORE.to(LOWER_CAMEL, enumName);
  }

  static ImmutableSet<String> supportedOptions() {
    return Stream.<Option<?>[]>of(Feature.values(), Validation.values())
        .flatMap(Arrays::stream)
        .filter(Option::useCommandLineOption)
        .map(Option::toString)
        .collect(toImmutableSet());
  }

  /** A parser for an {@link Option}. */
  private static class OptionParser<T extends Enum<T>> {
    private final Option<T> option;

    OptionParser(Option<T> option) {
      this.option = checkNotNull(option);
    }

    /**
     * Returns the value for this option as set on the command line, or the default value if not.
     */
    T parse(ProcessingEnvironment processingEnvironment) {
      String key = option.toString();
      Map<String, String> options = processingEnvironment.getOptions();
      if (options.containsKey(key)) {
        String stringValue = options.get(key);
        if (stringValue == null) {
          processingEnvironment
              .getMessager()
              .printMessage(Diagnostic.Kind.ERROR, "Processor option -A" + key + " needs a value");
        } else {
          try {
            T value = Enum.valueOf(valueClass(), Ascii.toUpperCase(stringValue));
            if (option.validValues().contains(value)) {
              return value;
            }
          } catch (IllegalArgumentException e) {
            // handled below
          }
          processingEnvironment
              .getMessager()
              .printMessage(
                  Diagnostic.Kind.ERROR,
                  String.format(
                      "Processor option -A%s may only have the values %s "
                          + "(case insensitive), found: %s",
                      key, option.validValues(), stringValue));
        }
      }
      return option.defaultValue();
    }

    private Class<T> valueClass() {
      return option.defaultValue().getDeclaringClass();
    }
  }
}

/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.hilt.processor.internal;

import static androidx.room.compiler.processing.compat.XConverters.getProcessingEnv;
import static com.google.common.base.Ascii.toUpperCase;

import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import androidx.room.compiler.processing.compat.XConverters;
import com.google.common.collect.ImmutableSet;
import dagger.hilt.processor.internal.optionvalues.BooleanValue;
import dagger.hilt.processor.internal.optionvalues.GradleProjectType;
import dagger.internal.codegen.extension.DaggerStreams;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import javax.tools.Diagnostic.Kind;

/** Hilt annotation processor options. */
// TODO(danysantiago): Consider consolidating with Dagger compiler options logic.
public final class HiltCompilerOptions {

  /**
   * Returns {@code true} if the superclass validation is disabled for {@link
   * dagger.hilt.android.AndroidEntryPoint}-annotated classes.
   *
   * <p>This flag is for internal use only! The superclass validation checks that the super class is
   * a generated {@code Hilt_} class. This flag is disabled by the Hilt Gradle plugin to enable
   * bytecode transformation to change the superclass.
   */
  public static boolean isAndroidSuperClassValidationDisabled(XTypeElement element) {
    EnumOption<BooleanValue> option = DISABLE_ANDROID_SUPERCLASS_VALIDATION;
    XProcessingEnv processorEnv = getProcessingEnv(element);
    return option.get(processorEnv) == BooleanValue.TRUE;
  }

  /**
   * Returns {@code true} if cross-compilation root validation is disabled.
   *
   * <p>This flag should rarely be needed, but may be used for legacy/migration purposes if tests
   * require the use of {@link dagger.hilt.android.HiltAndroidApp} rather than {@link
   * dagger.hilt.android.testing.HiltAndroidTest}.
   *
   * <p>Note that Hilt still does validation within a single compilation unit. In particular, a
   * compilation unit that contains a {@code HiltAndroidApp} usage cannot have other {@code
   * HiltAndroidApp} or {@code HiltAndroidTest} usages in the same compilation unit.
   */
  public static boolean isCrossCompilationRootValidationDisabled(
      ImmutableSet<XTypeElement> rootElements, XProcessingEnv env) {
    EnumOption<BooleanValue> option = DISABLE_CROSS_COMPILATION_ROOT_VALIDATION;
    return option.get(env) == BooleanValue.TRUE;
  }

  /** Returns {@code true} if the check for {@link dagger.hilt.InstallIn} is disabled. */
  public static boolean isModuleInstallInCheckDisabled(XProcessingEnv env) {
    return DISABLE_MODULES_HAVE_INSTALL_IN_CHECK.get(env) == BooleanValue.TRUE;
  }

  /**
   * Returns {@code true} of unit tests should try to share generated components, rather than using
   * separate generated components per Hilt test root.
   *
   * <p>Tests that provide their own test bindings (e.g. using {@link
   * dagger.hilt.android.testing.BindValue} or a test {@link dagger.Module}) cannot use the shared
   * component. In these cases, a component will be generated for the test.
   */
  public static boolean isSharedTestComponentsEnabled(XProcessingEnv env) {
    return SHARE_TEST_COMPONENTS.get(env) == BooleanValue.TRUE;
  }

  /**
   * Returns {@code true} if the aggregating processor is enabled (default is {@code true}).
   *
   * <p>Note:This is for internal use only!
   */
  public static boolean useAggregatingRootProcessor(XProcessingEnv env) {
    return USE_AGGREGATING_ROOT_PROCESSOR.get(env) == BooleanValue.TRUE;
  }

  /**
   * Returns project type or null if Hilt Gradle Plugin is not applied.
   *
   * <p>Note:This is for internal use only!
   */
  public static GradleProjectType getGradleProjectType(XProcessingEnv env) {
    return GRADLE_PROJECT_TYPE.get(env);
  }

  public static boolean isAssistedInjectViewModelsEnabled(XTypeElement viewModelElement) {
    boolean enabled =
        ENABLE_ASSISTED_INJECT_VIEWMODELS.get(XConverters.getProcessingEnv(viewModelElement))
            == BooleanValue.TRUE;
    return enabled;
  }

  /** Do not use! This is for internal use only. */
  private static final EnumOption<BooleanValue> DISABLE_ANDROID_SUPERCLASS_VALIDATION =
      new EnumOption<>("android.internal.disableAndroidSuperclassValidation", BooleanValue.FALSE);

  /** Do not use! This is for internal use only. */
  private static final EnumOption<BooleanValue> USE_AGGREGATING_ROOT_PROCESSOR =
      new EnumOption<>("internal.useAggregatingRootProcessor", BooleanValue.TRUE);

  private static final EnumOption<BooleanValue> DISABLE_CROSS_COMPILATION_ROOT_VALIDATION =
      new EnumOption<>("disableCrossCompilationRootValidation", BooleanValue.FALSE);

  private static final EnumOption<BooleanValue> DISABLE_MODULES_HAVE_INSTALL_IN_CHECK =
      new EnumOption<>("disableModulesHaveInstallInCheck", BooleanValue.FALSE);

  private static final EnumOption<BooleanValue> SHARE_TEST_COMPONENTS =
      new EnumOption<>(
          "shareTestComponents",
          BooleanValue.TRUE);

  /** Do not use! This is for internal use only. */
  private static final EnumOption<GradleProjectType> GRADLE_PROJECT_TYPE =
      new EnumOption<>("android.internal.projectType", GradleProjectType.UNSET);

  private static final EnumOption<BooleanValue> ENABLE_ASSISTED_INJECT_VIEWMODELS =
      new EnumOption<>(
          "enableAssistedInjectViewModels", BooleanValue.TRUE );
  private static final ImmutableSet<String> DEPRECATED_OPTIONS =
      ImmutableSet.of("dagger.hilt.android.useFragmentGetContextFix");

  public static void checkWrongAndDeprecatedOptions(XProcessingEnv env) {
    Set<String> knownOptions = getProcessorOptions();
    for (String option : env.getOptions().keySet()) {
      if (knownOptions.contains(option)) {
        continue;
      }

      if (DEPRECATED_OPTIONS.contains(option)) {
        env.getMessager()
            .printMessage(
                Kind.ERROR,
                "The compiler option "
                    + option
                    + " is deprecated and no longer does anything. "
                    + "Please do not set this option.");
        continue;
      }

      if (option.startsWith("dagger.hilt.")) {
        env.getMessager()
            .printMessage(
                Kind.ERROR,
                "The compiler option "
                    + option
                    + " is not a recognized Hilt option. Is there a typo?");
      }
    }
  }

  /** A processor option that can be set on the command line. */
  private static final class EnumOption<E extends Enum<E>> {
    private final String name;

    private final E defaultValue;

    private static final Set<EnumOption<?>> options = new HashSet<>();

    EnumOption(String name, E defaultValue) {
      this.name = name;
      this.defaultValue = defaultValue;
      options.add(this);
    }

    String getQualifiedName() {
      return "dagger.hilt." + name;
    }

    E get(XProcessingEnv env) {
      String value = env.getOptions().get(getQualifiedName());
      if (value == null) {
        return defaultValue;
      }

      ImmutableSet<String> validOptionNames =
          Arrays.stream(defaultValue.getDeclaringClass().getEnumConstants())
              .map(Enum::name)
              .collect(DaggerStreams.toImmutableSet());
      String uppercaseValue = toUpperCase(value);
      if (validOptionNames.contains(uppercaseValue)) {
        return Enum.valueOf(defaultValue.getDeclaringClass(), uppercaseValue);
      } else {
        throw new IllegalStateException(
            String.format(
                Locale.ROOT,
                "Expected a value of %s  for the flag \"%s\". Got instead: %s",
                String.join("/", validOptionNames),
                name,
                value));
      }
    }

    static Set<EnumOption<?>> getAllOptions() {
      return options;
    }
  }

  public static Set<String> getProcessorOptions() {
    return EnumOption.getAllOptions().stream()
        .map(EnumOption::getQualifiedName)
        .collect(DaggerStreams.toImmutableSet());
  }
}

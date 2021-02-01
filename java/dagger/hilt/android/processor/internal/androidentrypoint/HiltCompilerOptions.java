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

package dagger.hilt.android.processor.internal.androidentrypoint;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.processing.ProcessingEnvironment;

/** Hilt annotation processor options. */
// TODO(danysantiago): Consider consolidating with Dagger compiler options logic.
// TODO(user): Move this class to dagger/hilt/processor/internal
public final class HiltCompilerOptions {

  /** Processor options which can have true or false values. */
  public enum BooleanOption {
    /**
     * Flag that disables validating the superclass of @AndroidEntryPoint are Hilt_ generated,
     * classes. This flag is to be used internally by the Gradle plugin, enabling the bytecode
     * transformation to change the superclass.
     */
    DISABLE_ANDROID_SUPERCLASS_VALIDATION(
        "android.internal.disableAndroidSuperclassValidation", false),

    /** Flag that disables check on modules to be annotated with @InstallIn. */
    DISABLE_MODULES_HAVE_INSTALL_IN_CHECK("disableModulesHaveInstallInCheck", false);

    private final String name;
    private final boolean defaultValue;

    BooleanOption(String name, boolean defaultValue) {
      this.name = name;
      this.defaultValue = defaultValue;
    }

    public boolean get(ProcessingEnvironment env) {
      String value = env.getOptions().get(getQualifiedName());
      if (value == null) {
        return defaultValue;
      }
      // TODO(danysantiago): Strictly verify input, either 'true' or 'false' and nothing else.
      return Boolean.parseBoolean(value);
    }

    public String getQualifiedName() {
      return "dagger.hilt." + name;
    }
  }

  public static Set<String> getProcessorOptions() {
    return Arrays.stream(BooleanOption.values())
        .map(BooleanOption::getQualifiedName)
        .collect(Collectors.toSet());
  }

  private HiltCompilerOptions() {}
}

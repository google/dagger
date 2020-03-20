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

package dagger.hilt.android.processor.internal;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.processing.ProcessingEnvironment;

/** Hilt annotation processor options. */
// TODO(user): Consider consolidating with Dagger compiler options logic.
public final class HiltCompilerOptions {

  enum BooleanOption {
    /**
     * Flag that disables validating the superclass of @AndroidEntryPoint are Hilt_ generated,
     * classes. This flag is to be used internally by the Gradle plugin, enabling the bytecode
     * transformation to change the superclass.
     */
    DISABLE_ANDROID_SUPERCLASS_VALIDATION(
        "android.internal.disableAndroidSuperclassValidation", false);

    private final String name;
    private final boolean defaultValue;

    BooleanOption(String name, boolean defaultValue) {
      this.name = name;
      this.defaultValue = defaultValue;
    }

    boolean get(ProcessingEnvironment env) {
      String value = env.getOptions().get(getQualifiedName());
      if (value == null) {
        return defaultValue;
      }
      // TODO(user): Strictly verify input, either 'true' or 'false' and nothing else.
      return Boolean.parseBoolean(value);
    }

    String getQualifiedName() {
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

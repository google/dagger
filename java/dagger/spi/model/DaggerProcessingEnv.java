/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.spi.model;

import com.google.auto.value.AutoValue;
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment;
import javax.annotation.Nullable;
import javax.annotation.processing.ProcessingEnvironment;

/** Wrapper type for an element. */
@AutoValue
public abstract class DaggerProcessingEnv {
  /** Represents a type of backend used for compilation. */
  public enum Backend { JAVAC, KSP }

  public static boolean isJavac(Backend backend) {
    return backend.equals(Backend.JAVAC);
  }

  public static DaggerProcessingEnv fromJavac(ProcessingEnvironment env) {
    return new AutoValue_DaggerProcessingEnv(env, null);
  }

  public static DaggerProcessingEnv fromKsp(SymbolProcessorEnvironment env) {
    return new AutoValue_DaggerProcessingEnv(null, env);
  }

  /**
   * Java representation for the processing environment, returns {@code null} not using java
   * annotation processor.
   */
  @Nullable
  public abstract ProcessingEnvironment java();

  @Nullable
  public abstract SymbolProcessorEnvironment ksp();

  /** Returns the backend used in this compilation. */
  public DaggerProcessingEnv.Backend backend() {
    if (java() != null) {
      return DaggerProcessingEnv.Backend.JAVAC;
    } else if (ksp() != null) {
      return DaggerProcessingEnv.Backend.KSP;
    }
    throw new AssertionError("Unexpected backend");
  }
}

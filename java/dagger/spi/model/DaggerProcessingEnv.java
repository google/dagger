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

import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static androidx.room.compiler.processing.compat.XConverters.toKS;

import androidx.room.compiler.processing.XProcessingEnv;
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

  public static DaggerProcessingEnv from(XProcessingEnv processingEnv) {
    Backend backend = Backend.valueOf(processingEnv.getBackend().name());
    if (backend.equals(Backend.JAVAC)) {
      return fromJavac(toJavac(processingEnv));
    } else if (backend.equals(Backend.KSP)) {
      return fromKsp(toKS(processingEnv));
    }
    throw new IllegalStateException(String.format("Backend %s is not supported yet.", backend));
  }

  public static DaggerProcessingEnv fromJavac(ProcessingEnvironment env) {
    return new AutoValue_DaggerProcessingEnv(Backend.JAVAC, env, null);
  }

  public static DaggerProcessingEnv fromKsp(SymbolProcessorEnvironment env) {
    return new AutoValue_DaggerProcessingEnv(Backend.KSP, null, env);
  }

  /** Returns the backend used in this compilation. */
  public abstract Backend backend();

  /**
   * Java representation for the processing environment, returns {@code null} not using java
   * annotation processor.
   */
  @Nullable
  public abstract ProcessingEnvironment java();

  @Nullable
  public abstract SymbolProcessorEnvironment ksp();
}

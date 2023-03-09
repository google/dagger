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

import androidx.room.compiler.processing.XExecutableElement;
import androidx.room.compiler.processing.XProcessingEnv;
import com.google.auto.value.AutoValue;
import com.google.devtools.ksp.symbol.KSFunctionDeclaration;
import dagger.internal.codegen.xprocessing.XElements;
import javax.annotation.Nullable;
import javax.lang.model.element.ExecutableElement;

/** Wrapper type for an executable element. */
@AutoValue
public abstract class DaggerExecutableElement {
  public static DaggerExecutableElement from(
      XExecutableElement executableElement, XProcessingEnv env) {
    DaggerProcessingEnv.Backend backend =
        DaggerProcessingEnv.Backend.valueOf(env.getBackend().name());
    if (backend.equals(DaggerProcessingEnv.Backend.JAVAC)) {
      return fromJava(toJavac(executableElement), XElements.getSimpleName(executableElement));
    } else if (backend.equals(DaggerProcessingEnv.Backend.KSP)) {
      return fromKsp(toKS(executableElement), XElements.getSimpleName(executableElement));
    }
    throw new IllegalStateException(String.format("Backend %s is not supported yet.", backend));
  }

  public static DaggerExecutableElement fromJava(
      ExecutableElement executableElement, String simpleName) {
    return new AutoValue_DaggerExecutableElement(
        executableElement, null, DaggerProcessingEnv.Backend.JAVAC, simpleName);
  }

  public static DaggerExecutableElement fromKsp(
      KSFunctionDeclaration declaration, String simpleName) {
    return new AutoValue_DaggerExecutableElement(
        null, declaration, DaggerProcessingEnv.Backend.KSP, simpleName);
  }

  /**
   * Java representation for the element, returns {@code null} not using java annotation processor.
   */
  @Nullable
  public abstract ExecutableElement java();

  /** KSP declaration for the element, returns {@code null} not using KSP. */
  @Nullable
  public abstract KSFunctionDeclaration ksp();

  public abstract DaggerProcessingEnv.Backend backend();

  abstract String simpleName();

  @Override
  public final String toString() {
    return DaggerProcessingEnv.isJavac(backend()) ? java().toString() : ksp().toString();
  }
}

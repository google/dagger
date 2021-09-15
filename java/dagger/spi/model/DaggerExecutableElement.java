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

import static dagger.spi.model.CompilerEnvironment.JAVA;
import static dagger.spi.model.CompilerEnvironment.KSP;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.devtools.ksp.symbol.KSFunctionDeclaration;
import javax.annotation.Nullable;
import javax.lang.model.element.ExecutableElement;

/** Wrapper type for an executable element. */
@AutoValue
public abstract class DaggerExecutableElement {

  public static DaggerExecutableElement fromJava(ExecutableElement element) {
    return new AutoValue_DaggerExecutableElement(JAVA, Preconditions.checkNotNull(element), null);
  }

  public static DaggerExecutableElement fromKsp(KSFunctionDeclaration element) {
    return new AutoValue_DaggerExecutableElement(KSP, null, Preconditions.checkNotNull(element));
  }

  public ExecutableElement java() {
    Preconditions.checkState(compiler() == JAVA);
    return javaInternal();
  }

  public KSFunctionDeclaration ksp() {
    Preconditions.checkState(compiler() == KSP);
    return kspInternal();
  }

  public abstract CompilerEnvironment compiler();

  @Nullable
  abstract ExecutableElement javaInternal();

  @Nullable
  abstract KSFunctionDeclaration kspInternal();

  @Override
  public final String toString() {
    return (compiler() == JAVA ? java() : ksp()).toString();
  }
}

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
import com.google.devtools.ksp.symbol.KSClassDeclaration;
import com.squareup.javapoet.ClassName;
import javax.annotation.Nullable;
import javax.lang.model.element.TypeElement;

/** Wrapper type for a type element. */
@AutoValue
public abstract class DaggerTypeElement {

  public static DaggerTypeElement fromJava(TypeElement element) {
    return new AutoValue_DaggerTypeElement(JAVA, Preconditions.checkNotNull(element), null);
  }

  public static DaggerTypeElement fromKsp(KSClassDeclaration element) {
    return new AutoValue_DaggerTypeElement(KSP, null, Preconditions.checkNotNull(element));
  }

  public TypeElement java() {
    Preconditions.checkState(compiler() == JAVA);
    return javaInternal();
  }

  public KSClassDeclaration ksp() {
    Preconditions.checkState(compiler() == KSP);
    return kspInternal();
  }

  public ClassName className() {
    if (compiler() == KSP) {
      // TODO(bcorso): Add support for KSP. Consider using xprocessing types internally since that
      // already has support for KSP class names?
      throw new UnsupportedOperationException("Method className() is not yet supported in KSP.");
    } else {
      return ClassName.get(java());
    }
  }

  public abstract CompilerEnvironment compiler();

  @Nullable
  abstract TypeElement javaInternal();

  @Nullable
  abstract KSClassDeclaration kspInternal();

  @Override
  public final String toString() {
    return (compiler() == JAVA ? java() : ksp()).toString();
  }
}

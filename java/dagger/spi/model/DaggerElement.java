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
import com.google.devtools.ksp.symbol.KSAnnotated;
import javax.annotation.Nullable;
import javax.lang.model.element.Element;

/** Wrapper type for an element. */
@AutoValue
public abstract class DaggerElement {
  public static DaggerElement fromJavac(Element element) {
    return new AutoValue_DaggerElement(element, null);
  }

  public static DaggerElement fromKsp(KSAnnotated ksp) {
    return new AutoValue_DaggerElement(null, ksp);
  }

  /**
   * Java representation for the element, returns {@code null} not using java annotation processor.
   */
  @Nullable
  public abstract Element java();

  /** KSP declaration for the element, returns {@code null} not using KSP. */
  @Nullable
  public abstract KSAnnotated ksp();

  public DaggerProcessingEnv.Backend backend() {
    if (java() != null) {
      return DaggerProcessingEnv.Backend.JAVAC;
    } else if (ksp() != null) {
      return DaggerProcessingEnv.Backend.KSP;
    }
    throw new AssertionError("Unexpected backend");
  }

  @Override
  public final String toString() {
    switch (backend()) {
      case JAVAC:
        return java().toString();
      case KSP:
        return ksp().toString();
    }
    throw new IllegalStateException(String.format("Backend %s not supported yet.", backend()));
  }
}

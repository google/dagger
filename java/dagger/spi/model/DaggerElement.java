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

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XProcessingEnv;
import com.google.auto.value.AutoValue;
import com.google.devtools.ksp.symbol.KSAnnotated;
import dagger.internal.codegen.xprocessing.XElements;
import javax.annotation.Nullable;
import javax.lang.model.element.Element;

/** Wrapper type for an element. */
@AutoValue
public abstract class DaggerElement {
  public static DaggerElement from(XElement element, XProcessingEnv env) {
    DaggerProcessingEnv.Backend backend =
        DaggerProcessingEnv.Backend.valueOf(env.getBackend().name());
    if (backend.equals(DaggerProcessingEnv.Backend.JAVAC)) {
      return fromJavac(toJavac(element));
    } else if (backend.equals(DaggerProcessingEnv.Backend.KSP)) {
      return fromKsp(XElements.toKSAnnotated(element));
    }
    throw new IllegalStateException(String.format("Backend %s is not supported yet.", backend));
  }

  public static DaggerElement fromJavac(Element element) {
    return new AutoValue_DaggerElement(element, null, DaggerProcessingEnv.Backend.JAVAC);
  }

  public static DaggerElement fromKsp(KSAnnotated ksp) {
    return new AutoValue_DaggerElement(null, ksp, DaggerProcessingEnv.Backend.KSP);
  }

  /**
   * Java representation for the element, returns {@code null} not using java annotation processor.
   */
  @Nullable
  public abstract Element java();

  /** KSP declaration for the element, returns {@code null} not using KSP. */
  @Nullable
  public abstract KSAnnotated ksp();

  public abstract DaggerProcessingEnv.Backend backend();

  @Override
  public final String toString() {
    return DaggerProcessingEnv.isJavac(backend()) ? java().toString() : ksp().toString();
  }
}

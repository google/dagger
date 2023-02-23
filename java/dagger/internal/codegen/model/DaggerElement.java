/*
 * Copyright (C) 2023 The Dagger Authors.
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

package dagger.internal.codegen.model;

import static androidx.room.compiler.processing.compat.XConverters.getProcessingEnv;
import static androidx.room.compiler.processing.compat.XConverters.toJavac;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XProcessingEnv;
import com.google.auto.value.AutoValue;
import com.google.devtools.ksp.symbol.KSAnnotated;
import dagger.internal.codegen.xprocessing.XElements;
import dagger.spi.model.CompilerEnvironment;
import javax.lang.model.element.Element;

/** Internal implementation for dagger.spi.model.DaggerElement. */
@AutoValue
public abstract class DaggerElement implements dagger.spi.model.DaggerElement {
  public static DaggerElement from(XElement element) {
    return new AutoValue_DaggerElement(element);
  }

  @Override
  public CompilerEnvironment backend() {
    return getProcessingEnv(xprocessing()).getBackend() == XProcessingEnv.Backend.JAVAC
        ? CompilerEnvironment.JAVA
        : CompilerEnvironment.KSP;
  }

  public abstract XElement xprocessing();

  public static XElement xprocessing(dagger.spi.model.DaggerElement element) {
    return ((DaggerElement) element).xprocessing();
  }

  @Override
  public final Element java() {
    if (backend().equals(CompilerEnvironment.JAVA)) {
      return toJavac(xprocessing());
    }
    throw new IllegalStateException("Cannot access javac element when compiling with KSP");
  }

  // TODO(b/268549229): Rely on XElement#toKS once the API is added.
  @Override
  public final KSAnnotated ksp() {
    if (backend().equals(CompilerEnvironment.KSP)) {
      return XElements.toKS(xprocessing());
    }
    throw new IllegalStateException("Cannot access ksp element when compiling with JAVAC");
  }

  @Override
  public final String toString() {
    return XElements.toStableString(xprocessing());
  }
}

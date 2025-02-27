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

import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static com.google.common.base.Preconditions.checkNotNull;

import androidx.room.compiler.processing.XExecutableElement;
import com.google.auto.value.AutoValue;
import javax.lang.model.element.ExecutableElement;

/** Wrapper type for an executable element. */
@AutoValue
public abstract class DaggerExecutableElement {
  public static DaggerExecutableElement from(XExecutableElement executableElement) {
    return new AutoValue_DaggerExecutableElement(checkNotNull(executableElement));
  }

  public abstract XExecutableElement xprocessing();

  public ExecutableElement javac() {
    return toJavac(xprocessing());
  }

  @Override
  public final String toString() {
    return xprocessing().toString();
  }
}

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

import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import javax.lang.model.element.TypeElement;

/** Wrapper type for a type element. */
@AutoValue
public abstract class DaggerTypeElement {
  public static DaggerTypeElement from(XTypeElement typeElement) {
    return new AutoValue_DaggerTypeElement(typeElement);
  }

  public abstract XTypeElement xprocessing();

  public TypeElement javac() {
    return toJavac(xprocessing());
  }

  @Override
  public final String toString() {
    return xprocessing().toString();
  }
}

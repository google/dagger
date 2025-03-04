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

import androidx.room.compiler.processing.XElement;
import com.google.auto.value.AutoValue;
import javax.lang.model.element.Element;

/** Wrapper type for an element. */
@AutoValue
public abstract class DaggerElement {
  public static DaggerElement from(XElement element) {
    return new AutoValue_DaggerElement(element);
  }

  public abstract XElement xprocessing();

  public Element javac() {
    return toJavac(xprocessing());
  }

  @Override
  public final String toString() {
    return xprocessing().toString();
  }
}

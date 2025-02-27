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

import androidx.room.compiler.processing.XType;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import com.google.common.base.Preconditions;
import dagger.internal.codegen.xprocessing.XTypes;
import javax.lang.model.type.TypeMirror;

/** Wrapper type for a type. */
@AutoValue
public abstract class DaggerType {
  public static DaggerType from(XType type) {
    Preconditions.checkNotNull(type);
    return new AutoValue_DaggerType(XTypes.equivalence().wrap(type));
  }

  abstract Equivalence.Wrapper<XType> equivalenceWrapper();

  public XType xprocessing() {
    return equivalenceWrapper().get();
  }

  public TypeMirror javac() {
    return toJavac(xprocessing());
  }

  @Override
  public final String toString() {
    // We define our own stable string rather than use XType#toString() here because
    // XType#toString() is currently not stable across backends. In particular, in javac it returns
    // the qualified type but in ksp it returns the simple name.
    // TODO(bcorso): Consider changing XProcessing so that #toString() is stable across backends.
    return XTypes.toStableString(xprocessing());
  }
}

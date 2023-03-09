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

import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.devtools.ksp.symbol.KSType;
import dagger.internal.codegen.xprocessing.XTypes;
import javax.annotation.Nullable;
import javax.lang.model.type.TypeMirror;

/** Wrapper type for a type. */
@AutoValue
public abstract class DaggerType {
  public static DaggerType from(XType type, XProcessingEnv env) {
    Preconditions.checkNotNull(type);
    String backend = env.getBackend().name();
    String representation = XTypes.toStableString(type);
    if (backend.equals(DaggerProcessingEnv.Backend.JAVAC.name())) {
      return builder()
          .java(toJavac(type))
          .representation(representation)
          .backend(DaggerProcessingEnv.Backend.JAVAC)
          .build();
    } else if (backend.equals(DaggerProcessingEnv.Backend.KSP.name())) {
      return builder()
          .ksp(toKS(type))
          .representation(representation)
          .backend(DaggerProcessingEnv.Backend.KSP)
          .build();
    }
    throw new IllegalStateException(String.format("Backend %s is not supported yet.", backend));
  }

  public static Builder builder() {
    return new AutoValue_DaggerType.Builder();
  }

  /** A builder for {@link DaggerType}s. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder java(@Nullable TypeMirror java);

    public abstract Builder ksp(@Nullable KSType ksp);

    public abstract Builder backend(DaggerProcessingEnv.Backend backend);

    public abstract Builder representation(String value);

    public abstract DaggerType build();
  }

  /** Java representation for the type, returns {@code null} not using java annotation processor. */
  @Nullable
  public abstract TypeMirror java();

  /** KSP declaration for the type, returns {@code null} not using KSP. */
  @Nullable
  public abstract KSType ksp();

  public abstract DaggerProcessingEnv.Backend backend();

  abstract String representation();

  @Override
  public final String toString() {
    return representation();
  }
}

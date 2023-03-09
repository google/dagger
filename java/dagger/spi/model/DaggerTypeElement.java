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
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.common.MoreElements;
import com.google.auto.value.AutoValue;
import com.google.devtools.ksp.symbol.KSClassDeclaration;
import javax.annotation.Nullable;
import javax.lang.model.element.TypeElement;

/** Wrapper type for a type element. */
@AutoValue
public abstract class DaggerTypeElement {
  public static DaggerTypeElement from(XTypeElement typeElement, XProcessingEnv env) {
    String backend = env.getBackend().name();
    if (backend.equals(DaggerProcessingEnv.Backend.JAVAC.name())) {
      return builder()
          .java(toJavac(typeElement))
          .packageName(typeElement.getPackageName())
          .qualifiedName(typeElement.getClassName().canonicalName())
          .backend(DaggerProcessingEnv.Backend.JAVAC)
          .build();

    } else if (backend.equals(DaggerProcessingEnv.Backend.KSP.name())) {
      return builder()
          .ksp(toKS(typeElement))
          .packageName(typeElement.getPackageName())
          .qualifiedName(typeElement.getClassName().canonicalName())
          .backend(DaggerProcessingEnv.Backend.KSP)
          .build();
    }
    throw new IllegalStateException(String.format("Backend %s is not supported yet.", backend));
  }

  public static Builder builder() {
    return new AutoValue_DaggerTypeElement.Builder();
  }

  /** A builder for {@link DaggerTypeElement}s. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder java(@Nullable TypeElement java);

    public abstract Builder ksp(@Nullable KSClassDeclaration ksp);

    public abstract Builder packageName(String packageName);

    public abstract Builder qualifiedName(String qualifiedName);

    public abstract Builder backend(DaggerProcessingEnv.Backend backend);

    public abstract DaggerTypeElement build();
  }

  /** Java representation for the type, returns {@code null} not using java annotation processor. */
  @Nullable
  public abstract TypeElement java();

  /** KSP declaration for the element, returns {@code null} not using KSP. */
  @Nullable
  public abstract KSClassDeclaration ksp();

  public abstract String packageName();

  public abstract String qualifiedName();

  public abstract DaggerProcessingEnv.Backend backend();

  public final boolean hasAnnotation(String annotationName) {
    if (backend().equals(DaggerProcessingEnv.Backend.JAVAC)) {
      return MoreElements.isAnnotationPresent(java(), annotationName);
    }
    if (backend().equals(DaggerProcessingEnv.Backend.KSP)) {
      return KspUtilsKt.hasAnnotation(ksp(), annotationName);
    }

    throw new IllegalStateException(String.format("Backend %s not supported yet.", backend()));
  }

  @Override
  public final String toString() {
    return DaggerProcessingEnv.isJavac(backend()) ? java().toString() : ksp().toString();
  }
}

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

import com.google.auto.common.MoreElements;
import com.google.auto.value.AutoValue;
import com.google.devtools.ksp.symbol.KSClassDeclaration;
import javax.annotation.Nullable;
import javax.lang.model.element.TypeElement;

/** Wrapper type for a type element. */
@AutoValue
public abstract class DaggerTypeElement {
  public static DaggerTypeElement fromJavac(@Nullable TypeElement element) {
    return new AutoValue_DaggerTypeElement(element, null);
  }

  public static DaggerTypeElement fromKsp(@Nullable KSClassDeclaration declaration) {
    return new AutoValue_DaggerTypeElement(null, declaration);
  }

  /** Java representation for the type, returns {@code null} not using java annotation processor. */
  @Nullable
  public abstract TypeElement java();

  /** KSP declaration for the element, returns {@code null} not using KSP. */
  @Nullable
  public abstract KSClassDeclaration ksp();

  public final boolean hasAnnotation(String annotationName) {
    switch (backend()) {
      case JAVAC:
        return MoreElements.isAnnotationPresent(java(), annotationName);
      case KSP:
        return KspUtilsKt.hasAnnotation(ksp(), annotationName);
    }
    throw new IllegalStateException(String.format("Backend %s not supported yet.", backend()));
  }

  public String packageName() {
    switch (backend()) {
      case JAVAC:
        return MoreElements.getPackage(java()).getQualifiedName().toString();
      case KSP:
        return KspUtilsKt.getNormalizedPackageName(ksp());
    }
    throw new IllegalStateException(String.format("Backend %s not supported yet.", backend()));
  }

  public String qualifiedName() {
    switch (backend()) {
      case JAVAC:
        return java().getQualifiedName().toString();
      case KSP:
        return ksp().getQualifiedName().asString();
    }
    throw new IllegalStateException(String.format("Backend %s not supported yet.", backend()));
  }

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

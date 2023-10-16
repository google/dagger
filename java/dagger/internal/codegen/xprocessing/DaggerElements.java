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

package dagger.internal.codegen.xprocessing;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.compat.XConverters;
import com.google.devtools.ksp.symbol.KSClassDeclaration;
import com.google.devtools.ksp.symbol.KSFunctionDeclaration;
import com.google.devtools.ksp.symbol.KSPropertyDeclaration;
import com.google.devtools.ksp.symbol.KSValueParameter;
import dagger.spi.model.DaggerElement;
import dagger.spi.model.DaggerProcessingEnv;
import dagger.spi.model.DaggerType;

/** Convert Dagger model types to XProcessing types. */
public final class DaggerElements {
  public static XElement toXProcessing(
      DaggerElement element, DaggerProcessingEnv daggerProcessingEnv) {
    XProcessingEnv processingEnv = toXProcessing(daggerProcessingEnv);
    switch (element.backend()) {
      case JAVAC:
        return XConverters.toXProcessing(element.javac(), processingEnv);
      case KSP:
        if (element.ksp() instanceof KSFunctionDeclaration) {
          return XConverters.toXProcessing((KSFunctionDeclaration) element.ksp(), processingEnv);
        } else if (element.ksp() instanceof KSClassDeclaration) {
          return XConverters.toXProcessing((KSClassDeclaration) element.ksp(), processingEnv);
        } else if (element.ksp() instanceof KSValueParameter) {
          return XConverters.toXProcessing((KSValueParameter) element.ksp(), processingEnv);
        } else if (element.ksp() instanceof KSPropertyDeclaration) {
          return XConverters.toXProcessing((KSPropertyDeclaration) element.ksp(), processingEnv);
        }
        throw new IllegalStateException(
            String.format("Unsupported ksp declaration %s.", element.ksp()));
    }
    throw new IllegalStateException(
        String.format("Backend %s not supported yet.", element.backend()));
  }

  public static XType toXProcessing(DaggerType type, DaggerProcessingEnv daggerProcessingEnv) {
    XProcessingEnv processingEnv = toXProcessing(daggerProcessingEnv);
    switch (type.backend()) {
      case JAVAC:
        return XConverters.toXProcessing(type.javac(), processingEnv);
      case KSP:
        return XConverters.toXProcessing(type.ksp(), processingEnv);
    }
    throw new IllegalStateException(String.format("Backend %s not supported yet.", type.backend()));
  }

  public static XProcessingEnv toXProcessing(DaggerProcessingEnv processingEnv) {
    switch (processingEnv.backend()) {
      case JAVAC:
        return XProcessingEnv.create(processingEnv.javac());
      case KSP:
        return XProcessingEnv.create(processingEnv.ksp(), processingEnv.resolver());
    }
    throw new IllegalStateException(
        String.format("Backend %s not supported yet.", processingEnv.backend()));
  }

  private DaggerElements() {}
}

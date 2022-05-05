/*
 * Copyright (C) 2022 The Dagger Authors.
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

import static androidx.room.compiler.processing.compat.XConverters.toJavac;

import androidx.room.compiler.processing.XExecutableParameterElement;
import androidx.room.compiler.processing.XTypeElement;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeSpec;

// TODO(bcorso): Consider moving these methods into XProcessing library.
/** A utility class for JavaPoet types to interface with XProcessing types. */
public final class JavaPoetExt {
  /** Returns the {@link ParameterSpec} for the given parameter. */
  public static ParameterSpec getParameterSpec(XExecutableParameterElement parameter) {
    return ParameterSpec.get(toJavac(parameter));
  }

  /**
   * Marks the given type so that it avoids clashes with nested classes in the given {@link
   * TypeSpec.Builder}.
   */
  public static TypeSpec.Builder avoidClashesWithNestedClasses(
      TypeSpec.Builder typeBuilder, XTypeElement type) {
    return typeBuilder.avoidClashesWithNestedClasses(toJavac(type));
  }

  private JavaPoetExt() {}
}

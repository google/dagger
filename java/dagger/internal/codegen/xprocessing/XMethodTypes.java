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
import static androidx.room.compiler.processing.compat.XConverters.toXProcessing;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;

import androidx.room.compiler.processing.XMethodType;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.TypeName;

/** A utility class for {@link XMethodType} helper methods. */
// TODO(bcorso): Consider moving these methods into XProcessing library.
public final class XMethodTypes {

  /** Returns the list of thrown types for the method as {@link TypeName}s. */
  public static ImmutableList<XType> getThrownTypes(
      XMethodType methodType, XProcessingEnv processingEnv) {
    return toJavac(methodType).getThrownTypes().stream()
        .map(type -> toXProcessing(type, processingEnv))
        .collect(toImmutableList());
  }

  private XMethodTypes() {}
}

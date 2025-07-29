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

package dagger.hilt.processor.internal;

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static java.util.stream.Collectors.joining;

import androidx.room.compiler.processing.XExecutableElement;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XMethodType;
import androidx.room.compiler.processing.XType;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import dagger.internal.codegen.xprocessing.XElements;

/** Represents the method signature needed to uniquely identify a method. */
@AutoValue
public abstract class MethodSignature {
  MethodSignature() {}

  abstract String name();

  abstract ImmutableList<TypeName> parameters();

  /** Creates a {@link MethodSignature} from a method name and parameter {@link TypeName}s */
  public static MethodSignature of(String methodName, TypeName... typeNames) {
    return new AutoValue_MethodSignature(methodName, ImmutableList.copyOf(typeNames));
  }

  /** Creates a {@link MethodSignature} from a {@link MethodSpec} */
  public static MethodSignature of(MethodSpec method) {
    return new AutoValue_MethodSignature(
        method.name, method.parameters.stream().map(p -> p.type).collect(toImmutableList()));
  }

  /** Creates a {@link MethodSignature} from an {@link XExecutableElement} */
  public static MethodSignature of(XExecutableElement executableElement) {
    return new AutoValue_MethodSignature(
        XElements.getSimpleName(executableElement),
        executableElement.getParameters().stream()
            .map(p -> p.getType().getTypeName())
            .collect(toImmutableList()));
  }

  /**
   * Creates a {@link MethodSignature} from an {@link XMethodElement}.
   *
   * <p>This version will resolve type parameters as declared by {@code enclosing}.
   */
  static MethodSignature ofDeclaredType(XMethodElement method, XType enclosing) {
    XMethodType executableType = method.asMemberOf(enclosing);
    return new AutoValue_MethodSignature(
        XElements.getSimpleName(method),
        executableType.getParameterTypes().stream()
            .map(XType::getTypeName)
            .collect(toImmutableList()));
  }

  /** Returns a string in the format: METHOD_NAME(PARAM_TYPE1,PARAM_TYPE2,...) */
  @Override
  public final String toString() {
    return String.format(
        "%s(%s)", name(), parameters().stream().map(Object::toString).collect(joining(",")));
  }
}

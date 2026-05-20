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

import androidx.room3.compiler.processing.XExecutableElement;
import androidx.room3.compiler.processing.XMethodElement;
import androidx.room3.compiler.processing.XType;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import dagger.internal.codegen.xprocessing.XElements;
import java.util.Objects;

/** Represents the method signature needed to uniquely identify a method. */
public abstract class MethodSignature {
  MethodSignature() {}

  abstract String name();

  abstract ImmutableList<TypeName> parameters();

  /** Creates a {@link MethodSignature} from a method name and parameter {@link TypeName}s */
  public static MethodSignature of(String methodName, TypeName... typeNames) {
    return new AutoValue_MethodSignature_TypeNameMethodSignature(
        methodName, ImmutableList.copyOf(typeNames));
  }

  /** Creates a {@link MethodSignature} from a {@link MethodSpec} */
  public static MethodSignature of(MethodSpec method) {
    return new AutoValue_MethodSignature_TypeNameMethodSignature(
        method.name, method.parameters.stream().map(p -> p.type).collect(toImmutableList()));
  }

  /** Creates a {@link MethodSignature} from an {@link XExecutableElement} */
  public static MethodSignature of(XExecutableElement executableElement) {
    return new AutoValue_MethodSignature_ElementMethodSignature(
        executableElement, executableElement.getEnclosingElement().getType());
  }

  /**
   * Creates a {@link MethodSignature} from an {@link XMethodElement}.
   *
   * <p>This version will resolve type parameters as declared by {@code enclosing}.
   */
  static MethodSignature ofDeclaredType(XMethodElement method, XType enclosing) {
    return new AutoValue_MethodSignature_ElementMethodSignature(method, enclosing);
  }

  @Override
  public final boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof MethodSignature)) {
      return false;
    }
    MethodSignature that = (MethodSignature) other;
    return Objects.equals(this.name(), that.name())
        && Objects.equals(this.parameters(), that.parameters());
  }

  @Override
  public final int hashCode() {
    // Only hash the name to avoid expensive parameter resolution. This allows Set and Map lookups
    // to be efficient, only calling equals() when the name matches.
    return Objects.hashCode(name());
  }

  /** Returns a string in the format: METHOD_NAME(PARAM_TYPE1,PARAM_TYPE2,...) */
  @Override
  public final String toString() {
    return String.format(
        "%s(%s)", name(), parameters().stream().map(Object::toString).collect(joining(",")));
  }

  @AutoValue
  abstract static class TypeNameMethodSignature extends MethodSignature {
    @Override
    public abstract String name();

    @Override
    public abstract ImmutableList<TypeName> parameters();
  }

  @AutoValue
  abstract static class ElementMethodSignature extends MethodSignature {
    abstract XExecutableElement executableElement();

    abstract XType enclosingType();

    @Override
    public final String name() {
      return XElements.getSimpleName(executableElement());
    }

    @Override
    @Memoized
    public ImmutableList<TypeName> parameters() {
      return executableElement().asMemberOf(enclosingType()).getParameterTypes().stream()
          .map(XType::getTypeName)
          .collect(toImmutableList());
    }
  }
}
